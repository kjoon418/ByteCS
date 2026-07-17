package watson.bytecs.extrastudy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.bytecs.extrastudy.FakeExtraStudyRepository.Companion.available
import watson.bytecs.extrastudy.FakeExtraStudyRepository.Companion.correctAttempt
import watson.bytecs.extrastudy.FakeExtraStudyRepository.Companion.mismatchAttempt
import watson.bytecs.extrastudy.FakeExtraStudyRepository.Companion.nearMissAttempt
import watson.bytecs.extrastudy.FakeExtraStudyRepository.Companion.problem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ExtraStudyViewModel 검증. 현재 로드·무낙인 피드백·정답 공개·정답 후 advance 재조회·소진 전이·
 * 이중 제출 가드·열린 항목 경합 재동기화.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExtraStudyViewModelTest {

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun ExtraStudyViewModel.active(): ExtraStudyUiState.Active =
        uiState.value as ExtraStudyUiState.Active

    @Test
    fun load_available_showsCurrentProblem() = runTest {
        val repo = FakeExtraStudyRepository(current = available(problem(2L)))
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        assertEquals(2L, viewModel.active().problem.id)
    }

    @Test
    fun load_exhausted_yieldsExhausted() = runTest {
        val repo = FakeExtraStudyRepository(current = ExtraStudyState.Exhausted)
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        assertTrue(viewModel.uiState.value is ExtraStudyUiState.Exhausted)
    }

    @Test
    fun loadFailure_yieldsError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeExtraStudyRepository().apply { getCurrentError = RuntimeException("down") }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ExtraStudyUiState.Error)
    }

    @Test
    fun mismatch_yieldsNeutralFeedback_andRetainsInput() = runTest {
        val repo = FakeExtraStudyRepository().apply { onSubmit = { mismatchAttempt() } }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        viewModel.onInputChange("프로세스")
        viewModel.submit()

        val state = viewModel.active()
        assertEquals(ExtraStudyFeedback.Mismatch(), state.feedback)
        assertEquals("프로세스", state.inputText, "불일치 후에도 입력 유지")
        assertFalse(state.solved)
    }

    @Test
    fun nearMiss_yieldsInfoFeedback() = runTest {
        val repo = FakeExtraStudyRepository().apply { onSubmit = { nearMissAttempt() } }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        viewModel.onInputChange("스레두")
        viewModel.submit()

        assertEquals(ExtraStudyFeedback.NearMiss, viewModel.active().feedback)
    }

    @Test
    fun mismatch_carriesMisconceptionHint_whenServerProvides() = runTest {
        val repo = FakeExtraStudyRepository().apply {
            onSubmit = { mismatchAttempt(misconceptionHint = "실행 흐름의 단위를 다시 떠올려 봐요") }
        }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        viewModel.onInputChange("프로세스")
        viewModel.submit()

        val feedback = viewModel.active().feedback
        assertTrue(feedback is ExtraStudyFeedback.Mismatch)
        assertEquals("실행 흐름의 단위를 다시 떠올려 봐요", feedback.misconceptionHint)
    }

    @Test
    fun correct_showsFeedback_thenAdvance_loadsNextProblem() = runTest {
        val repo = FakeExtraStudyRepository(current = available(problem(1L)))
        repo.onSubmit = { correctAttempt() }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        viewModel.onInputChange("스레드")
        viewModel.submit()

        val afterCorrect = viewModel.active()
        assertTrue(afterCorrect.feedback is ExtraStudyFeedback.Correct)
        assertEquals(1L, afterCorrect.problem.id, "정답 직후엔 아직 현재 문제를 보여준다")

        // advance는 getCurrent를 다시 불러 다음 문제를 받는다(서버가 다음 열린 항목으로 갈아끼운다).
        repo.current = available(problem(2L))
        viewModel.advance()

        val advanced = viewModel.active()
        assertEquals(2L, advanced.problem.id, "advance는 getCurrent로 다음 문제를 받는다")
        assertNull(advanced.feedback)
        assertEquals("", advanced.inputText)
    }

    @Test
    fun correct_thenAdvance_whenNoneLeft_transitionsToExhausted() = runTest {
        val repo = FakeExtraStudyRepository(current = available(problem(1L)))
        repo.onSubmit = { correctAttempt() }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        viewModel.onInputChange("정답")
        viewModel.submit()
        assertTrue(viewModel.active().solved)

        // 이 문제가 마지막 — 정답 후 서버는 소진을 돌려준다.
        repo.current = ExtraStudyState.Exhausted
        viewModel.advance()

        assertTrue(viewModel.uiState.value is ExtraStudyUiState.Exhausted, "정답 후 소진을 받으면 소진으로 전이")
    }

    @Test
    fun submitFailure_setsSystemError_notFeedback() = runTest {
        val repo = FakeExtraStudyRepository().apply { submitError = RuntimeException("network") }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        viewModel.onInputChange("스레드")
        viewModel.submit()

        val state = viewModel.active()
        assertTrue(state.systemError, "전송 실패는 시스템 오류")
        assertNull(state.feedback, "시스템 오류는 오답 피드백이 아니다")
        assertFalse(state.isSubmitting)
        assertEquals("스레드", state.inputText)
    }

    @Test
    fun submit_noOpenItem_resyncsViaLoad_notSystemError() = runTest {
        // 열린 항목이 사라진 경합(다른 기기 등) → 시스템 오류가 아니라 getCurrent 재동기화.
        val repo = FakeExtraStudyRepository(current = available(problem(1L)))
        repo.submitError = ExtraStudyNoOpenItemException()
        val viewModel = ExtraStudyViewModel(repo).apply { load() }
        // 재조회 시 서버가 새 열린 항목을 준다.
        repo.current = available(problem(2L))

        viewModel.onInputChange("정답")
        viewModel.submit()

        val state = viewModel.active()
        assertEquals(2L, state.problem.id, "경합은 load로 재동기화된다")
        assertFalse(state.systemError, "경합은 시스템 오류가 아니다")
    }

    @Test
    fun reveal_setsModelAnswer() = runTest {
        val repo = FakeExtraStudyRepository()
        repo.revealResult = ExtraStudyReveal(listOf("스택"), "LIFO", "스택 (stack)")
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        viewModel.requestReveal()

        val reveal = viewModel.active().reveal
        assertTrue(reveal != null)
        assertEquals("스택 (stack)", reveal.representativeAnswer)
        assertEquals(1, repo.revealCount)
    }

    @Test
    fun reveal_offered_beforeAndAfterWrongAttempt() = runTest {
        // [결정 2026-07-17] 시도 전에도 '정답 보기'를 열 수 있다(선행 오답 요구 폐지, 무낙인).
        val repo = FakeExtraStudyRepository().apply { onSubmit = { mismatchAttempt() } }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        assertTrue(viewModel.active().canReveal, "시도 전에도 정답 보기 제안")

        viewModel.onInputChange("틀린답")
        viewModel.submit()

        assertTrue(viewModel.active().canReveal, "오답 뒤에도 여전히 제안")
    }

    @Test
    fun reveal_notOffered_afterCorrect() = runTest {
        val repo = FakeExtraStudyRepository()
        repo.onSubmit = { correctAttempt() }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        viewModel.onInputChange("정답")
        viewModel.submit()

        assertFalse(viewModel.active().canReveal, "정답 상태에선 정답 보기 제안 안 함")
    }

    @Test
    fun correctAfterReveal_clearsRevealPanel() = runTest {
        val repo = FakeExtraStudyRepository()
        val viewModel = ExtraStudyViewModel(repo).apply { load() }
        viewModel.requestReveal()
        assertTrue(viewModel.active().reveal != null)

        // 공개된 답을 따라 입력해 정답 처리되면, 정답 카드 옆 낡은 공개 패널이 사라진다.
        repo.onSubmit = { correctAttempt() }
        viewModel.onInputChange("스택")
        viewModel.submit()

        assertTrue(viewModel.active().feedback is ExtraStudyFeedback.Correct)
        assertNull(viewModel.active().reveal, "정답 후 공개 패널은 지워진다")
    }

    @Test
    fun rapidDoubleSubmit_callsRepositoryOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeExtraStudyRepository()
        repo.onSubmit = { mismatchAttempt() }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }
        advanceUntilIdle()

        viewModel.onInputChange("스레드")
        viewModel.submit()
        viewModel.submit() // 전송 중이므로 무시
        advanceUntilIdle()

        assertEquals(1, repo.submitCount, "이중 제출은 한 번만 호출된다")
    }

    @Test
    fun correct_thenResubmit_neverReachesServer() = runTest {
        val repo = FakeExtraStudyRepository(current = available(problem(1L)))
        repo.onSubmit = { correctAttempt() }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        viewModel.onInputChange("스레드")
        viewModel.submit()
        assertTrue(viewModel.active().solved, "선행 조건: 정답 상태")

        viewModel.submit() // 엔터 한 번 더

        assertEquals(1, repo.submitCount, "정답 후 재제출은 서버에 닿지 않는다")
    }

    @Test
    fun correct_keepsUserAnswerVisible() = runTest {
        val repo = FakeExtraStudyRepository()
        repo.onSubmit = { correctAttempt() }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        viewModel.onInputChange("스레드")
        viewModel.submit()

        assertEquals("스레드", viewModel.active().inputText, "정답 후에도 내가 쓴 답이 보인다")
    }

    @Test
    fun editingInput_clearsPriorFeedback() = runTest {
        val repo = FakeExtraStudyRepository().apply { onSubmit = { mismatchAttempt() } }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        viewModel.onInputChange("프로세스")
        viewModel.submit()
        assertEquals(ExtraStudyFeedback.Mismatch(), viewModel.active().feedback)

        viewModel.onInputChange("스레드")
        assertNull(viewModel.active().feedback, "입력을 고치면 직전 피드백이 지워진다")
    }

    @Test
    fun revealHint_success_replacesWithServerList() = runTest {
        val serverList = listOf(ExtraStudyHint("서버가 준 첫 힌트"))
        val repo = FakeExtraStudyRepository(current = available(problem(1L, hintCount = 2)))
        repo.onRevealHint = { ExtraStudyHintReveal(hintCount = 2, revealedHints = serverList) }
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        assertEquals(0, viewModel.active().revealedHintCount, "진입 전에는 공개 0")
        viewModel.revealNextHint()

        val state = viewModel.active()
        assertEquals(serverList, state.revealedHints, "서버가 준 목록이 원천")
        assertEquals(1, state.revealedHintCount)
        assertEquals(0, repo.lastRevealHintCount, "클라가 아는 현재 공개 수(0)를 서버에 보낸다")
        assertEquals(1, repo.revealHintCount)
    }

    @Test
    fun load_restoresRevealedHints_forReentry() = runTest {
        val already = listOf(ExtraStudyHint("이미 본 힌트"))
        val repo = FakeExtraStudyRepository(
            current = available(problem(1L, hintCount = 2, revealedHints = already)),
        )
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        val state = viewModel.active()
        assertEquals(already, state.revealedHints, "서버가 준 공개 힌트로 복원")
        assertEquals(1, state.revealedHintCount)
        assertTrue(state.hasMoreHints, "2개 중 1개만 열렸으므로 더 열 수 있다")
    }

    @Test
    fun revealHint_failure_doesNotBlock_norSystemError() = runTest {
        val repo = FakeExtraStudyRepository(current = available(problem(1L, hintCount = 2)))
        repo.revealHintError = RuntimeException("network")
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        viewModel.revealNextHint()

        val state = viewModel.active()
        assertEquals(0, state.revealedHintCount, "실패했으므로 공개는 늘지 않는다")
        assertFalse(state.isRevealingHint, "진행 표시는 내려간다")
        assertFalse(state.systemError, "힌트 열람 실패는 시스템 오류가 아니다")
    }

    @Test
    fun revealHint_whenNoMoreHints_doesNotReachServer() = runTest {
        val repo = FakeExtraStudyRepository(current = available(problem(1L, hintCount = 0)))
        val viewModel = ExtraStudyViewModel(repo).apply { load() }

        viewModel.revealNextHint()

        assertEquals(0, repo.revealHintCount, "힌트가 없으면 열기 요청도 없다")
    }
}
