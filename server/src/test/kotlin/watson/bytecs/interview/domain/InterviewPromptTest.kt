package watson.bytecs.interview.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.InvalidApprovalStateException

/**
 * 면접 질문(개념 귀속 큐레이션 콘텐츠)의 불변식과 승인 상태 전이(문제와 같은 모델 재사용)를 검증한다.
 */
class InterviewPromptTest {

    @Nested
    inner class 불변식 {

        @Test
        fun `질문 문구가 비어 있으면 만들 수 없다`() {
            assertThatThrownBy { prompt(question = "  ") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `모범 설명이 비어 있으면 만들 수 없다`() {
            assertThatThrownBy { prompt(modelAnswer = "") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `루브릭이 비어 있으면 만들 수 없다`() {
            assertThatThrownBy { prompt(rubricPoints = emptyList()) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `루브릭에 빈 포인트가 있으면 만들 수 없다`() {
            assertThatThrownBy { prompt(rubricPoints = listOf("핵심 1", "  ")) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `루브릭 순서는 태깅 순서 그대로 보존된다`() {
            val prompt = prompt(rubricPoints = listOf("첫째", "둘째", "셋째"))

            assertThat(prompt.rubricPoints).containsExactly("첫째", "둘째", "셋째")
        }
    }

    @Nested
    inner class 승인_상태_전이 {

        @Test
        fun `신규 면접 질문은 초안 상태다`() {
            assertThat(prompt().approvalStatus).isEqualTo(ApprovalStatus.DRAFT)
        }

        @Test
        fun `초안에서 검수를 시작할 수 있다`() {
            val prompt = prompt().apply { startReview() }

            assertThat(prompt.approvalStatus).isEqualTo(ApprovalStatus.IN_REVIEW)
        }

        @Test
        fun `검수중에서 구조 검증을 통과하면 승인된다`() {
            val prompt = prompt().apply { startReview() }

            prompt.approve()

            assertThat(prompt.approvalStatus).isEqualTo(ApprovalStatus.APPROVED)
        }

        @Test
        fun `초안에서 바로 승인하면 예외를 던진다`() {
            assertThatThrownBy { prompt().approve() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
        }

        @Test
        fun `검수중에서 반려할 수 있고 반려 후 다시 검수를 시작할 수 있다`() {
            val prompt = prompt().apply { startReview() }

            prompt.reject()
            assertThat(prompt.approvalStatus).isEqualTo(ApprovalStatus.REJECTED)

            prompt.startReview()
            assertThat(prompt.approvalStatus).isEqualTo(ApprovalStatus.IN_REVIEW)
        }

        @Test
        fun `승인된 질문을 회수할 수 있고 회수 후 다시 검수를 시작할 수 있다`() {
            val prompt = prompt().apply {
                startReview()
                approve()
            }

            prompt.retract()
            assertThat(prompt.approvalStatus).isEqualTo(ApprovalStatus.RETRACTED)

            prompt.startReview()
            assertThat(prompt.approvalStatus).isEqualTo(ApprovalStatus.IN_REVIEW)
        }

        @Test
        fun `승인 상태에서 검수를 다시 시작하면 예외를 던진다`() {
            val prompt = prompt().apply {
                startReview()
                approve()
            }

            assertThatThrownBy { prompt.startReview() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
        }

        @Test
        fun `승인되지 않은 질문을 회수하면 예외를 던진다`() {
            assertThatThrownBy { prompt().apply { startReview() }.retract() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
        }
    }

    @Nested
    inner class 승인_구조_검증 {

        @Test
        fun `루브릭과 모범 설명을 갖춘 질문은 구조 검증을 통과한다`() {
            // 전이 없이도(시드 직접 승인 경로) 구조 요건을 통과하는지 확인한다.
            prompt().assertStructurallyApprovable()
        }
    }

    private fun prompt(
        concept: Concept = Concept("스택"),
        question: String = "스택을 설명해보세요.",
        modelAnswer: String = "후입선출(LIFO) 구조로, 가장 나중에 넣은 데이터가 먼저 나온다.",
        rubricPoints: List<String> = listOf("LIFO 언급", "push/pop 동작"),
        approvalStatus: ApprovalStatus = ApprovalStatus.DRAFT,
    ): InterviewPrompt =
        InterviewPrompt(
            concept = concept,
            question = question,
            modelAnswer = modelAnswer,
            rubricPoints = rubricPoints,
            approvalStatus = approvalStatus,
        )
}
