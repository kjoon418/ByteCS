package watson.bytecs.problem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ProblemViewModel 단위 테스트. FakeProblemRepository의 결정적 판정으로 세 피드백 상태와
 * 불일치 후 입력 유지(무낙인)를 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProblemViewModelTest {

    // viewModelScope는 Main 디스패처를 쓰므로 테스트 디스패처로 교체한다.
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = ProblemViewModel(FakeProblemRepository())

    private fun ProblemViewModel.ready(): ProblemUiState.Ready =
        uiState.value as ProblemUiState.Ready

    @Test
    fun initialState_isLoading_beforeFirstProblemArrives() = runTest {
        // 지연 실행 디스패처로 교체해, init의 loadProblem이 아직 실행되지 않은 최초 상태를 관찰한다.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = ProblemViewModel(FakeProblemRepository())

        assertTrue(viewModel.uiState.value is ProblemUiState.Loading, "최초 상태는 Loading 이어야 한다")

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ProblemUiState.Ready, "문제 로드 후 Ready 로 전이한다")
    }

    @Test
    fun exactAnswer_yieldsCorrect_withConceptAndExplanation() = runTest {
        val viewModel = newViewModel()

        viewModel.onInputChange("스레드")
        viewModel.submit()

        val feedback = viewModel.ready().feedback
        assertTrue(feedback is Feedback.Correct, "정확 일치는 Correct 여야 한다")
        assertEquals("프로세스와 스레드", feedback.concept)
        // 정답 시에는 해설도 함께 노출된다.
        assertNotNull(feedback.explanation, "정답 피드백은 해설을 노출해야 한다")
        assertEquals(
            "스레드는 프로세스의 코드·데이터·힙을 공유하되, 스택과 레지스터는 각자 가진다.",
            feedback.explanation,
        )
    }

    @Test
    fun blankSubmit_isNoOp() = runTest {
        val viewModel = newViewModel()

        viewModel.onInputChange("   ") // 공백만
        viewModel.submit()

        assertNull(viewModel.ready().feedback, "공백 제출은 아무 피드백도 만들지 않는다")
    }

    @Test
    fun typoAnswer_yieldsNearMiss_withoutConcept() = runTest {
        val viewModel = newViewModel()

        viewModel.onInputChange("스레두") // 편집거리 1
        viewModel.submit()

        val feedback = viewModel.ready().feedback
        assertTrue(feedback is Feedback.NearMiss, "오탈자 수준은 NearMiss 여야 한다")
        // NearMiss는 개념을 노출하지 않는다(별도 상태이므로 concept 필드 자체가 없다).
    }

    @Test
    fun wrongAnswer_yieldsMismatch_withoutConcept() = runTest {
        val viewModel = newViewModel()

        viewModel.onInputChange("프로세스")
        viewModel.submit()

        assertTrue(viewModel.ready().feedback is Feedback.Mismatch, "불일치는 Mismatch 여야 한다")
    }

    @Test
    fun mismatch_retainsInputForRetry() = runTest {
        val viewModel = newViewModel()

        viewModel.onInputChange("프로세스")
        viewModel.submit()

        val state = viewModel.ready()
        assertTrue(state.feedback is Feedback.Mismatch)
        assertEquals("프로세스", state.inputText, "불일치 후에도 입력은 유지되어야 한다")
    }

    @Test
    fun editingInput_clearsPriorFeedback() = runTest {
        val viewModel = newViewModel()

        viewModel.onInputChange("프로세스")
        viewModel.submit()
        assertTrue(viewModel.ready().feedback is Feedback.Mismatch)

        // 답을 고치면 직전 피드백이 지워져 다음 제출이 깨끗한 상태에서 시작한다.
        viewModel.onInputChange("스레드")
        assertNull(viewModel.ready().feedback)
    }

    // ── M6: 다음 문제 진행 ────────────────────────────────────────────────────

    @Test
    fun loadNext_advancesProgress_andResetsInputAndFeedback() = runTest {
        val viewModel = newViewModel() // current=1, total=5

        viewModel.onInputChange("스레드")
        viewModel.submit()
        assertTrue(viewModel.ready().feedback is Feedback.Correct)
        assertEquals(1, viewModel.ready().current)

        viewModel.loadNext()

        val state = viewModel.ready()
        assertEquals(2, state.current, "다음 문제로 진행도가 증가해야 한다")
        assertEquals("", state.inputText, "새 문제에서 입력은 초기화된다")
        assertNull(state.feedback, "새 문제에서 피드백은 초기화된다")
    }

    @Test
    fun loadNext_atLastProblem_staysAtTotal_withoutCrash() = runTest {
        val viewModel = ProblemViewModel(FakeProblemRepository(), totalProblems = 5, currentIndex = 5)

        viewModel.loadNext()

        assertEquals(5, viewModel.ready().current, "마지막 문제에서는 진행도를 total로 유지한다")
    }

    // ── no-leak: 불일치·근접은 개념·해설을 절대 흘리지 않는다(무낙인·정답 비노출) ──────────

    @Test
    fun nearMissResult_leaksNoConceptOrExplanation() = runTest {
        val result = FakeProblemRepository().submitAttempt(problemId = 1L, answer = "스레두")

        assertEquals(JudgeResult.NEAR_MISS, result.result)
        assertNull(result.concept, "근접은 개념을 노출하지 않아야 한다")
        assertNull(result.explanation, "근접은 해설을 노출하지 않아야 한다")
    }

    @Test
    fun mismatchResult_leaksNoConceptOrExplanation() = runTest {
        val result = FakeProblemRepository().submitAttempt(problemId = 1L, answer = "프로세스")

        assertEquals(JudgeResult.MISMATCH, result.result)
        assertNull(result.concept, "불일치는 개념을 노출하지 않아야 한다")
        assertNull(result.explanation, "불일치는 해설을 노출하지 않아야 한다")
    }
}
