package watson.bytecs.problem

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 실제 네트워크 연동 시의 실패·경합을 다루는 뷰모델 동작 검증.
 * 시스템 오류를 사용자 오답과 구분하고(§5.12), 이중 제출을 막는다(L1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProblemViewModelNetworkTest {

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private class FailingRepository : ProblemRepository {
        override suspend fun getNext(): ProblemView = throw RuntimeException("network down")
        override suspend fun submitAttempt(problemId: Long, answer: String): AttemptResult =
            throw RuntimeException("network down")
    }

    private class SubmitFailingRepository : ProblemRepository {
        override suspend fun getNext(): ProblemView = ProblemView(id = 1L, question = "Q")
        override suspend fun submitAttempt(problemId: Long, answer: String): AttemptResult =
            throw RuntimeException("network down")
    }

    /** submitAttempt를 게이트로 붙잡아, 전송 중 재진입을 재현한다. */
    private class GatedRepository : ProblemRepository {
        val gate = CompletableDeferred<Unit>()
        var submitCount = 0
        override suspend fun getNext(): ProblemView = ProblemView(id = 1L, question = "Q")
        override suspend fun submitAttempt(problemId: Long, answer: String): AttemptResult {
            submitCount++
            gate.await()
            return AttemptResult(JudgeResult.MISMATCH)
        }
    }

    @Test
    fun loadFailure_yieldsErrorState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = ProblemViewModel(FailingRepository())

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ProblemUiState.Error, "로드 실패는 Error 상태여야 한다")
    }

    @Test
    fun submitFailure_keepsReady_andFlagsSubmitFailed_notAsWrongAnswer() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = ProblemViewModel(SubmitFailingRepository())

        viewModel.onInputChange("스레드")
        viewModel.submit()

        val state = viewModel.uiState.value as ProblemUiState.Ready
        assertTrue(state.submitFailed, "전송 실패 플래그가 서야 한다")
        assertFalse(state.isSubmitting, "실패 후 전송 중 플래그는 내려가야 한다")
        assertNull(state.feedback, "시스템 오류는 오답 피드백으로 취급하지 않는다")
        assertEquals("스레드", state.inputText, "입력은 유지된다")
    }

    @Test
    fun rapidDoubleSubmit_isGuarded_onlyOneInFlight() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = GatedRepository()
        val viewModel = ProblemViewModel(repository)
        advanceUntilIdle() // 최초 문제 로드 완료

        viewModel.onInputChange("스레드")
        viewModel.submit() // 전송 시작 → isSubmitting=true(동기 반영)
        viewModel.submit() // 전송 중이므로 무시되어야 함
        advanceUntilIdle() // 첫 전송 코루틴이 게이트까지 진행

        assertEquals(1, repository.submitCount, "전송 중 재제출은 무시되어 한 번만 호출된다")
        assertTrue((viewModel.uiState.value as ProblemUiState.Ready).isSubmitting)

        repository.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse((viewModel.uiState.value as ProblemUiState.Ready).isSubmitting, "완료 후 플래그 해제")
    }
}
