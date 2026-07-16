package watson.bytecs.problem.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 힌트 노출 규칙 — 개수는 항상 알리되 본문은 공개분만 잘라 내보낸다(no-leak).
 */
class ProblemHintTest {

    @Nested
    inner class 힌트_개수를_돌려준다 {

        @Test
        fun 힌트가_있으면_그_수를_돌려준다() {
            val problem = problemWithHints(Hint("약"), Hint("강"))

            assertThat(problem.hintCount).isEqualTo(2)
        }

        @Test
        fun 힌트가_없으면_0이다() {
            val problem = problemWithHints()

            assertThat(problem.hintCount).isEqualTo(0)
        }
    }

    @Nested
    inner class 공개분만_잘라_돌려준다 {

        @Test
        fun 앞에서부터_요청한_수만큼_약에서_강_순으로_돌려준다() {
            val problem = problemWithHints(Hint("약"), Hint("중"), Hint("강"))

            assertThat(problem.revealedHints(2).map { it.text }).containsExactly("약", "중")
        }

        @Test
        fun 전체_수를_넘겨_요청해도_전체까지만_돌려준다() {
            // no-leak 절단: 미공개 힌트가 없어도 요청 수가 힌트 수를 넘지 않도록 방어한다.
            val problem = problemWithHints(Hint("약"), Hint("강"))

            assertThat(problem.revealedHints(5).map { it.text }).containsExactly("약", "강")
        }

        @Test
        fun 음수를_요청하면_아무것도_돌려주지_않는다() {
            val problem = problemWithHints(Hint("약"), Hint("강"))

            assertThat(problem.revealedHints(-1)).isEmpty()
        }

        @Test
        fun 공개_수가_0이면_아무_본문도_새지_않는다() {
            val problem = problemWithHints(Hint("약"), Hint("강"))

            assertThat(problem.revealedHints(0)).isEmpty()
        }
    }

    @Nested
    inner class 힌트_본문을_검증한다 {

        @Test
        fun 본문이_비어_있으면_예외를_던진다() {
            assertThatThrownBy { Hint(" ") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("힌트 본문은 비어 있을 수 없습니다.")
        }
    }

    private fun problemWithHints(vararg hints: Hint): Problem =
        Problem(
            questionText = "질문",
            concepts = listOf(Concept("개념")),
            acceptableAnswers = setOf("정답"),
            representativeAnswer = "정답",
            type = ProblemType.DEFINITION_RECALL,
            difficulty = Difficulty.EASY,
            hints = hints.toList(),
        )
}
