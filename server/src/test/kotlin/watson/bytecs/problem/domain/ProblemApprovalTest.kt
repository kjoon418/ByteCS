package watson.bytecs.problem.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 콘텐츠 승인 상태 전이 규칙과, 승인 시 결정적 구조 검증(콘텐츠 신뢰성 가드레일)을 검증한다.
 */
class ProblemApprovalTest {

    @Test
    fun `신규 문제는 초안 상태다`() {
        val problem = approvableProblem()

        assertThat(problem.approvalStatus).isEqualTo(ApprovalStatus.DRAFT)
    }

    @Nested
    inner class 검수를_시작한다 {

        @Test
        fun `초안 상태에서 검수를 시작할 수 있다`() {
            val problem = approvableProblem()

            problem.startReview()

            assertThat(problem.approvalStatus).isEqualTo(ApprovalStatus.IN_REVIEW)
        }

        @Test
        fun `반려된 문제는 다시 검수를 시작할 수 있다`() {
            val rejectedProblem = approvableProblem().apply {
                startReview()
                reject()
            }

            rejectedProblem.startReview()

            assertThat(rejectedProblem.approvalStatus).isEqualTo(ApprovalStatus.IN_REVIEW)
        }

        @Test
        fun `회수된 문제는 다시 검수를 시작할 수 있다`() {
            val retractedProblem = approvableProblem().apply {
                startReview()
                approve()
                retract()
            }

            retractedProblem.startReview()

            assertThat(retractedProblem.approvalStatus).isEqualTo(ApprovalStatus.IN_REVIEW)
        }

        @Test
        fun `승인 상태에서 검수를 시작하면 예외를 던진다`() {
            val approvedProblem = approvableProblem().apply {
                startReview()
                approve()
            }

            assertThatThrownBy { approvedProblem.startReview() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
        }
    }

    @Nested
    inner class 승인한다 {

        @Test
        fun `검수중 상태에서 구조 검증을 통과하면 승인된다`() {
            val problem = approvableProblem().apply { startReview() }

            problem.approve()

            assertThat(problem.approvalStatus).isEqualTo(ApprovalStatus.APPROVED)
        }

        @Test
        fun `초안 상태에서 바로 승인하면 예외를 던진다`() {
            val problem = approvableProblem()

            assertThatThrownBy { problem.approve() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
        }

        @Test
        fun `문제 유형 태깅이 없으면 승인할 수 없다`() {
            // 유형은 근접 신호·복습 선정 분기의 입력이라, 유형 없는 문제는 서빙 동작이 정의되지 않는다(명세 수용 기준 23).
            val untypedProblem = approvableProblem(type = null).apply { startReview() }

            assertThatThrownBy { untypedProblem.approve() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
                .hasMessage(Problem.TYPE_REQUIRED_MESSAGE)
        }

        @Test
        fun `힌트가 정답을 노출하면 승인할 수 없다`() {
            val leakingProblem = approvableProblem(
                hints = listOf(Hint("정답은 스택입니다.", null)),
            ).apply { startReview() }

            assertThatThrownBy { leakingProblem.approve() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
                .hasMessage(Problem.ANSWER_LEAK_MESSAGE)
        }

        @Test
        fun `힌트의 정답 노출 판정은 정규화 기준이다`() {
            // 허용답 "Stack"은 정규화로 "stack"이 되고, 힌트 본문도 소문자화해 대조한다(시드 no-leak 스윕과 동일 기준).
            val leakingProblem = approvableProblem(
                acceptableAnswers = setOf("Stack"),
                representativeAnswer = "Stack",
                hints = listOf(Hint("영어로 STACK이라고 부릅니다.", null)),
            ).apply { startReview() }

            assertThatThrownBy { leakingProblem.approve() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
                .hasMessage(Problem.ANSWER_LEAK_MESSAGE)
        }

        @Test
        fun `오답 교정 힌트가 정답을 노출하면 승인할 수 없다`() {
            val leakingProblem = approvableProblem(
                misconceptionHints = listOf(MisconceptionHint(setOf("큐"), "큐가 아니라 스택을 생각해보세요.")),
            ).apply { startReview() }

            assertThatThrownBy { leakingProblem.approve() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
                .hasMessage(Problem.ANSWER_LEAK_MESSAGE)
        }

        @Test
        fun `정답을 노출하지 않는 힌트와 교정 힌트는 승인을 막지 않는다`() {
            val problem = approvableProblem(
                hints = listOf(Hint("후입선출 구조를 생각해보세요.", null)),
                misconceptionHints = listOf(MisconceptionHint(setOf("큐"), "그 구조는 선입선출입니다. 반대를 생각해보세요.")),
            ).apply { startReview() }

            problem.approve()

            assertThat(problem.approvalStatus).isEqualTo(ApprovalStatus.APPROVED)
        }

        @Test
        fun `연결 문제로 지정됐는데 개념이 2개 미만이면 승인할 수 없다`() {
            // DI12: 연결 문제는 여러 개념을 잇는다는 정의상 개념 2개 이상이어야 한다. 단일 개념 지정은 게이트를 무의미하게 만든다.
            val singleConceptIntegration = approvableProblem(integration = true).apply { startReview() }

            assertThatThrownBy { singleConceptIntegration.approve() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
                .hasMessage(Problem.INTEGRATION_CONCEPTS_MESSAGE)
        }

        @Test
        fun `연결 문제로 지정되고 개념이 2개 이상이면 승인된다`() {
            val integration = approvableProblem(
                integration = true,
                concepts = listOf(Concept("스택"), Concept("큐")),
            ).apply { startReview() }

            integration.approve()

            assertThat(integration.approvalStatus).isEqualTo(ApprovalStatus.APPROVED)
        }

        @Test
        fun `연결 문제로 지정하지 않으면 다개념이 아니어도 승인된다`() {
            // 미지정 문제는 개념 수 제약이 없다(DI12: 다개념 태깅 자체는 게이트·승인 요건과 무관).
            val problem = approvableProblem(integration = false).apply { startReview() }

            problem.approve()

            assertThat(problem.approvalStatus).isEqualTo(ApprovalStatus.APPROVED)
        }
    }

    @Nested
    inner class 반려한다 {

        @Test
        fun `검수중 상태에서 반려할 수 있다`() {
            val problem = approvableProblem().apply { startReview() }

            problem.reject()

            assertThat(problem.approvalStatus).isEqualTo(ApprovalStatus.REJECTED)
        }

        @Test
        fun `검수중이 아닌 상태에서 반려하면 예외를 던진다`() {
            val problem = approvableProblem()

            assertThatThrownBy { problem.reject() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
        }
    }

    @Nested
    inner class 회수한다 {

        @Test
        fun `승인 상태에서 회수할 수 있다`() {
            val approvedProblem = approvableProblem().apply {
                startReview()
                approve()
            }

            approvedProblem.retract()

            assertThat(approvedProblem.approvalStatus).isEqualTo(ApprovalStatus.RETRACTED)
        }

        @Test
        fun `승인되지 않은 문제를 회수하면 예외를 던진다`() {
            val problem = approvableProblem().apply { startReview() }

            assertThatThrownBy { problem.retract() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
        }
    }

    private fun approvableProblem(
        acceptableAnswers: Set<String> = setOf("스택"),
        representativeAnswer: String = "스택",
        type: ProblemType? = ProblemType.DEFINITION_RECALL,
        hints: List<Hint> = emptyList(),
        misconceptionHints: List<MisconceptionHint> = emptyList(),
        concepts: List<Concept> = listOf(Concept("스택")),
        integration: Boolean = false,
    ): Problem =
        Problem(
            questionText = "가장 나중에 넣은 데이터가 먼저 나오는 후입선출 자료구조는?",
            concepts = concepts,
            integration = integration,
            acceptableAnswers = acceptableAnswers,
            representativeAnswer = representativeAnswer,
            type = type,
            hints = hints,
            misconceptionHints = misconceptionHints,
        )
}
