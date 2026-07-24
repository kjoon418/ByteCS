package watson.bytecs.problem.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.problem.domain.Enrichment
import watson.bytecs.problem.domain.EnrichmentItem
import watson.bytecs.problem.domain.Hint
import watson.bytecs.problem.domain.MisconceptionHint
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemCategory
import watson.bytecs.problem.domain.ProblemType

/**
 * 애플리케이션 기동 시 리소스 JSON([resourcePath])의 CS 문제를 시딩한다.
 * **local·tester 프로파일 전용이다** — 시딩분은 승인(APPROVED)으로 들어가므로, 운영에서 동작하면 미검수 콘텐츠가
 * 즉시 서빙되어 "검수 전 실서비스 투입 금지" 가드레일을 깬다(운영 유입은 관리자 검수 경로만).
 * tester는 테스터 피드백용 배포 환경(실서비스 아님)이고 메인 시드가 테스터용으로 큐레이션된 콘텐츠이므로,
 * 즉시 승인 투입을 허용한다(오너 결정 2026-07-20, application-tester.yml 참고).
 * 테스트는 각자 필요한 데이터를 직접 준비한다(이 로더의 단위 테스트는 빈을 직접 생성한다).
 *
 * **동작(기동 시 콘텐츠 업서트, 오너 결정 2026-07-25)**: DB가 비어 있으면 전량 신규 삽입한다(기존 경로).
 * DB에 이미 데이터가 있으면, 시드 항목을 질문 텍스트(정확 일치)로 기존 행과 대조해 **콘텐츠 필드만** 갱신한다
 * (이번 확정 범위: enrichment만 — [upsertProblems] 참고). 매칭되지 않은 시드 항목은 신규 삽입하고,
 * DB에는 있는데 시드에 없는 항목은 삭제하지 않고 로그만 남긴다. 라이브 테스터 DB가 이미 시딩된 상태에서
 * 콘텐츠를 재저작해도(문제 자체를 새로 추가하지 않는 한) 배포만으로 반영되게 하기 위함이다 — 일회성 SQL이 아닌
 * 지속 가능한 경로.
 *
 * 이 로더는 곧 검증기다: JSON을 DTO로 파싱한 뒤 반드시 도메인 생성자([Problem]·[Concept]·[MisconceptionHint]·
 * [Enrichment] 등)로 조립하며, 파싱 DTO를 그대로 영속화하지 않는다. 그래서 필수 필드 누락(Jackson 파싱 실패)이나
 * 불변식 위반(도메인 init require 실패)이 있으면 조용히 스킵되지 않고 기동이 시끄럽게 실패한다.
 * 이 검증기 역할은 업서트 경로에서도 동일하게 유지된다 — 시드 파일 전체를 항상 도메인 객체로 조립·단정한 뒤에야
 * 신규/갱신을 가른다.
 *
 * 시드는 승인(APPROVED)으로 곧장 생성되어 [Problem.approve]의 전이 검증(IN_REVIEW 상태 요구)을 거치지 않으므로,
 * 유형 미상(type=null)·힌트 정답 유출 같은 승인 요건 위반이 검증 없이 서빙될 수 있다(수용 기준 16·23).
 * 그래서 saveAll 직전에 [Problem.assertStructurallyApprovable]로 같은 구조 가드레일을 재사용해 단정한다 —
 * 위반이 있으면 [watson.bytecs.problem.domain.InvalidApprovalStateException]으로 기동이 실패한다.
 */
@Component
@Profile("local", "tester")
@Order(1)
class ProblemDataLoader(
    private val conceptRepository: ConceptRepository,
    private val problemRepository: ProblemRepository,
    private val objectMapper: ObjectMapper,
    private val resourcePath: String = "seed/problems-generated.json",
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(vararg args: String?) {
        val seedFile = loadSeedFile()
        // 같은 이름의 개념은 이 실행 안에서 하나의 [Concept] 행으로 묶여야, 문제 간 공유(복습 인터리빙의 전제)가 성립한다.
        val conceptCache = mutableMapOf<String, Concept>()
        val conceptCategories = seedFile.conceptCategories.mapValues { (_, category) -> category?.let { ProblemCategory.valueOf(it) } }

        val problems = seedFile.problems.map { toProblem(it, conceptCache, conceptCategories) }
        // 승인 상태로 곧장 만든 시드가 승인 요건(유형 태깅·no-leak·연결 문제 개념 수)을 실제로 충족하는지, 저장 전에 단정한다.
        // 신규 삽입·갱신 여부와 무관하게 항상 실행한다(검증기 역할은 업서트 경로에서도 동일).
        problems.forEach { it.assertStructurallyApprovable() }
        // 연결 문제(DI12)의 계단 보장: 각 구성 개념이 같은 시드의 단일 개념 문제로도 다뤄져야 한다(위반 시 기동 실패).
        validateIntegrationProblemsAreStaged(problems)

        if (problemRepository.count() == 0L) {
            problemRepository.saveAll(problems)
            return
        }

        upsertProblems(problems)
    }

    /**
     * 기동 시 업서트(오너 결정 2026-07-25): 시드 문제를 질문 텍스트(정확 일치)로 기존 행과 대조한다.
     *  - 매칭되면 콘텐츠 필드만 갱신한다. **이번 확정 범위는 enrichment(심화 정보)만**이다 — 문제 자체(정답·힌트·유형 등)의
     *    재저작은 정답 판정·복습 로직에 영향을 줄 수 있어 이번 범위 밖으로 뒀다(범위 확대 여지는 리뷰에서 재확정).
     *    무변경이면 [Problem.updateEnrichment]를 아예 호출하지 않아, 더티체킹이 불필요한 쓰기를 일으키지 않는다.
     *  - 매칭되지 않으면(신규 문제) 기존 시딩 관례대로 신규 삽입한다(같은 승인 검증 게이트를 이미 통과했다).
     *  - DB에는 있는데 시드에 없는 항목은 삭제하지 않고 로그만 남긴다(사용자 학습 이력이 그 id를 참조할 수 있어 보존).
     */
    private fun upsertProblems(seedProblems: List<Problem>) {
        val existingByQuestion = problemRepository.findAllWithEnrichment().associateBy { it.questionText }
        val newProblems = mutableListOf<Problem>()

        seedProblems.forEach { seedProblem ->
            val existing = existingByQuestion[seedProblem.questionText]
            if (existing == null) {
                newProblems.add(seedProblem)
                return@forEach
            }
            if (!enrichmentContentEquals(existing.enrichment, seedProblem.enrichment)) {
                existing.updateEnrichment(seedProblem.enrichment)
            }
        }

        if (newProblems.isNotEmpty()) {
            problemRepository.saveAll(newProblems)
        }

        val staleQuestions = existingByQuestion.keys - seedProblems.map { it.questionText }.toSet()
        if (staleQuestions.isNotEmpty()) {
            log.info("시드에 없는 기존 문제 {}건은 삭제하지 않고 유지합니다: {}", staleQuestions.size, staleQuestions)
        }
    }

    /** 두 심화 정보의 콘텐츠(제목·본문·항목·인용)가 같은지 값 기준으로 비교한다. [Enrichment]는 엔티티라 기본 equals가 참조 비교라 직접 비교한다. */
    private fun enrichmentContentEquals(a: Enrichment?, b: Enrichment?): Boolean {
        if (a == null || b == null) {
            return a == b
        }
        return a.title == b.title &&
            a.body == b.body &&
            a.quote == b.quote &&
            a.items.map { it.title to it.description } == b.items.map { it.title to it.description }
    }

    /**
     * 연결 문제(DI12)의 '계단 보장' 파일 단위 검증. 연결 문제로 지정된 문제의 각 구성 개념은, 같은 시드 안에서
     * **단일 개념 문제로도** 다뤄져야 한다 — 그래야 사용자가 그 개념을 먼저 익혀(게이트 통과) 연결 문제에 도달할 수 있다.
     * 계단이 없으면 연결 문제는 어떤 사용자에게도 영구 잠금이 되므로, 조용히 스킵하지 않고 기동을 실패시킨다(콘텐츠 결함).
     * 개념은 로더 안에서 이름으로 공유되므로([findOrCreateConcept]) 이름 기준으로 단일 개념 커버리지를 판정한다.
     */
    private fun validateIntegrationProblemsAreStaged(problems: List<Problem>) {
        val singleConceptCoverage = problems
            .filter { it.concepts.size == 1 }
            .map { it.concepts.first().name }
            .toSet()
        problems.filter { it.integration }.forEach { problem ->
            problem.concepts.forEach { concept ->
                require(concept.name in singleConceptCoverage) {
                    "연결 문제 '${problem.questionText}'의 구성 개념 '${concept.name}'은(는) " +
                        "같은 시드의 단일 개념 문제로도 다뤄져야 합니다(계단 없는 연결 문제 금지)."
                }
            }
        }
    }

    private fun loadSeedFile(): ProblemSeedFile {
        val resource = ClassPathResource(resourcePath)
        require(resource.exists()) { "시드 데이터 파일을 찾을 수 없습니다: $resourcePath" }
        return resource.inputStream.use { objectMapper.readValue(it, ProblemSeedFile::class.java) }
    }

    /**
     * 이름 기준 find-or-create. DB에 이미 있으면 재사용하고, 없으면 새로 만들어 즉시 저장한다(Problem은 Concept를 cascade하지 않는다).
     * [categories]에 이름이 없으면 미분류(null)로 생성된다 — 카테고리 필드 도입 이전 시드와의 하위 호환.
     */
    private fun findOrCreateConcept(name: String, cache: MutableMap<String, Concept>, categories: Map<String, ProblemCategory?>): Concept =
        cache.getOrPut(name) {
            conceptRepository.findByName(name) ?: conceptRepository.save(Concept(name, category = categories[name]))
        }

    private fun toProblem(dto: ProblemSeedDto, conceptCache: MutableMap<String, Concept>, conceptCategories: Map<String, ProblemCategory?>): Problem =
        Problem(
            // 명세: MVP는 시딩분을 승인 취급한다. 이 로더는 local 전용이며(운영 유입은 관리자 검수 경로),
            // 시드 구조는 로더 테스트의 no-leak·불변식 스윕이 전수 검증하므로 전이 검증 없이 승인으로 넣는다.
            approvalStatus = ApprovalStatus.APPROVED,
            questionText = dto.question,
            concepts = dto.concepts.map { findOrCreateConcept(it, conceptCache, conceptCategories) },
            integration = dto.integration,
            acceptableAnswers = dto.acceptableAnswers.toSet(),
            representativeAnswer = dto.representativeAnswer,
            type = dto.type?.let { ProblemType.valueOf(it) },
            difficulty = dto.difficulty?.let { Difficulty.valueOf(it) },
            codeSnippet = dto.codeSnippet,
            explanation = dto.explanation,
            enrichment = dto.enrichment?.let { toEnrichment(it) },
            hints = dto.hints.map { Hint(it.text, it.codeSnippet) },
            misconceptionHints = dto.misconceptionHints.map { MisconceptionHint(it.expectedAnswers.toSet(), it.message) },
        )

    private fun toEnrichment(dto: EnrichmentDto): Enrichment =
        Enrichment(
            title = dto.title,
            body = dto.body,
            items = dto.items.map { EnrichmentItem(it.title, it.description) },
            quote = dto.quote,
        )
}
