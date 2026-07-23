package watson.bytecs.interview.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * 면접 세션 애그리거트의 진행 규칙을 검증한다: 채점 성공/폴백 모두 진행을 전진시키고, 마지막 칸을 답하면 완료된다.
 * 재제출은 없다(1문제 1채점, 계획 §3.3).
 */
class InterviewSessionTest {

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 7, 23)
        val JUDGED_AT: Instant = Instant.parse("2026-07-23T00:00:00Z")
    }

    @Test
    fun `배정 직후 현재 질문은 첫 칸이고 채점 성공 칸이 없다`() {
        val session = InterviewSession.assign(1L, TODAY, listOf(10L, 20L, 30L))

        assertThat(session.currentPromptId()).isEqualTo(10L)
        assertThat(session.currentPosition).isEqualTo(0)
        assertThat(session.totalCount).isEqualTo(3)
        assertThat(session.hasSuccessfulJudge()).isFalse()
    }

    @Test
    fun `채점 성공은 다음 칸으로 전진시키고 마지막 칸이면 완료한다`() {
        val session = InterviewSession.assign(1L, TODAY, listOf(10L, 20L))

        session.recordGraded("설명1", listOf(true, false), "코멘트", JUDGED_AT)
        assertThat(session.currentPromptId()).isEqualTo(20L)
        assertThat(session.isCompleted).isFalse()
        assertThat(session.hasSuccessfulJudge()).isTrue()

        session.recordGraded("설명2", listOf(true, true), "코멘트", JUDGED_AT)
        assertThat(session.isCompleted).isTrue()
        assertThat(session.currentPromptId()).isNull()
    }

    @Test
    fun `채점 폴백도 진행을 전진시키지만 채점 성공 칸으로 세지 않는다`() {
        val session = InterviewSession.assign(1L, TODAY, listOf(10L, 20L))

        session.recordFallback("설명1", JUDGED_AT)

        assertThat(session.currentPromptId()).isEqualTo(20L)
        assertThat(session.hasSuccessfulJudge()).isFalse()
    }

    @Test
    fun `전량 폴백으로 완료돼도 채점 성공 칸은 없다`() {
        val session = InterviewSession.assign(1L, TODAY, listOf(10L))

        session.recordFallback("설명1", JUDGED_AT)

        assertThat(session.isCompleted).isTrue()
        assertThat(session.hasSuccessfulJudge()).isFalse()
    }

    @Test
    fun `이미 완료된 세션에 다시 제출하면 예외가 발생한다`() {
        val session = InterviewSession.assign(1L, TODAY, listOf(10L))
        session.recordGraded("설명1", listOf(true), "코멘트", JUDGED_AT)

        assertThatThrownBy { session.recordGraded("설명2", listOf(true), "코멘트", JUDGED_AT) }
            .isInstanceOf(InterviewSessionAlreadyCompletedException::class.java)
        assertThatThrownBy { session.recordFallback("설명2", JUDGED_AT) }
            .isInstanceOf(InterviewSessionAlreadyCompletedException::class.java)
    }

    @Test
    fun `배정할 질문이 없으면 세션을 만들 수 없다`() {
        assertThatThrownBy { InterviewSession.assign(1L, TODAY, emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
