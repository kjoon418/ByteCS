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
 * 카테고리 이력의 한 문제 상세(레벨3) 뷰모델 단위 테스트. 카테고리+problemId로 항목을 찾으면 Ready,
 * 없으면 방어적으로 Error, 저장소 조회 실패면 Error로 떨어지는지 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryHistoryProblemDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun item(problemId: Long, question: String) = CategoryHistoryItem(
        problemId = problemId,
        question = question,
        codeSnippet = null,
        difficulty = "MEDIUM",
        result = JudgeResult.CORRECT,
        concepts = listOf("해시 충돌"),
        explanation = "해설",
        representativeAnswer = "해시 충돌 (collision)",
    )

    @Test
    fun 카테고리와_problemId로_항목을_찾으면_Ready로_노출한다() = runTest {
        val repository = FakeCategoryHistoryRepository(
            groups = listOf(
                CategoryHistoryGroup("DATA_STRUCTURE", listOf(item(1L, "해시 충돌이란?"), item(2L, "트리란?"))),
            ),
        )
        val viewModel = CategoryHistoryProblemDetailViewModel(repository, category = "DATA_STRUCTURE", problemId = 2L)

        val state = viewModel.uiState.value as CategoryHistoryProblemDetailUiState.Ready
        assertEquals(2L, state.item.problemId)
        assertEquals("트리란?", state.item.question)
    }

    @Test
    fun 항목을_찾지_못하면_Error로_떨어진다() = runTest {
        val repository = FakeCategoryHistoryRepository(
            groups = listOf(CategoryHistoryGroup("DATA_STRUCTURE", listOf(item(1L, "해시 충돌이란?")))),
        )
        val viewModel = CategoryHistoryProblemDetailViewModel(repository, category = "DATA_STRUCTURE", problemId = 99L)

        assertTrue(viewModel.uiState.value is CategoryHistoryProblemDetailUiState.Error)
    }

    @Test
    fun 조회에_실패하면_Error로_떨어진다() = runTest {
        val repository = FakeCategoryHistoryRepository(getFailWith = RuntimeException("network"))
        val viewModel = CategoryHistoryProblemDetailViewModel(repository, category = "DATA_STRUCTURE", problemId = 1L)

        assertTrue(viewModel.uiState.value is CategoryHistoryProblemDetailUiState.Error)
    }
}
