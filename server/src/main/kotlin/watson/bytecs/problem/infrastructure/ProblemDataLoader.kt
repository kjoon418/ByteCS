package watson.bytecs.problem.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
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
 * 애플리케이션 기동 시, 문제가 하나도 없을 때만 리소스 JSON([resourcePath])의 CS 문제를 시딩한다.
 * **local·tester 프로파일 전용이다** — 시딩분은 승인(APPROVED)으로 들어가므로, 운영에서 동작하면 미검수 콘텐츠가
 * 즉시 서빙되어 "검수 전 실서비스 투입 금지" 가드레일을 깬다(운영 유입은 관리자 검수 경로만).
 * tester는 테스터 피드백용 배포 환경(실서비스 아님)이고 메인 시드가 테스터용으로 큐레이션된 콘텐츠이므로,
 * 즉시 승인 투입을 허용한다(오너 결정 2026-07-20, application-tester.yml 참고).
 * 테스트는 각자 필요한 데이터를 직접 준비한다(이 로더의 단위 테스트는 빈을 직접 생성한다).
 *
 * 이 로더는 곧 검증기다: JSON을 DTO로 파싱한 뒤 반드시 도메인 생성자([Problem]·[Concept]·[MisconceptionHint]·
 * [Enrichment] 등)로 조립하며, 파싱 DTO를 그대로 영속화하지 않는다. 그래서 필수 필드 누락(Jackson 파싱 실패)이나
 * 불변식 위반(도메인 init require 실패)이 있으면 조용히 스킵되지 않고 기동이 시끄럽게 실패한다.
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

    @Transactional
    override fun run(vararg args: String?) {
        if (problemRepository.count() > 0) {
            return
        }

        val seedFile = loadSeedFile()
        // 같은 이름의 개념은 이 실행 안에서 하나의 [Concept] 행으로 묶여야, 문제 간 공유(복습 인터리빙의 전제)가 성립한다.
        val conceptCache = mutableMapOf<String, Concept>()
        val conceptCategories = seedFile.conceptCategories.mapValues { (_, category) -> category?.let { ProblemCategory.valueOf(it) } }

        val problems = seedFile.problems.map { toProblem(it, conceptCache, conceptCategories) }
        // 승인 상태로 곧장 만든 시드가 승인 요건(유형 태깅·no-leak·연결 문제 개념 수)을 실제로 충족하는지, 저장 전에 단정한다.
        problems.forEach { it.assertStructurallyApprovable() }
        // 연결 문제(DI12)의 계단 보장: 각 구성 개념이 같은 시드의 단일 개념 문제로도 다뤄져야 한다(위반 시 기동 실패).
        validateIntegrationProblemsAreStaged(problems)
        problemRepository.saveAll(problems)
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
