package watson.bytecs.interview.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
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
 * 애플리케이션 기동 시, 면접 질문이 하나도 없을 때만 리소스 JSON([resourcePath])의 면접 질문을 시딩한다(계획 §3.3 C1).
 * **local·tester 프로파일 전용**이다(문제 시드 로더와 같은 제약 — 시딩분은 승인 취급이라 운영 유입은 검수 경로만).
 *
 * 이 로더는 곧 검증기다: JSON을 DTO로 파싱한 뒤 반드시 도메인 생성자([InterviewPrompt])로 조립하며(불변식 발동),
 * 개념은 **이름으로 기존 개념을 참조**한다 — 없으면 조용히 스킵하지 않고 기동을 시끄럽게 실패시킨다([IllegalStateException]).
 * 개념은 [watson.bytecs.problem.infrastructure.ProblemDataLoader]가 만들므로, 그보다 **뒤에 실행**되어야 한다
 * ([Order] — 문제 로더 @Order(1) 다음). 본 시드는 **빈 prompts 배열로 시작**한다(콘텐츠는 별도 워커가 후속 저작 —
 * 빈 파일이어도 정상 기동·graceful).
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

    @Transactional
    override fun run(vararg args: String?) {
        if (interviewPromptRepository.count() > 0) {
            return
        }

        val seedFile = loadSeedFile()
        val prompts = seedFile.prompts.map { toPrompt(it) }
        // 승인 상태로 곧장 만든 시드가 승인 요건(루브릭·모범 설명)을 실제로 충족하는지, 저장 전에 단정한다.
        prompts.forEach { it.assertStructurallyApprovable() }
        interviewPromptRepository.saveAll(prompts)
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
