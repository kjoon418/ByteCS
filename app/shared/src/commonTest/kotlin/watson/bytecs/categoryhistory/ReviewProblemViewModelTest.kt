package watson.bytecs.categoryhistory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import watson.bytecs.problem.JudgeResult

/**
 * '그때 푼 문제 다시 보기'(DI10) 뷰모델 단위 테스트. 단건 재열람에 성공하면 Ready, 실패(네트워크·404 등)면
 * 막다른 길 대신 Error로 떨어지는지 검증한다(재시도 가능).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewProblemViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun item(problemId: Long) = CategoryHistoryItem(
        problemId = problemId,
        question = "프로세스란?",
        codeSnippet = null,
        difficulty = "MEDIUM",
        result = JudgeResult.CORRECT,
        concepts = listOf("프로세스"),
        explanation = "해설",
        representativeAnswer = "프로세스 (process)",
    )

    @Test
    fun 단건_재열람에_성공하면_Ready로_노출한다() = runTest {
        val repository = FakeCategoryHistoryRepository(solvedProblem = item(7L))
        val viewModel = ReviewProblemViewModel(repository, problemId = 7L)

        val state = viewModel.uiState.value as CategoryHistoryProblemDetailUiState.Ready
        assertEquals(7L, state.item.problemId)
        assertEquals("프로세스란?", state.item.question)
    }

    @Test
    fun 조회에_실패하면_Error로_떨어진다() = runTest {
        val repository = FakeCategoryHistoryRepository(solvedProblemFailWith = RuntimeException("404"))
        val viewModel = ReviewProblemViewModel(repository, problemId = 7L)

        assertTrue(viewModel.uiState.value is CategoryHistoryProblemDetailUiState.Error)
    }
}
