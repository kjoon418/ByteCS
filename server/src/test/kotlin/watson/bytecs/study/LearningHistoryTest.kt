package watson.bytecs.study

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import watson.bytecs.extrastudy.infrastructure.ExtraStudyRepository
import watson.bytecs.session.infrastructure.SessionRepository

/**
 * 세션 이력과 추가 학습 이력을 합집합으로 합치는지 검증한다.
 * solved는 두 활동의 풀이 합집합, assigned는 두 활동의 배정 이력 ∪ 추가 학습의 열린 문제까지 포함한다.
 */
class LearningHistoryTest {

    private val sessionRepository = mock(SessionRepository::class.java)
    private val extraStudyRepository = mock(ExtraStudyRepository::class.java)
    private val learningHistory = LearningHistory(sessionRepository, extraStudyRepository)

    private companion object {
        const val USER_ID = 3L
    }

    @Test
    fun `푼 문제는 세션과 추가 학습의 합집합이다`() {
        given(sessionRepository.findSolvedProblemIds(USER_ID)).willReturn(listOf(1L, 2L))
        given(extraStudyRepository.findSolvedProblemIds(USER_ID)).willReturn(listOf(2L, 3L))

        assertThat(learningHistory.findSolvedProblemIds(USER_ID)).containsExactlyInAnyOrder(1L, 2L, 3L)
    }

    @Test
    fun `배정 이력은 세션 배정과 추가 학습 풀이 그리고 열린 문제의 합집합이다`() {
        given(sessionRepository.findAssignedProblemIds(USER_ID)).willReturn(listOf(1L, 2L))
        given(extraStudyRepository.findSolvedProblemIds(USER_ID)).willReturn(listOf(3L))
        given(extraStudyRepository.findOpenProblemId(USER_ID)).willReturn(4L)

        assertThat(learningHistory.findAssignedProblemIds(USER_ID)).containsExactlyInAnyOrder(1L, 2L, 3L, 4L)
    }

    @Test
    fun `열린 문제가 없으면 배정 이력에 열린 문제를 더하지 않는다`() {
        given(sessionRepository.findAssignedProblemIds(USER_ID)).willReturn(listOf(1L))
        given(extraStudyRepository.findSolvedProblemIds(USER_ID)).willReturn(emptyList())
        given(extraStudyRepository.findOpenProblemId(USER_ID)).willReturn(null)

        assertThat(learningHistory.findAssignedProblemIds(USER_ID)).containsExactly(1L)
    }
}
