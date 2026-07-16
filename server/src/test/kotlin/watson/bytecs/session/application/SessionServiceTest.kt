package watson.bytecs.session.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.dao.DataIntegrityViolationException
import watson.bytecs.account.domain.User
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.application.ReviewService
import watson.bytecs.session.application.dto.SessionStateResponse
import watson.bytecs.session.application.dto.StreakResponse
import watson.bytecs.session.domain.Session
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

/**
 * get-or-create 경합 복구를 결정적으로 검증한다(REQUIRES_NEW 격리 덕분에 진 요청도 500 없이 기존 세션을 얻는다).
 * 스레드 경합 대신, 협력자 스텁으로 '생성 실패 → 재조회 성공' 경로를 그대로 재현한다.
 */
class SessionServiceTest {

    private val sessionRepository: SessionRepository = mock(SessionRepository::class.java)
    private val problemRepository: ProblemRepository = mock(ProblemRepository::class.java)
    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val responseMapper: SessionResponseMapper = mock(SessionResponseMapper::class.java)
    private val sessionCreator: SessionCreator = mock(SessionCreator::class.java)
    private val reviewService: ReviewService = mock(ReviewService::class.java)

    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val today: LocalDate = LocalDate.of(2026, 7, 14)
    private val clock: Clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)

    private val service = SessionService(
        sessionRepository,
        problemRepository,
        userRepository,
        responseMapper,
        sessionCreator,
        reviewService,
        clock,
    )

    @Test
    fun `생성 경합에서 진 요청은 500 없이 이미 만들어진 세션을 돌려준다`() {
        val winner = Session.assign(userId = 1L, sessionDate = today, problemIds = listOf(10L))
        val problem = Problem(questionText = "질문", concepts = listOf(Concept("개념")), acceptableAnswers = setOf("정답"), representativeAnswer = "정답")
        val user = User.createGuest()
        val expected = stateResponse()

        // 최초 조회는 없음 → 생성 시도가 유니크 경합으로 실패 → 재조회에서 승자의 세션을 본다.
        given(sessionRepository.findByUserIdAndSessionDate(1L, today)).willReturn(null, winner)
        given(sessionCreator.createInNewTransaction(1L, today))
            .willThrow(DataIntegrityViolationException("uk_study_session_user_date"))
        given(problemRepository.findById(10L)).willReturn(Optional.of(problem))
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        given(responseMapper.toStateResponse(winner, problem, user.streak)).willReturn(expected)

        val result = service.getOrCreateToday(1L)

        assertThat(result).isEqualTo(expected)
        // 사전 조회(null) 1회 + 경합 복구 재조회 1회.
        verify(sessionRepository, times(2)).findByUserIdAndSessionDate(1L, today)
    }

    @Test
    fun `재조회가 비는 무결성 위반은 그대로 전파되어 중립 CONFLICT로 매핑된다`() {
        // 생성이 무결성 위반으로 실패했는데 재조회도 비어 있으면(우리가 아는 경합이 아니면),
        // DataIntegrityViolationException을 그대로 전파한다. 전역 핸들러가 이를 중립 CONFLICT(409)로 매핑하므로
        // 계정 슬라이스의 EMAIL_DUPLICATED로 오분류되지 않는다.
        given(sessionRepository.findByUserIdAndSessionDate(1L, today)).willReturn(null, null)
        given(sessionCreator.createInNewTransaction(1L, today))
            .willThrow(DataIntegrityViolationException("some other constraint"))

        assertThatThrownBy { service.getOrCreateToday(1L) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun stateResponse(): SessionStateResponse =
        SessionStateResponse(
            sessionId = 1L,
            sessionDate = today,
            status = "IN_PROGRESS",
            solvedCount = 0,
            totalCount = 1,
            position = 0,
            currentProblem = null,
            streak = StreakResponse(count = 0, lastStudyDate = null),
        )
}
