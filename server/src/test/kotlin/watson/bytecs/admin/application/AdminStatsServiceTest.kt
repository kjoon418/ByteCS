package watson.bytecs.admin.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.interview.infrastructure.InterviewSessionRepository
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 관리자 지표 집계의 **기간 라우팅**을 검증한다: from·to가 없으면 전체 기간 쿼리, 있으면 KST 반개구간([from 00:00, to+1 00:00))의
 * Between 쿼리를 쓴다. 날짜→Instant 변환(KST)·뒤집힌 입력 방어가 이 서비스의 새 로직이라 여기서 못박는다(집계 값 자체는 저장소 쿼리 몫).
 */
class AdminStatsServiceTest {

    private val sessionRepository: SessionRepository = mock(SessionRepository::class.java)
    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val interviewSessionRepository: InterviewSessionRepository = mock(InterviewSessionRepository::class.java)

    // KST(Asia/Seoul, UTC+9) 고정 클록 — 날짜 구간 변환의 기준 존이다.
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val service = AdminStatsService(sessionRepository, userRepository, interviewSessionRepository, clock)

    @Test
    fun `기간이 없으면 전체 기간 쿼리로 집계한다`() {
        given(interviewSessionRepository.countUsersAnswered()).willReturn(7L)

        val metrics = service.collectTesterMetrics(null, null)

        assertThat(metrics.interviewAnsweredUserCount).isEqualTo(7L)
        verify(sessionRepository).countUsersStarted()
        verify(sessionRepository).countUsersCompleted()
        verify(sessionRepository).countUsersStudiedMoreAfterCompletion()
        verify(interviewSessionRepository).countUsersAnswered()
    }

    @Test
    fun `기간이 있으면 KST 반개구간으로 Between 쿼리를 쓴다`() {
        given(sessionRepository.countUsersStartedBetween(START, END_EXCLUSIVE)).willReturn(3L)

        val metrics = service.collectTesterMetrics(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 22))

        assertThat(metrics.startedUserCount).isEqualTo(3L)
        verify(sessionRepository).countUsersStartedBetween(START, END_EXCLUSIVE)
        verify(sessionRepository).countUsersCompletedBetween(START, END_EXCLUSIVE)
        verify(sessionRepository).countUsersStudiedMoreAfterCompletionBetween(START, END_EXCLUSIVE)
        verify(interviewSessionRepository).countUsersAnsweredBetween(START, END_EXCLUSIVE)
        // 기간이 지정되면 전체 기간 쿼리는 쓰지 않는다.
        verify(sessionRepository, never()).countUsersStarted()
    }

    @Test
    fun `시작일과 종료일이 뒤집혀 들어와도 같은 구간으로 관대하게 조회한다`() {
        service.collectTesterMetrics(LocalDate.of(2026, 7, 22), LocalDate.of(2026, 7, 20))

        verify(sessionRepository).countUsersStartedBetween(START, END_EXCLUSIVE)
    }

    private companion object {
        // 2026-07-20 00:00 KST = 2026-07-19T15:00Z, (2026-07-22+1) 00:00 KST = 2026-07-23 00:00 KST = 2026-07-22T15:00Z
        val START: Instant = Instant.parse("2026-07-19T15:00:00Z")
        val END_EXCLUSIVE: Instant = Instant.parse("2026-07-22T15:00:00Z")
    }
}
