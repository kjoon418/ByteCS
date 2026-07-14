package watson.bytecs.session.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.Judgement
import java.time.LocalDate

class SessionTest {

    private val date: LocalDate = LocalDate.of(2026, 7, 14)

    private fun sessionOf(vararg problemIds: Long): Session =
        Session.assign(userId = 1L, sessionDate = date, problemIds = problemIds.toList())

    private fun correct(answer: String = "answer"): Pair<Judgement, AnswerText> =
        Judgement.CORRECT to AnswerText(answer)

    private fun wrong(answer: String = "wrong"): Pair<Judgement, AnswerText> =
        Judgement.MISMATCH to AnswerText(answer)

    @Nested
    inner class 세션을_배정한다 {

        @Test
        fun 주어진_순서대로_위치_0부터_칸을_만든다() {
            val session = sessionOf(30L, 10L, 20L)

            assertThat(session.items.map { it.position }).containsExactly(0, 1, 2)
            assertThat(session.items.map { it.problemId }).containsExactly(30L, 10L, 20L)
            assertThat(session.status).isEqualTo(SessionStatus.IN_PROGRESS)
            assertThat(session.currentPosition).isEqualTo(0)
            assertThat(session.solvedCount).isEqualTo(0)
            assertThat(session.currentItemProblemId()).isEqualTo(30L)
        }

        @Test
        fun 빈_배정은_허용하지_않는다() {
            assertThatThrownBy { sessionOf() }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class 답을_반영한다 {

        @Test
        fun 정답이면_현재_칸을_통과하고_커서가_전진한다() {
            val session = sessionOf(10L, 20L)

            val (judgement, answer) = correct("정답1")
            session.recordAttempt(judgement, answer)

            assertThat(session.items[0].solved).isTrue()
            assertThat(session.items[0].submittedAnswer).isEqualTo(AnswerText("정답1").value)
            assertThat(session.currentPosition).isEqualTo(1)
            assertThat(session.solvedCount).isEqualTo(1)
            assertThat(session.isCompleted).isFalse()
            assertThat(session.currentItemProblemId()).isEqualTo(20L)
        }

        @Test
        fun 마지막_칸을_정답으로_통과하면_세션이_완료된다() {
            val session = sessionOf(10L, 20L)

            session.recordAttempt(Judgement.CORRECT, AnswerText("a"))
            session.recordAttempt(Judgement.CORRECT, AnswerText("b"))

            assertThat(session.isCompleted).isTrue()
            assertThat(session.status).isEqualTo(SessionStatus.COMPLETED)
            assertThat(session.solvedCount).isEqualTo(2)
            assertThat(session.currentItemProblemId()).isNull()
        }

        @Test
        fun 비정답이면_오답_횟수만_오르고_전진하지_않는다() {
            val session = sessionOf(10L, 20L)

            session.recordAttempt(Judgement.MISMATCH, AnswerText("틀림"))
            session.recordAttempt(Judgement.NEAR_MISS, AnswerText("근접"))

            assertThat(session.items[0].solved).isFalse()
            assertThat(session.items[0].wrongAttemptCount).isEqualTo(2)
            assertThat(session.currentPosition).isEqualTo(0)
            assertThat(session.solvedCount).isEqualTo(0)
        }

        @Test
        fun 이미_완료된_세션에_제출하면_예외를_던진다() {
            val session = sessionOf(10L)
            session.recordAttempt(Judgement.CORRECT, AnswerText("a"))

            assertThatThrownBy { session.recordAttempt(Judgement.CORRECT, AnswerText("b")) }
                .isInstanceOf(SessionAlreadyCompletedException::class.java)
        }
    }

    @Nested
    inner class 정답을_공개한다 {

        @Test
        fun 비정답_제출_전에는_공개할_수_없다() {
            val session = sessionOf(10L)

            assertThatThrownBy { session.reveal() }
                .isInstanceOf(RevealNotAllowedException::class.java)
        }

        @Test
        fun 한_번_이상_틀린_뒤에는_공개할_수_있다() {
            val session = sessionOf(10L)
            session.recordAttempt(Judgement.MISMATCH, AnswerText("틀림"))

            session.reveal()

            assertThat(session.items[0].revealed).isTrue()
            // 공개해도 진행은 그대로다(직접 정답을 입력해야 넘어간다).
            assertThat(session.currentPosition).isEqualTo(0)
        }

        @Test
        fun 완료된_세션에서는_공개할_수_없다() {
            val session = sessionOf(10L)
            session.recordAttempt(Judgement.CORRECT, AnswerText("a"))

            assertThatThrownBy { session.reveal() }
                .isInstanceOf(SessionAlreadyCompletedException::class.java)
        }
    }

    @Nested
    inner class 지난_문제를_조회한다 {

        @Test
        fun 이미_통과한_위치는_돌려준다() {
            val session = sessionOf(10L, 20L)
            session.recordAttempt(Judgement.CORRECT, AnswerText("정답"))

            val past = session.pastItemAt(0)

            assertThat(past.problemId).isEqualTo(10L)
            assertThat(past.solved).isTrue()
            assertThat(past.submittedAnswer).isEqualTo(AnswerText("정답").value)
        }

        @Test
        fun 현재_위치나_이후_위치는_볼_수_없다() {
            val session = sessionOf(10L, 20L)
            session.recordAttempt(Judgement.CORRECT, AnswerText("정답"))

            // 위치 1은 지금 풀 칸(아직 도달)이라 열람 불가.
            assertThatThrownBy { session.pastItemAt(1) }
                .isInstanceOf(ItemNotViewableException::class.java)
        }

        @Test
        fun 음수_위치는_볼_수_없다() {
            val session = sessionOf(10L, 20L)
            session.recordAttempt(Judgement.CORRECT, AnswerText("정답"))

            assertThatThrownBy { session.pastItemAt(-1) }
                .isInstanceOf(ItemNotViewableException::class.java)
        }
    }
}
