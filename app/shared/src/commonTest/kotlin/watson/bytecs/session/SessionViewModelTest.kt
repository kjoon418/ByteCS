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
        assertEquals(SessionFeedback.Mismatch, state.feedback)
        assertEquals("프로세스", state.inputText, "불일치 후에도 입력 유지")
        assertFalse(state.solved)
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

    @Test
    fun completingAttempt_emitsCompletedEvent_withStreak() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 2, total = 3, solved = 2))
        repo.onSubmit = {
            correctOutcome(next = null, position = 3, solved = 3, total = 3, completed = true, streak = Streak(7, "2026-07-14"))
        }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("정답")
        viewModel.submit()

        val event = viewModel.events.first()
        assertTrue(event is SessionEvent.Completed)
        assertEquals(3, event.summary.solvedCount)
        assertEquals(7, event.summary.streak?.count)
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
        repo.revealResult = Reveal("스택", "LIFO", listOf("스택", "stack"))
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.requestReveal()

        val reveal = viewModel.active().reveal
        assertTrue(reveal != null)
        assertEquals(listOf("스택", "stack"), reveal.acceptableAnswers)
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
    fun reveal_notOffered_beforeWrongAttempt_offeredAfter() = runTest {
        // no-leak 안전판: 오답을 내기 전에는 '정답 보기'를 제안하지 않는다.
        val repo = FakeSessionRepository().apply { onSubmit = { mismatchOutcome() } }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        assertFalse(viewModel.active().canReveal, "시도 전에는 정답 보기 제안 안 함")

        viewModel.onInputChange("틀린답")
        viewModel.submit()

        assertTrue(viewModel.active().canReveal, "오답(불일치·근접) 뒤에만 정답 보기 제안")
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
    fun completion_emitsEventExactlyOnce() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 2, total = 3, solved = 2))
        repo.onSubmit = { correctOutcome(next = null, position = 3, solved = 3, total = 3, completed = true) }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("정답")
        viewModel.submit()

        assertTrue(viewModel.events.first() is SessionEvent.Completed)
        // ⭐️ 두 번째 완료 이벤트는 없다(중복 로드/축하 방지 회귀 가드).
        assertNull(withTimeoutOrNull(100) { viewModel.events.first() }, "완료 이벤트는 한 번만 발사된다")
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

    @Test
    fun reveal_notAllowed_isHandledSoftly_notSystemError() = runTest {
        val repo = FakeSessionRepository().apply { revealError = RevealNotAllowedException() }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.requestReveal()

        val state = viewModel.active()
        assertNull(state.reveal)
        assertFalse(state.isRevealing, "진행 표시는 내려간다")
        assertFalse(state.systemError, "공개 불가는 시스템 오류로 취급하지 않는다(비처벌)")
    }

    @Test
    fun editingInput_clearsPriorFeedback() = runTest {
        val repo = FakeSessionRepository().apply { onSubmit = { mismatchOutcome() } }
        val viewModel = SessionViewModel(repo).apply { loadSession() }

        viewModel.onInputChange("프로세스")
        viewModel.submit()
        assertEquals(SessionFeedback.Mismatch, viewModel.active().feedback)

        viewModel.onInputChange("스레드")
        assertNull(viewModel.active().feedback, "입력을 고치면 직전 피드백이 지워진다")
    }
}
