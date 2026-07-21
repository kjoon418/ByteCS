package watson.bytecs.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.bytecs.session.FakeSessionRepository.Companion.activeSession
import watson.bytecs.session.FakeSessionRepository.Companion.completedSession
import watson.bytecs.session.FakeSessionRepository.Companion.correctOutcome
import watson.bytecs.session.FakeSessionRepository.Companion.mismatchOutcome
import watson.bytecs.session.FakeSessionRepository.Companion.nearMissOutcome
import watson.bytecs.session.FakeSessionRepository.Companion.problem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SessionViewModel 검증. 세션 연동 진행·무낙인 피드백·정답 공개·완료(일회성 이벤트)·지난 문제·이중 제출 가드.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun SessionViewModel.active(): SessionUiState.Active =
        uiState.value as SessionUiState.Active

    @Test
    fun load_active_showsCurrentProblem() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 1, total = 3, solved = 1, problem = problem(2L)))
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        val state = viewModel.active()
        assertEquals(2L, state.problem.id)
        assertEquals(2, state.current, "표시용 번호는 position+1")
        assertEquals(3, state.total)
    }

    @Test
    fun loadFailure_yieldsError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeSessionRepository().apply { getTodayError = RuntimeException("down") }
        val viewModel = SessionViewModel(repo).apply { loadSession() }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SessionUiState.Error)
    }

    /** 풀이 화면 진입(활성 세션 로드) 시, 테스터 지표를 위해 시작을 서버에 표시한다. */
    @Test
    fun load_active_reportsStarted() = runTest {
        val repo = FakeSessionRepository(today = activeSession())
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        assertEquals(1, repo.markStartedCount)
        assertTrue(viewModel.uiState.value is SessionUiState.Active)
    }

    /** 지표 기록(markStarted) 실패는 부수 효과라 풀이 흐름을 막지 않는다 — 화면은 정상 활성 상태로 남는다. */
    @Test
    fun markStartedFailure_doesNotBreakSession() = runTest {
        val repo = FakeSessionRepository(today = activeSession()).apply { markStartedError = RuntimeException("down") }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        assertEquals(1, repo.markStartedCount)
        assertTrue(viewModel.uiState.value is SessionUiState.Active)
    }

    /** 이미 완료된 세션으로 진입하면 곧장 완료로 넘어가므로 풀이 시작으로 세지 않는다(지표 1 오염 방지). */
    @Test
    fun load_completed_doesNotReportStarted() = runTest {
        val repo = FakeSessionRepository(today = completedSession(total = 3))
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        assertEquals(0, repo.markStartedCount)
    }

    // ── D6·D9 일원화: '조금 더 풀기'(추가 학습 폐지, 새 세션 재진입) ─────────────

    /** startNext=true면 getToday가 아니라 startNextSession(POST /today/next)으로 진입한다. */
    @Test
    fun loadSession_withStartNext_callsStartNextSession_notGetToday() = runTest {
        val repo = FakeSessionRepository(today = completedSession(total = 3))
        repo.nextSession = activeSession(position = 0, total = 3, solved = 0, problem = problem(9L))
        val viewModel = SessionViewModel(repo).apply { loadSession(startNext = true) }

        assertEquals(1, repo.startNextCount, "startNextSession이 호출된다")
        assertEquals(9L, viewModel.active().problem.id, "새로 만들어진(또는 반환된) 세션으로 진입한다")
    }

    /** startNext=false(기본)는 기존과 동일하게 getToday만 호출한다 — startNextSession에는 닿지 않는다. */
    @Test
    fun loadSession_withoutStartNext_neverCallsStartNextSession() = runTest {
        val repo = FakeSessionRepository(today = activeSession())
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        assertEquals(0, repo.startNextCount)
        assertTrue(viewModel.uiState.value is SessionUiState.Active)
    }

    /**
     * ⭐️ 재시도(오류 화면의 [onRetry] = `viewModel::loadSession`, 인자 없음)는 직전 진입 방식을 그대로
     * 재사용한다 — '조금 더 풀기'로 진입했다가 실패했으면, 인자 없는 재시도도 startNextSession으로 간다.
     * (그렇지 않으면 재시도가 조용히 getToday로 새 완료된 세션을 다시 불러 완료 화면으로 되돌아가 버린다.)
     */
    @Test
    fun retryAfterStartNextFailure_reusesStartNext() = runTest {
        val repo = FakeSessionRepository(today = completedSession(total = 3)).apply {
            startNextError = RuntimeException("network")
        }
        val viewModel = SessionViewModel(repo).apply { loadSession(startNext = true) }
        assertTrue(viewModel.uiState.value is SessionUiState.Error)
        assertEquals(1, repo.startNextCount)

        repo.startNextError = null
        repo.nextSession = activeSession(position = 0, total = 3, problem = problem(9L))
        viewModel.loadSession() // 재시도 — 인자 없음

        assertEquals(2, repo.startNextCount, "재시도도 startNextSession으로 간다(getToday로 새지 않는다)")
        assertEquals(9L, viewModel.active().problem.id)
    }

    @Test
    fun alreadyCompletedOnLoad_emitsCompletedEvent() = runTest {
        val repo = FakeSessionRepository(today = completedSession(total = 5, streak = Streak(3, "2026-07-14")))
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        val event = viewModel.events.first()
        assertTrue(event is SessionEvent.Completed)
        assertEquals(5, event.summary.solvedCount)
        assertEquals(3, event.summary.streak?.count)
    }

    @Test
    fun mismatch_yieldsNeutralFeedback_andRetainsInput() = runTest {
        val repo = FakeSessionRepository().apply { onSubmit = { mismatchOutcome() } }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("프로세스")
        viewModel.submit()

        val state = viewModel.active()
        assertEquals(SessionFeedback.Mismatch(), state.feedback)
        assertEquals("프로세스", state.inputText, "불일치 후에도 입력 유지")
        assertFalse(state.solved)
    }

    // ── D2: 재시도 안내 근거(wrongAttemptCount) ─────────────────────────────────

    /**
     * ⭐️ 같은 칸(같은 problem.id)이 돌아와도 서버가 준 최신 문제 데이터로 갱신해야 한다 — 특히
     * wrongAttemptCount는 이 제출로 늘어난 값이므로, feedback만 얹고 problem을 그대로 두면(예전 동작)
     * 재시도 안내가 한 박자 늦게 반영된다(다음 로드까지 갱신 안 됨).
     */
    @Test
    fun mismatch_updatesWrongAttemptCount_onSameProblem() = runTest {
        val repo = FakeSessionRepository(today = activeSession(problem = problem(1L, wrongAttemptCount = 0)))
        repo.onSubmit = { mismatchOutcome().copy(currentProblem = problem(1L, wrongAttemptCount = 1)) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("오답")
        viewModel.submit()

        assertEquals(1, viewModel.active().problem.wrongAttemptCount, "서버가 갱신한 오답 수를 즉시 반영한다")

        repo.onSubmit = { mismatchOutcome().copy(currentProblem = problem(1L, wrongAttemptCount = 2)) }
        viewModel.onInputChange("또 오답")
        viewModel.submit()

        assertEquals(2, viewModel.active().problem.wrongAttemptCount, "거듭될수록 누적치가 그대로 갱신된다")
    }

    /**
     * 정답으로 전진([advance])하면 새 칸의 wrongAttemptCount는 서버가 준 값(보통 0, 아직 시도 안 함)을
     * 그대로 따른다 — 방금 통과한 칸의 누적치를 물려받지 않는다.
     */
    @Test
    fun correct_thenAdvance_carriesFreshWrongAttemptCount_forNextProblem() = runTest {
        val next = problem(2L, wrongAttemptCount = 0)
        val repo = FakeSessionRepository(today = activeSession(position = 0, total = 3, problem = problem(1L, wrongAttemptCount = 2)))
        repo.onSubmit = { correctOutcome(next = next, position = 1, solved = 1, total = 3) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("정답")
        viewModel.submit()
        viewModel.advance()

        assertEquals(0, viewModel.active().problem.wrongAttemptCount, "새 칸은 아직 시도한 적이 없다")
    }

    @Test
    fun nearMiss_yieldsInfoFeedback() = runTest {
        val repo = FakeSessionRepository().apply { onSubmit = { nearMissOutcome() } }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("스레두")
        viewModel.submit()

        assertEquals(SessionFeedback.NearMiss, viewModel.active().feedback)
    }

    @Test
    fun correct_showsFeedback_thenAdvance_movesToNextProblem() = runTest {
        val next = problem(2L)
        val repo = FakeSessionRepository(today = activeSession(position = 0, total = 3, problem = problem(1L)))
        repo.onSubmit = { correctOutcome(next = next, position = 1, solved = 1, total = 3) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("스레드")
        viewModel.submit()

        val afterCorrect = viewModel.active()
        assertTrue(afterCorrect.feedback is SessionFeedback.Correct)
        assertEquals(1L, afterCorrect.problem.id, "정답 직후엔 아직 현재 문제를 보여준다")

        viewModel.advance()
        val advanced = viewModel.active()
        assertEquals(2L, advanced.problem.id, "다음 문제로 진행")
        assertEquals(2, advanced.current)
        assertNull(advanced.feedback)
        assertEquals("", advanced.inputText)
    }

    /**
     * [결정 2026-07-16] 마지막 본 문제를 맞혀도 곧장 완료 이벤트를 보내지 않는다 — 이 문제도 다른 문제와
     * 동등하게 피드백(개념·해설·심화)을 보여주고, [finishSession]을 눌러야 완료로 넘어간다. 완료 요약은
     * 미리 [pendingCompletion]에 담아 둔다(전환을 미뤄도 유실 없음).
     */
    @Test
    fun correctOnLastProblem_showsFeedback_withoutEmittingCompletedEvent() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 2, total = 3, solved = 2))
        repo.onSubmit = {
            correctOutcome(next = null, position = 3, solved = 3, total = 3, completed = true, streak = Streak(7, "2026-07-14"))
        }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("정답")
        viewModel.submit()

        val state = viewModel.active()
        assertTrue(state.feedback is SessionFeedback.Correct, "마지막 문제도 다른 문제와 동등하게 피드백을 보여준다")
        assertTrue(state.isLastProblem, "CTA가 [한입 마치기]로 바뀌는 신호")
        assertEquals(3, state.pendingCompletion?.solvedCount)
        assertEquals(7, state.pendingCompletion?.streak?.count)
        assertNull(
            withTimeoutOrNull(100) { viewModel.events.first() },
            "finishSession을 부르기 전에는 완료 이벤트가 나가지 않는다",
        )
    }

    /** [finishSession]을 부르면 그제서야 완료 이벤트가 나간다. 요약은 마지막 제출 응답에서 보존된 값이다. */
    @Test
    fun finishSession_emitsCompletedEvent_usingPendingSummary() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 2, total = 3, solved = 2))
        repo.onSubmit = {
            correctOutcome(next = null, position = 3, solved = 3, total = 3, completed = true, streak = Streak(7, "2026-07-14"))
        }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("정답")
        viewModel.submit()
        viewModel.finishSession()

        val event = viewModel.events.first()
        assertTrue(event is SessionEvent.Completed)
        assertEquals(3, event.summary.solvedCount)
        assertEquals(7, event.summary.streak?.count)
    }

    /** ⭐️ 이중 탭·백 버튼 경계: [finishSession]을 두 번 불러도 완료 이벤트는 한 번만 나간다. */
    @Test
    fun finishSession_calledTwice_emitsEventOnlyOnce() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 2, total = 3, solved = 2))
        repo.onSubmit = { correctOutcome(next = null, position = 3, solved = 3, total = 3, completed = true) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("정답")
        viewModel.submit()
        viewModel.finishSession()
        viewModel.finishSession() // 이중 탭

        assertTrue(viewModel.events.first() is SessionEvent.Completed)
        assertNull(withTimeoutOrNull(100) { viewModel.events.first() }, "완료 이벤트는 정확히 한 번만 나간다")
    }

    /** 마지막 문제가 아니면 기존과 동일하게 CTA는 [다음 문제]로 남는다(완료 대상 아님). */
    @Test
    fun correctOnNonLastProblem_doesNotMarkAsLastProblem() = runTest {
        val next = problem(2L)
        val repo = FakeSessionRepository(today = activeSession(position = 0, total = 3, problem = problem(1L)))
        repo.onSubmit = { correctOutcome(next = next, position = 1, solved = 1, total = 3) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("스레드")
        viewModel.submit()

        val state = viewModel.active()
        assertFalse(state.isLastProblem)
        assertNull(state.pendingCompletion)
    }

    @Test
    fun submitFailure_setsSystemError_notFeedback() = runTest {
        val repo = FakeSessionRepository().apply { submitError = RuntimeException("network") }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("스레드")
        viewModel.submit()

        val state = viewModel.active()
        assertTrue(state.systemError, "전송 실패는 시스템 오류")
        assertNull(state.feedback, "시스템 오류는 오답 피드백이 아니다")
        assertFalse(state.isSubmitting)
        assertEquals("스레드", state.inputText)
    }

    @Test
    fun reveal_setsModelAnswers() = runTest {
        val repo = FakeSessionRepository()
        repo.revealResult = Reveal(listOf("스택"), "LIFO", "스택 (stack)")
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.requestReveal()

        val reveal = viewModel.active().reveal
        assertTrue(reveal != null)
        assertEquals("스택 (stack)", reveal.representativeAnswer)
        assertEquals(1, repo.revealCount)
    }

    @Test
    fun openPast_thenClose() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 2, total = 3, solved = 2, problem = problem(3L)))
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.openPast(1)
        val past = viewModel.active().past
        assertTrue(past is PastView.Loaded)
        assertEquals(1, past.item.position)
        assertEquals(1, repo.lastPastPosition)

        viewModel.closePast()
        assertNull(viewModel.active().past)
    }

    @Test
    fun openPast_outOfRange_isIgnored() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 0, total = 3))
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.openPast(0) // position 0 == current position → 지나온 칸 없음
        assertNull(viewModel.active().past)
        assertNull(repo.lastPastPosition)
    }

    @Test
    fun rapidDoubleSubmit_callsRepositoryOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeSessionRepository()
        repo.onSubmit = { mismatchOutcome() }
        val viewModel = SessionViewModel(repo).apply { loadSession() }
        advanceUntilIdle()

        viewModel.onInputChange("스레드")
        viewModel.submit() // isSubmitting=true 동기 반영 후 예약
        viewModel.submit() // 전송 중이므로 무시
        advanceUntilIdle()

        assertEquals(1, repo.submitCount, "이중 제출은 한 번만 호출된다")
    }

    // ── C2: 정답 공개 게이팅 방향 · 완료 이벤트 1회 · 타입드 예외 처리 ─────────────

    @Test
    fun reveal_offered_beforeAndAfterWrongAttempt() = runTest {
        // [결정 2026-07-17] 시도 전에도 '정답 보기'를 열 수 있다(선행 오답 요구 폐지, 무낙인).
        val repo = FakeSessionRepository().apply { onSubmit = { mismatchOutcome() } }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        assertTrue(viewModel.active().canReveal, "시도 전에도 정답 보기 제안")

        viewModel.onInputChange("틀린답")
        viewModel.submit()

        assertTrue(viewModel.active().canReveal, "오답 뒤에도 여전히 제안")
    }

    @Test
    fun reveal_notOffered_afterCorrect() = runTest {
        val repo = FakeSessionRepository()
        repo.onSubmit = { FakeSessionRepository.correctOutcome(next = problem(2L), position = 1, solved = 1) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("정답")
        viewModel.submit()

        assertFalse(viewModel.active().canReveal, "정답 상태에선 정답 보기 제안 안 함")
    }

    @Test
    fun correctAfterReveal_clearsRevealPanel() = runTest {
        val repo = FakeSessionRepository()
        val viewModel = SessionViewModel(repo).apply { loadSession() }
        viewModel.requestReveal()
        assertTrue(viewModel.active().reveal != null)

        // 공개된 답을 따라 입력해 정답 처리되면, 정답 카드 옆 낡은 공개 패널이 사라진다.
        repo.onSubmit = { FakeSessionRepository.correctOutcome(next = problem(2L), position = 1, solved = 1) }
        viewModel.onInputChange("스택")
        viewModel.submit()

        assertTrue(viewModel.active().feedback is SessionFeedback.Correct)
        assertNull(viewModel.active().reveal, "정답 후 공개 패널은 지워진다")
    }

    @Test
    fun submitOnAlreadyCompleted_resyncsToCompletion_notSystemError() = runTest {
        // Active로 진입한 뒤 서버가 SESSION_ALREADY_COMPLETED를 던지면(경합), 시스템 오류가 아니라 완료로 재동기화한다.
        val repo = FakeSessionRepository(today = activeSession())
        val viewModel = SessionViewModel(repo).apply { loadSession() }
        // 재조회 시 완료로 보이도록 서버 상태를 바꾸고, 제출은 완료 예외를 던지게 한다.
        repo.today = completedSession(total = 3)
        repo.submitError = SessionCompletedException()

        viewModel.onInputChange("정답")
        viewModel.submit()

        assertTrue(viewModel.events.first() is SessionEvent.Completed, "완료로 재동기화")
    }

    // ── 정답 후 재제출 · 서버 위치 재동기화 ─────────────────────────────────

    /**
     * ⭐️ 정답 후 재제출은 서버에 닿지 않는다.
     *
     * 세션 제출은 **위치 기반**이다(`submitAttempt(answer)` — 문제 id가 없다). 정답에 서버 커서가 이미
     * 다음 칸으로 전진했으므로, 낡은 입력이 한 번 더 나가면 **사용자가 본 적 없는 다음 문제**가 그 입력으로
     * 채점된다. 진행은 [SessionViewModel.advance]만 한다.
     */
    @Test
    fun correct_thenResubmit_neverReachesServer() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 0, total = 3, problem = problem(1L)))
        repo.onSubmit = { correctOutcome(next = problem(2L), position = 1, solved = 1) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("스레드")
        viewModel.submit()
        assertTrue(viewModel.active().solved, "선행 조건: 정답 상태")

        viewModel.submit() // 엔터 한 번 더

        assertEquals(1, repo.submitCount, "정답 후 재제출은 서버에 닿지 않는다")
        assertEquals(listOf("스레드"), repo.submitted, "낡은 입력이 다음 칸으로 채점되면 안 된다")
    }

    /** 정답 후에도 화면 상태는 그대로다 — 재제출이 막혔다고 해서 피드백·다음 칸이 흐트러지지 않는다. */
    @Test
    fun correct_thenResubmit_leavesStateIntact() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 0, total = 3, problem = problem(1L)))
        repo.onSubmit = { correctOutcome(next = problem(2L), position = 1, solved = 1) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("스레드")
        viewModel.submit()
        viewModel.submit()

        val state = viewModel.active()
        assertTrue(state.feedback is SessionFeedback.Correct, "정답 피드백이 유지된다")
        assertEquals(1L, state.problem.id, "아직 현재 문제를 보여준다")
        assertFalse(state.systemError, "차단은 오류가 아니다")
        assertFalse(state.isSubmitting, "전송 중 표시가 눌러붙지 않는다")
    }

    /**
     * ⭐️ 정답을 맞혀도 입력칸을 비우지 않는다 — 사용자가 방금 쓴 답이 개념·해설 옆에 남아 있어야
     * "내가 쓴 이것이 맞았다"가 성립한다(학습). 재제출 위험은 [correct_thenResubmit_neverReachesServer]의
     * 가드가 막으므로, 사용자가 쓴 것을 지워서 막을 이유가 없다.
     */
    @Test
    fun correct_keepsUserAnswerVisible() = runTest {
        val repo = FakeSessionRepository()
        repo.onSubmit = { correctOutcome(next = problem(2L), position = 1, solved = 1) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("스레드")
        viewModel.submit()

        assertEquals("스레드", viewModel.active().inputText, "정답 후에도 내가 쓴 답이 보인다")
    }

    /**
     * ⭐️ 서버가 준 칸이 클라가 그리던 칸과 다르면 서버로 되맞춘다(서버가 진실).
     *
     * 서버는 정답에만 커서를 전진시키므로 정상 흐름에선 도달하지 않는 방어선이다. 응답의 position·
     * currentProblem을 버리면, 어긋난 순간 클라가 **낡은 문제를 계속 그리고** 다음 문제는 화면에
     * 한 번도 뜨지 못한 채 소비된다 — 표시 문제가 아니라 학습 손실이다.
     */
    @Test
    fun mismatch_resyncsToServerProblem_whenClientIsStale() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 0, total = 3, problem = problem(1L)))
        // 서버는 이미 2번 칸에 있다 — 불일치 응답이 그 사실을 실어 온다.
        repo.onSubmit = { mismatchOutcome().copy(position = 1, currentProblem = problem(2L), solvedCount = 1) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("아무거나")
        viewModel.submit()

        val state = viewModel.active()
        assertEquals(2L, state.problem.id, "서버가 진실 — 낡은 문제를 계속 그리지 않는다")
        assertEquals(2, state.current, "position도 함께 되맞춘다")
        assertEquals(1, state.solvedCount)
    }

    /**
     * 되맞춘 칸에는 오답 넛지를 얹지 않는다 — 사용자가 본 적도 없는 문제에 '아직이에요'를 붙이면
     * 없던 오답을 뒤집어씌우는 셈이다(무낙인). 입력도 새 칸에 맞게 비운다.
     */
    @Test
    fun resyncedProblem_carriesNoWrongAnswerNudge() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 0, total = 3, problem = problem(1L)))
        repo.onSubmit = { nearMissOutcome().copy(position = 1, currentProblem = problem(2L)) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("아무거나")
        viewModel.submit()

        val state = viewModel.active()
        assertNull(state.feedback, "본 적 없는 문제에 오답 피드백을 얹지 않는다")
        assertEquals("", state.inputText, "새 칸에는 빈 입력으로 들어간다")
    }

    /**
     * 서버가 비정답에 칸을 실어 주지 않으면(계약상 없는 응답) 되맞출 근거가 없다 — 지금 칸을 그대로 두고
     * 피드백만 얹는다. 없는 정보를 '진실'로 삼아 화면을 흔들지 않는다.
     */
    @Test
    fun mismatch_withoutServerProblem_keepsCurrentProblem() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 0, total = 3, problem = problem(1L)))
        repo.onSubmit = { mismatchOutcome().copy(currentProblem = null) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("아무거나")
        viewModel.submit()

        val state = viewModel.active()
        assertEquals(1L, state.problem.id)
        assertEquals(SessionFeedback.Mismatch(), state.feedback)
        assertEquals("아무거나", state.inputText)
    }

    /**
     * ⭐️ [실기기 QA] 입력을 고쳐도 오개념 교정 힌트(stickyMisconceptionHint)는 유지된다 — 힌트를 읽으면서
     * 답을 고칠 수 있어야 한다(예전엔 한 글자만 바꿔도 힌트가 사라졌다). 반면 판정 피드백(feedback)은 편집
     * 시 지워진다 — 그래야 다음 제출에서 같은 유형 오답이어도 주황 플래시·라이브 리전 낭독이 다시 발화한다.
     */
    @Test
    fun editingInput_keepsStickyHint_butClearsVerdictFeedback() = runTest {
        val hint = "그건 프로세스예요, 실행 흐름의 단위를 다시 떠올려 봐요"
        val repo = FakeSessionRepository().apply {
            onSubmit = { mismatchOutcome(misconceptionHint = hint) }
        }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("프로세스")
        viewModel.submit()
        assertEquals(SessionFeedback.Mismatch(hint), viewModel.active().feedback)
        assertEquals(hint, viewModel.active().stickyMisconceptionHint, "제출 시 교정 힌트가 sticky로 실린다")

        viewModel.onInputChange("스레드")
        assertNull(viewModel.active().feedback, "입력을 고치면 판정 피드백은 지워진다(플래시·낭독 재발화용)")
        assertEquals(hint, viewModel.active().stickyMisconceptionHint, "입력을 고쳐도 교정 힌트는 유지된다")
        assertEquals("스레드", viewModel.active().inputText, "입력은 고친 값 그대로")
    }

    /** 정답을 맞히면 직전 오답의 교정 힌트(sticky)는 비워진다 — 더 볼 이유가 없다. */
    @Test
    fun correct_clearsStickyHint() = runTest {
        val next = problem(2L)
        val repo = FakeSessionRepository(today = activeSession(position = 0, total = 3, problem = problem(1L)))
        repo.onSubmit = { mismatchOutcome(misconceptionHint = "힌트") }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("오답")
        viewModel.submit()
        assertEquals("힌트", viewModel.active().stickyMisconceptionHint)

        repo.onSubmit = { correctOutcome(next = next, position = 1, solved = 1, total = 3) }
        viewModel.onInputChange("정답")
        viewModel.submit()
        assertNull(viewModel.active().stickyMisconceptionHint, "정답이면 교정 힌트를 비운다")
    }

    /** 입력을 고치면 직전 제출 실패(systemError) 표시는 내려간다 — 재입력은 새 시도이므로. */
    @Test
    fun editingInput_clearsSystemError() = runTest {
        val repo = FakeSessionRepository().apply { submitError = RuntimeException("network") }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("아무거나")
        viewModel.submit()
        assertTrue(viewModel.active().systemError, "전송 실패는 systemError로 표시된다")

        viewModel.onInputChange("다시 입력")
        assertFalse(viewModel.active().systemError, "재입력하면 전송 실패 표시는 내려간다")
    }

    // ── 힌트(pull) · 오답 교정 힌트(push) ────────────────────────────────────

    /**
     * ⭐️ 힌트 열기는 서버 왕복이고, 공개 목록은 **서버가 원천**이다 — 로컬 카운터를 올리는 게 아니라
     * 서버 응답의 목록으로 갈아끼운다. 서버가 준 딱 그 목록이어야 이 단언이 통과한다(로컬 ++로 바꾸면 깨진다).
     */
    @Test
    fun revealHint_success_replacesWithServerList() = runTest {
        val serverList = listOf(SessionHint("서버가 준 첫 힌트"))
        val repo = FakeSessionRepository(today = activeSession(problem = problem(1L, hintCount = 2)))
        repo.onRevealHint = { HintReveal(hintCount = 2, revealedHints = serverList) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        assertEquals(0, viewModel.active().revealedHintCount, "진입 전에는 공개 0")
        viewModel.revealNextHint()

        val state = viewModel.active()
        assertEquals(serverList, state.revealedHints, "서버가 준 목록이 원천")
        assertEquals(1, state.revealedHintCount)
        assertEquals(0, repo.lastRevealHintCount, "클라가 아는 현재 공개 수(0)를 서버에 보낸다")
        assertEquals(1, repo.revealHintCount)
    }

    /** ⭐️ 재진입 복원: 서버가 이미 공개된 힌트를 실어 주면 로드 즉시 그 상태로 복원된다. */
    @Test
    fun load_restoresRevealedHints_forReentry() = runTest {
        val already = listOf(SessionHint("이미 본 힌트"))
        val repo = FakeSessionRepository(
            today = activeSession(problem = problem(1L, hintCount = 2, revealedHints = already)),
        )
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        val state = viewModel.active()
        assertEquals(already, state.revealedHints, "서버가 준 공개 힌트로 복원")
        assertEquals(1, state.revealedHintCount)
        assertTrue(state.hasMoreHints, "2개 중 1개만 열렸으므로 더 열 수 있다")
    }

    /**
     * ⭐️ 힌트 열람 실패(네트워크 등)는 세션 진행을 막지 않는다 — 시스템 오류로 취급하지 않고 진행 표시만 내린다.
     * (submit 실패가 systemError를 세우는 것과 대비: 힌트는 보조 장치라 실패해도 무낙인·비차단.)
     */
    @Test
    fun revealHint_failure_doesNotBlock_norSystemError() = runTest {
        val repo = FakeSessionRepository(today = activeSession(problem = problem(1L, hintCount = 2)))
        repo.revealHintError = RuntimeException("network")
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.revealNextHint()

        val state = viewModel.active()
        assertEquals(0, state.revealedHintCount, "실패했으므로 공개는 늘지 않는다")
        assertFalse(state.isRevealingHint, "진행 표시는 내려간다")
        assertFalse(state.systemError, "힌트 열람 실패는 시스템 오류가 아니다")
    }

    /** 더 열 힌트가 없으면(전부 공개·힌트 0개) 서버에 닿지 않는다 — 헛된 왕복·상태 흔들림 방지. */
    @Test
    fun revealHint_whenNoMoreHints_doesNotReachServer() = runTest {
        val repo = FakeSessionRepository(today = activeSession(problem = problem(1L, hintCount = 0)))
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.revealNextHint()

        assertEquals(0, repo.revealHintCount, "힌트가 없으면 열기 요청도 없다")
    }

    /** 오답 교정 힌트(push)는 불일치 피드백에 실려 온다 — 매칭된 오답에만 채워진다. */
    @Test
    fun mismatch_carriesMisconceptionHint_whenServerProvides() = runTest {
        val repo = FakeSessionRepository()
        repo.onSubmit = { mismatchOutcome(misconceptionHint = "그건 프로세스예요, 실행 흐름의 단위를 다시 떠올려 봐요") }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("프로세스")
        viewModel.submit()

        val feedback = viewModel.active().feedback
        assertTrue(feedback is SessionFeedback.Mismatch)
        assertEquals("그건 프로세스예요, 실행 흐름의 단위를 다시 떠올려 봐요", feedback.misconceptionHint)
    }

    /** 큐레이션 안 된 오답은 교정 힌트 없이 일반 불일치로 흐른다(막다른 길 없음). */
    @Test
    fun mismatch_withoutMisconception_isPlainMismatch() = runTest {
        val repo = FakeSessionRepository().apply { onSubmit = { mismatchOutcome() } }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("아무 오답")
        viewModel.submit()

        val feedback = viewModel.active().feedback
        assertTrue(feedback is SessionFeedback.Mismatch)
        assertNull(feedback.misconceptionHint, "매칭 안 되면 교정 힌트는 없다")
    }
}
