package watson.bytecs.extrastudy.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import watson.bytecs.problem.domain.Judgement

/**
 * 추가 학습 애그리거트의 불변식을 검증한다.
 * 한 번에 한 문제(재진입 이어 풀기)·정답 승격·정답 공개·힌트 더블탭 가드·열린 항목 부재 시 예외를 단위로 확인한다.
 */
class ExtraStudyTest {

    private companion object {
        const val USER_ID = 7L
        const val P1 = 11L
        const val P2 = 22L
    }

    @Test
    fun `열린 항목이 없으면 새 문제를 열어 이어 풀 문제로 고정한다`() {
        val extraStudy = ExtraStudy.create(USER_ID)

        extraStudy.assignOpen(P1)

        assertThat(extraStudy.openProblemId()).isEqualTo(P1)
    }

    @Test
    fun `이미 열린 항목이 있으면 다시 뽑아도 갈아끼우지 않는다`() {
        val extraStudy = ExtraStudy.create(USER_ID)
        extraStudy.assignOpen(P1)

        // 새로고침으로 다른 문제를 다시 뽑는 악용 차단: 재선정해도 처음 연 문제를 유지한다.
        extraStudy.assignOpen(P2)

        assertThat(extraStudy.openProblemId()).isEqualTo(P1)
    }

    @Test
    fun `정답이면 solved로 승격하고 열린 항목을 비운다`() {
        val extraStudy = ExtraStudy.create(USER_ID)
        extraStudy.assignOpen(P1)

        extraStudy.recordAttempt(Judgement.CORRECT, submittedAnswer = "정답")

        assertThat(extraStudy.openProblemId()).isNull()
        assertThat(extraStudy.solvedProblemIds).containsExactly(P1)
    }

    @Test
    fun `비정답이면 열린 항목을 유지하고 승격하지 않는다`() {
        val extraStudy = ExtraStudy.create(USER_ID)
        extraStudy.assignOpen(P1)

        extraStudy.recordAttempt(Judgement.MISMATCH, submittedAnswer = "오답")

        assertThat(extraStudy.openProblemId()).isEqualTo(P1)
        assertThat(extraStudy.solvedProblemIds).isEmpty()
    }

    @Test
    fun `정답으로 비운 뒤 다음 문제를 새로 열 수 있다`() {
        val extraStudy = ExtraStudy.create(USER_ID)
        extraStudy.assignOpen(P1)
        extraStudy.recordAttempt(Judgement.CORRECT, submittedAnswer = "정답")

        extraStudy.assignOpen(P2)

        assertThat(extraStudy.openProblemId()).isEqualTo(P2)
        assertThat(extraStudy.solvedProblemIds).containsExactly(P1)
    }

    @Test
    fun `정답 공개는 열린 항목에 기록되고 진행을 바꾸지 않는다`() {
        val extraStudy = ExtraStudy.create(USER_ID)
        extraStudy.assignOpen(P1)

        extraStudy.reveal()

        assertThat(extraStudy.openItem!!.revealed).isTrue()
        assertThat(extraStudy.openProblemId()).isEqualTo(P1)
    }

    @Test
    fun `힌트는 클라가 아는 공개 수와 일치할 때만 하나 더 열린다`() {
        val extraStudy = ExtraStudy.create(USER_ID)
        extraStudy.assignOpen(P1)

        assertThat(extraStudy.revealHint(expectedRevealedCount = 0, hintCount = 2)).isEqualTo(1)
        // 더블탭(여전히 0을 들고 다시 누름)은 증가하지 않는다.
        assertThat(extraStudy.revealHint(expectedRevealedCount = 0, hintCount = 2)).isEqualTo(1)
        assertThat(extraStudy.revealHint(expectedRevealedCount = 1, hintCount = 2)).isEqualTo(2)
        // 모두 열었으면 더 열려 해도 증가하지 않는다.
        assertThat(extraStudy.revealHint(expectedRevealedCount = 2, hintCount = 2)).isEqualTo(2)
    }

    @Test
    fun `오답 교정 힌트를 본 오답은 열린 항목에 마킹된다`() {
        val extraStudy = ExtraStudy.create(USER_ID)
        extraStudy.assignOpen(P1)

        extraStudy.recordAttempt(Judgement.MISMATCH, submittedAnswer = "오답", misconceptionShown = true)

        assertThat(extraStudy.openItem!!.misconceptionHintSeen).isTrue()
    }

    @Test
    fun `열린 항목이 없으면 답 제출은 예외다`() {
        val extraStudy = ExtraStudy.create(USER_ID)

        assertThatThrownBy { extraStudy.recordAttempt(Judgement.CORRECT, submittedAnswer = "정답") }
            .isInstanceOf(ExtraStudyNoOpenItemException::class.java)
    }

    @Test
    fun `열린 항목이 없으면 정답 공개는 예외다`() {
        val extraStudy = ExtraStudy.create(USER_ID)

        assertThatThrownBy { extraStudy.reveal() }
            .isInstanceOf(ExtraStudyNoOpenItemException::class.java)
    }

    @Test
    fun `열린 항목이 없으면 힌트 공개는 예외다`() {
        val extraStudy = ExtraStudy.create(USER_ID)

        assertThatThrownBy { extraStudy.revealHint(expectedRevealedCount = 0, hintCount = 2) }
            .isInstanceOf(ExtraStudyNoOpenItemException::class.java)
    }
}
