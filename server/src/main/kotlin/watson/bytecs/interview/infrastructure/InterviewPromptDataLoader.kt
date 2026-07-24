package watson.bytecs.interview.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.interview.domain.InterviewPrompt
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.infrastructure.ConceptRepository

/**
 * 애플리케이션 기동 시 리소스 JSON([resourcePath])의 면접 질문을 시딩한다(계획 §3.3 C1).
 * **local·tester 프로파일 전용**이다(문제 시드 로더와 같은 제약 — 시딩분은 승인 취급이라 운영 유입은 검수 경로만).
 *
 * **동작(기동 시 콘텐츠 업서트, 오너 결정 2026-07-25)**: DB가 비어 있으면 전량 신규 삽입한다(기존 경로).
 * DB에 이미 데이터가 있으면, 시드 항목을 개념 이름으로 기존 행과 대조해 **콘텐츠 필드만** 갱신한다
 * (이번 확정 범위: hints만 — [upsertPrompts] 참고, Problem 시드 로더 관례와 동일). 매칭되지 않은 시드 항목은
 * 신규 삽입하고, DB에는 있는데 시드에 없는 항목은 삭제하지 않고 로그만 남긴다.
 *
 * 이 로더는 곧 검증기다: JSON을 DTO로 파싱한 뒤 반드시 도메인 생성자([InterviewPrompt])로 조립하며(불변식 발동),
 * 개념은 **이름으로 기존 개념을 참조**한다 — 없으면 조용히 스킵하지 않고 기동을 시끄럽게 실패시킨다([IllegalStateException]).
 * 개념은 [watson.bytecs.problem.infrastructure.ProblemDataLoader]가 만들므로, 그보다 **뒤에 실행**되어야 한다
 * ([Order] — 문제 로더 @Order(1) 다음). 본 시드는 **빈 prompts 배열로 시작**한다(콘텐츠는 별도 워커가 후속 저작 —
 * 빈 파일이어도 정상 기동·graceful). 이 검증기 역할은 업서트 경로에서도 동일하게 유지된다 — 시드 파일 전체를
 * 항상 도메인 객체로 조립·단정한 뒤에야 신규/갱신을 가른다.
 *
 * 시드는 승인(APPROVED)으로 곧장 생성되어 전이 검증을 거치지 않으므로, saveAll 직전
 * [InterviewPrompt.assertStructurallyApprovable]로 같은 구조 가드레일(루브릭·모범 설명)을 재사용해 단정한다.
 */
@Component
@Profile("local", "tester")
@Order(2)
class InterviewPromptDataLoader(
    private val conceptRepository: ConceptRepository,
    private val interviewPromptRepository: InterviewPromptRepository,
    private val objectMapper: ObjectMapper,
    private val resourcePath: String = "seed/interview-prompts.json",
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(vararg args: String?) {
        val seedFile = loadSeedFile()
        val prompts = seedFile.prompts.map { toPrompt(it) }
        // 승인 상태로 곧장 만든 시드가 승인 요건(루브릭·모범 설명)을 실제로 충족하는지, 저장 전에 단정한다.
        // 신규 삽입·갱신 여부와 무관하게 항상 실행한다(검증기 역할은 업서트 경로에서도 동일).
        prompts.forEach { it.assertStructurallyApprovable() }

        if (interviewPromptRepository.count() == 0L) {
            interviewPromptRepository.saveAll(prompts)
            return
        }

        upsertPrompts(prompts)
    }

    /**
     * 기동 시 업서트(오너 결정 2026-07-25): 시드 면접 질문을 개념 이름으로 기존 행과 대조한다.
     *  - 매칭되면 콘텐츠 필드만 갱신한다. **이번 확정 범위는 hints(점진 공개 힌트)만**이다 — 질문·모범 설명·루브릭의
     *    재저작은 채점 기준 자체를 바꿔 이번 범위 밖으로 뒀다(범위 확대 여지는 리뷰에서 재확정).
     *    무변경이면 [InterviewPrompt.updateHints]도 뒤이은 save도 아예 호출하지 않아, 더티체킹이 불필요한 쓰기를 일으키지 않는다.
     *  - 매칭되지 않으면(신규 질문) 기존 시딩 관례대로 신규 삽입한다(같은 승인 검증 게이트를 이미 통과했다).
     *  - DB에는 있는데 시드에 없는 항목은 삭제하지 않고 로그만 남긴다.
     *
     * 변경분은 [InterviewPromptRepository.save]로 명시적으로 저장한다 — `run()`의 [Transactional]은 이 클래스가
     * 스프링이 관리하는 프록시 빈으로 호출될 때만 실제 트랜잭션 경계로 작동하므로(로컬/tester 기동 경로),
     * 더티체킹 하나에만 기대지 않고 명시적 저장으로 변경을 확정한다(ProblemDataLoader 관례와 동일).
     */
    private fun upsertPrompts(seedPrompts: List<InterviewPrompt>) {
        val existingByConceptName = interviewPromptRepository.findAllWithConcept().associateBy { it.concept.name }
        val newPrompts = mutableListOf<InterviewPrompt>()

        seedPrompts.forEach { seedPrompt ->
            val existing = existingByConceptName[seedPrompt.concept.name]
            if (existing == null) {
                newPrompts.add(seedPrompt)
                return@forEach
            }
            if (existing.hints != seedPrompt.hints) {
                existing.updateHints(seedPrompt.hints)
                interviewPromptRepository.save(existing)
            }
        }

        if (newPrompts.isNotEmpty()) {
            interviewPromptRepository.saveAll(newPrompts)
        }

        val staleConceptNames = existingByConceptName.keys - seedPrompts.map { it.concept.name }.toSet()
        if (staleConceptNames.isNotEmpty()) {
            log.info("시드에 없는 기존 면접 질문 {}건은 삭제하지 않고 유지합니다(개념): {}", staleConceptNames.size, staleConceptNames)
        }
    }

    private fun loadSeedFile(): InterviewPromptSeedFile {
        val resource = ClassPathResource(resourcePath)
        require(resource.exists()) { "면접 질문 시드 파일을 찾을 수 없습니다: $resourcePath" }
        return resource.inputStream.use { objectMapper.readValue(it, InterviewPromptSeedFile::class.java) }
    }

    private fun toPrompt(dto: InterviewPromptSeedDto): InterviewPrompt {
        // 개념은 이름으로 기존 개념을 참조한다. 없으면 문제 시드가 먼저 로드되지 않았거나 개념 이름이 틀린 것이므로 기동을 실패시킨다.
        val concept = conceptRepository.findByName(dto.concept)
            ?: throw IllegalStateException(
                "면접 질문이 참조하는 개념 '${dto.concept}'을(를) 찾을 수 없습니다. " +
                    "문제 시드가 먼저 로드되어야 하고, 개념 이름이 정확해야 합니다.",
            )
        return InterviewPrompt(
            // MVP는 시딩분을 승인 취급한다(Problem 시드 관례). 운영 유입은 관리자 검수 경로만.
            approvalStatus = ApprovalStatus.APPROVED,
            concept = concept,
            question = dto.question,
            modelAnswer = dto.modelAnswer,
            rubricPoints = dto.rubricPoints,
            hints = dto.hints,
        )
    }
}
