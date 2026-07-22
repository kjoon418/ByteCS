package watson.bytecs.interview.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import watson.bytecs.interview.domain.InterviewPrompt
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.infrastructure.ConceptRepository

/**
 * 면접 질문 서빙 게이트([InterviewPromptRepository.findApproved])를 실제 저장소 위에서 검증한다 —
 * 승인(APPROVED)된 질문만 서빙 후보로 조회되고, 초안·검수중·반려·회수는 걸러진다(계획 §3.3).
 */
@SpringBootTest
class InterviewPromptServingGateTest(
    @Autowired private val interviewPromptRepository: InterviewPromptRepository,
    @Autowired private val conceptRepository: ConceptRepository,
) {

    private lateinit var concept: Concept

    @BeforeEach
    fun setUp() {
        interviewPromptRepository.deleteAll()
        conceptRepository.deleteAll()
        concept = conceptRepository.save(Concept("스택"))
    }

    // 공유 H2를 쓰는 다른 통합 테스트가 concept를 deleteAll할 때, 남은 interview_prompt의 FK가 삭제를 막지 않도록
    // 이 테스트가 만든 면접 질문·개념을 뒤처리한다(통합 테스트 상호 격리 관례).
    @AfterEach
    fun tearDown() {
        interviewPromptRepository.deleteAll()
        conceptRepository.deleteAll()
    }

    @Test
    fun `승인된 면접 질문만 서빙 후보로 조회된다`() {
        savePrompt("승인된 질문", ApprovalStatus.APPROVED)
        savePrompt("초안 질문", ApprovalStatus.DRAFT)
        savePrompt("검수중 질문", ApprovalStatus.IN_REVIEW)
        savePrompt("반려 질문", ApprovalStatus.REJECTED)
        savePrompt("회수 질문", ApprovalStatus.RETRACTED)

        val approved = interviewPromptRepository.findApproved()

        assertThat(approved).hasSize(1)
        assertThat(approved.single().question).isEqualTo("승인된 질문")
    }

    @Test
    fun `승인된 질문은 개념과 루브릭을 함께 실어 준다`() {
        savePrompt("승인된 질문", ApprovalStatus.APPROVED, rubricPoints = listOf("포인트 A", "포인트 B"))

        val prompt = interviewPromptRepository.findApproved().single()

        assertThat(prompt.concept.name).isEqualTo("스택")
        assertThat(prompt.rubricPoints).containsExactly("포인트 A", "포인트 B")
    }

    private fun savePrompt(
        question: String,
        status: ApprovalStatus,
        rubricPoints: List<String> = listOf("핵심 포인트"),
    ) {
        interviewPromptRepository.save(
            InterviewPrompt(
                concept = concept,
                question = question,
                modelAnswer = "모범 설명",
                rubricPoints = rubricPoints,
                approvalStatus = status,
            ),
        )
    }
}
