package watson.bytecs.problem.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
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
 * 테스트는 각자 필요한 데이터를 직접 준비하도록, test 프로파일에서는 동작하지 않는다.
 *
 * 이 로더는 곧 검증기다: JSON을 DTO로 파싱한 뒤 반드시 도메인 생성자([Problem]·[Concept]·[MisconceptionHint]·
 * [Enrichment] 등)로 조립하며, 파싱 DTO를 그대로 영속화하지 않는다. 그래서 필수 필드 누락(Jackson 파싱 실패)이나
 * 불변식 위반(도메인 init require 실패)이 있으면 조용히 스킵되지 않고 기동이 시끄럽게 실패한다.
 */
@Component
@Profile("!test")
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
        problemRepository.saveAll(problems)
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
            // 명세: MVP는 시딩분을 승인 취급한다. 이 로더는 local·test 전용이며(운영 유입은 관리자 검수 경로),
            // 시드 구조는 로더 테스트의 no-leak·불변식 스윕이 전수 검증하므로 전이 검증 없이 승인으로 넣는다.
            approvalStatus = ApprovalStatus.APPROVED,
            questionText = dto.question,
            concepts = dto.concepts.map { findOrCreateConcept(it, conceptCache, conceptCategories) },
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
