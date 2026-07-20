package watson.bytecs.session.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import watson.bytecs.account.domain.User
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.AttemptOutcome
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemType
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.application.ReviewService
import watson.bytecs.review.domain.MasterySignal
import watson.bytecs.session.domain.Session
import watson.bytecs.session.infrastructure.SessionRepository
import watson.bytecs.study.LearningHistory
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

/**
 * '오늘의 세션'(그 날짜의 최신 세션) get-or-create와 '조금 더 풀기'(getOrCreateNext)의 세션 선택을 검증한다.
 * 하루 여러 세션 모델(D6·D9)에서 조회는 최신 세션을 재사용하고, '조금 더 풀기'는 완료됐을 때만 새 세션을 만든다.
 * 스레드 경합 대신, 협력자 스텁으로 각 분기(있음/없음/완료/진행 중)를 결정적으로 재현한다.
 */
class SessionServiceTest {

    private val sessionRepository: SessionRepository = mock(SessionRepository::class.java)
    private val problemRepository: ProblemRepository = mock(ProblemRepository::class.java)
    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val responseMapper: SessionResponseMapper = mock(SessionResponseMapper::class.java)
    private val sessionCreator: SessionCreator = mock(SessionCreator::class.java)
    private val reviewService: ReviewService = mock(ReviewService::class.java)
    private val learningHistory: LearningHistory = mock(LearningHistory::class.java)

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
        learningHistory,
        clock,
    )

    @Test
    fun `오늘 최신 세션이 있으면 조회는 그 세션을 재사용하고 새로 만들지 않는다`() {
        val existing = Session.assign(userId = 1L, sessionDate = today, problemIds = listOf(10L))
        stubProblemAndUser(problemId = 10L)
        given(sessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(existing)

        service.getOrCreateToday(1L)

        verify(sessionCreator, never()).create(1L, today)
    }

    @Test
    fun `오늘 세션이 없으면 조회는 새 세션을 만들어 돌려준다`() {
        val created = Session.assign(userId = 1L, sessionDate = today, problemIds = listOf(10L))
        stubProblemAndUser(problemId = 10L)
        given(sessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(null)
        given(sessionCreator.create(1L, today)).willReturn(created)

        service.getOrCreateToday(1L)

        verify(sessionCreator).create(1L, today)
    }

    @Test
    fun `조금 더 풀기는 오늘 최신이 완료됐으면 새 세션을 만든다`() {
        val completed = completedSession(userId = 1L, problemId = 10L)
        val next = Session.assign(userId = 1L, sessionDate = today, problemIds = listOf(20L))
        stubProblemAndUser(problemId = 20L)
        given(sessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(completed)
        given(sessionCreator.create(1L, today)).willReturn(next)

        service.getOrCreateNext(1L)

        verify(sessionCreator).create(1L, today)
    }

    @Test
    fun `조금 더 풀기는 오늘 최신이 진행 중이면 새로 만들지 않고 그 세션을 돌려준다`() {
        val inProgress = Session.assign(userId = 1L, sessionDate = today, problemIds = listOf(10L))
        stubProblemAndUser(problemId = 10L)
        given(sessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(inProgress)

        service.getOrCreateNext(1L)

        verify(sessionCreator, never()).create(1L, today)
    }

    @Test
    fun `조금 더 풀기는 오늘 세션이 없으면 새 세션을 만든다`() {
        val created = Session.assign(userId = 1L, sessionDate = today, problemIds = listOf(10L))
        stubProblemAndUser(problemId = 10L)
        given(sessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(null)
        given(sessionCreator.create(1L, today)).willReturn(created)

        service.getOrCreateNext(1L)

        verify(sessionCreator).create(1L, today)
    }

    /**
     * D8: '이미 풀었던 문제인가'는 recordAttempt로 이번 정답이 반영되기 전에 스냅샷해야 한다.
     * 여기서는 그 스냅샷 값이 정확히 reviewService.recordSolve로 전달되는지만 검증한다
     * (숙련도 갱신 스킵/정상 갱신 자체의 판정 로직은 ReviewServiceTest가 검증한다).
     */
    @Test
    fun `정답 제출 시 이미 풀었던 문제이면 alreadySolved true로 recordSolve를 호출한다`() {
        val problem = Problem(questionText = "질문", concepts = listOf(Concept("개념")), acceptableAnswers = setOf("정답"), representativeAnswer = "정답")
        val session = Session.assign(userId = 1L, sessionDate = today, problemIds = listOf(problem.id))
        val user = User.createGuest()

        given(sessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(session)
        given(problemRepository.findById(problem.id)).willReturn(Optional.of(problem))
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        // 이번 정답 이전에 이미 이 문제를 푼 적이 있다(세션 소진 반복 폴백으로 재출제된 상황을 흉내).
        given(learningHistory.findSolvedProblemIds(1L)).willReturn(setOf(problem.id))
        // responseMapper는 목이라 이 테스트의 관심사(recordSolve 호출)와 무관 — 굳이 스텁하지 않는다.

        service.submitAnswer(1L, AnswerText("정답"))

        // 값 인자만 쓰므로(매처 없음) equals 비교로 그대로 검증한다 — Mockito 매처(any/eq)는 null을 반환해
        // Kotlin의 non-null 파라미터 호출부에서 NPE를 일으키므로 여기선 피한다.
        verify(reviewService).recordSolve(1L, problem.conceptIds(), MasterySignal.UNAIDED, today, problem.id, true)
    }

    @Test
    fun `정답 제출 시 처음 푸는 문제이면 alreadySolved false로 recordSolve를 호출한다`() {
        val problem = Problem(questionText = "질문", concepts = listOf(Concept("개념")), acceptableAnswers = setOf("정답"), representativeAnswer = "정답")
        val session = Session.assign(userId = 1L, sessionDate = today, problemIds = listOf(problem.id))
        val user = User.createGuest()

        given(sessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(session)
        given(problemRepository.findById(problem.id)).willReturn(Optional.of(problem))
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        // 이번 정답 이전엔 이 문제를 푼 적이 없다(신규 정답).
        given(learningHistory.findSolvedProblemIds(1L)).willReturn(emptySet())
        // responseMapper는 목이라 이 테스트의 관심사(recordSolve 호출)와 무관 — 굳이 스텁하지 않는다.

        service.submitAnswer(1L, AnswerText("정답"))

        verify(reviewService).recordSolve(1L, problem.conceptIds(), MasterySignal.UNAIDED, today, problem.id, false)
    }

    /**
     * D1(따라 입력): 정답을 공개한 칸에 전사 오타를 제출하면, SessionService가 그 칸의 revealed를 typeAlong으로 넘겨
     * 유도형이어도 근접(NEAR_MISS)으로 판정되게 한다. 매퍼로 흘러간 판정 결과(outcome)로 typeAlong 전달을 확인한다.
     * (근접 vs 불일치는 응답 result에만 드러나므로, 목 매퍼에 실제로 전달된 outcome을 구체값으로 검증한다 — 매처 없음.)
     */
    @Test
    fun `정답을 공개한 칸에 전사 오타를 제출하면 유도형이라도 근접으로 판정된다`() {
        val problem = derivationProblem()
        val session = Session.assign(userId = 1L, sessionDate = today, problemIds = listOf(problem.id))
        session.reveal() // 현재 칸을 정답 공개 상태로 만든다 → typeAlong=true.
        stubSubmit(session, problem)

        service.submitAnswer(1L, AnswerText("o(n)"))

        // 유형 관문이 면제돼 근접으로 판정된다. 전진하지 않으므로 다음 문제는 여전히 같은 문제이고, 완료가 아니라 스트릭은 null이다.
        verify(responseMapper).toAttemptResponse(session, AttemptOutcome(Judgement.NEAR_MISS, null), problem, problem, null)
    }

    @Test
    fun `정답을 공개하지 않은 칸에 같은 오타를 제출하면 유도형이라 불일치로 판정된다`() {
        val problem = derivationProblem()
        val session = Session.assign(userId = 1L, sessionDate = today, problemIds = listOf(problem.id))
        // reveal 하지 않는다 → typeAlong=false → 유도형 근접 금지가 그대로 적용된다.
        stubSubmit(session, problem)

        service.submitAnswer(1L, AnswerText("o(n)"))

        verify(responseMapper).toAttemptResponse(session, AttemptOutcome(Judgement.MISMATCH, null), problem, problem, null)
    }

    /** 유도형 문제(대표 정답 "o(n²)") — "o(n)"은 편집거리 1이지만 유형 관문에 막혀 평소엔 불일치다. */
    private fun derivationProblem(): Problem =
        Problem(
            questionText = "질문",
            concepts = listOf(Concept("개념")),
            acceptableAnswers = setOf("o(n²)"),
            representativeAnswer = "o(n²)",
            type = ProblemType.DERIVATION,
        )

    /** submitAnswer가 로드하는 오늘 세션·문제·사용자를 스텁한다. */
    private fun stubSubmit(session: Session, problem: Problem) {
        given(sessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(session)
        given(problemRepository.findById(problem.id)).willReturn(Optional.of(problem))
        given(userRepository.findById(1L)).willReturn(Optional.of(User.createGuest()))
    }

    /** 상태 응답 변환이 로드하는 현재 문제·사용자와, M1 진입부의 사용자 행 잠금 조회를 스텁한다(responseMapper는 목이라 반환값은 관심사가 아니다). */
    private fun stubProblemAndUser(problemId: Long) {
        val problem = Problem(questionText = "질문", concepts = listOf(Concept("개념")), acceptableAnswers = setOf("정답"), representativeAnswer = "정답")
        given(problemRepository.findById(problemId)).willReturn(Optional.of(problem))
        given(userRepository.findById(1L)).willReturn(Optional.of(User.createGuest()))
        // M1: getOrCreateToday·getOrCreateNext 진입부가 사용자 행을 잠금 조회한다(생성 경합 직렬화).
        given(userRepository.findWithLockById(1L)).willReturn(Optional.of(User.createGuest()))
    }

    /** 단일 문제를 정답으로 통과시켜 완료 상태가 된 세션을 만든다. */
    private fun completedSession(userId: Long, problemId: Long): Session =
        Session.assign(userId = userId, sessionDate = today, problemIds = listOf(problemId)).apply {
            recordAttempt(Judgement.CORRECT, AnswerText("정답"))
        }
}
