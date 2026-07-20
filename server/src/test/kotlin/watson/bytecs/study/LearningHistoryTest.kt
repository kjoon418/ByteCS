package watson.bytecs.study

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import watson.bytecs.session.infrastructure.SessionRepository

/**
 * 학습 이력을 세션 리포지토리에서 집합으로 돌려주는지 검증한다(D6·D9 일원화 이후 세션 단독 출처).
 * solved는 세션에서 정답으로 통과한 문제, assigned는 세션에서 배정받은 문제(중복 제거).
 */
class LearningHistoryTest {

    private val sessionRepository = mock(SessionRepository::class.java)
    private val learningHistory = LearningHistory(sessionRepository)

    private companion object {
        const val USER_ID = 3L
    }

    @Test
    fun `푼 문제는 세션 풀이 이력을 집합으로 돌려준다`() {
        given(sessionRepository.findSolvedProblemIds(USER_ID)).willReturn(listOf(1L, 2L, 2L))

        assertThat(learningHistory.findSolvedProblemIds(USER_ID)).containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    fun `배정 이력은 세션 배정 이력을 집합으로 돌려준다`() {
        given(sessionRepository.findAssignedProblemIds(USER_ID)).willReturn(listOf(1L, 2L, 2L))

        assertThat(learningHistory.findAssignedProblemIds(USER_ID)).containsExactlyInAnyOrder(1L, 2L)
    }
}
