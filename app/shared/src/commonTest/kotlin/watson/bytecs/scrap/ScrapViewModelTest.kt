package watson.bytecs.scrap

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 스크랩 목록·재열람 뷰모델 단위 테스트. 빈 목록·오류·스크랩 토글(낙관적 반영·실패 되돌림)을 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrapViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun detail(id: Long) = ScrapDetail(
        problemId = id,
        question = "문제 $id",
        codeSnippet = null,
        concepts = listOf("개념 $id"),
        explanation = "해설 $id",
        acceptableAnswers = listOf("답$id"),
    )

    // ── 목록 ───────────────────────────────────────────────────────────────────

    @Test
    fun 스크랩이_없으면_빈_Ready로_노출한다() = runTest {
        val viewModel = ScrapListViewModel(FakeScrapRepository())
        viewModel.refresh()

        val state = viewModel.uiState.value
        assertTrue(state is ScrapListUiState.Ready && state.items.isEmpty(), "빈 목록은 오류가 아니라 빈 Ready다")
    }

    @Test
    fun 스크랩이_있으면_항목을_노출한다() = runTest {
        val viewModel = ScrapListViewModel(FakeScrapRepository(seeds = listOf(detail(7L))))
        viewModel.refresh()

        val state = viewModel.uiState.value as ScrapListUiState.Ready
        assertEquals(1, state.items.size)
        assertEquals(7L, state.items[0].problemId)
    }

    @Test
    fun 목록_조회에_실패하면_Error로_떨어진다() = runTest {
        val viewModel = ScrapListViewModel(FakeScrapRepository(listFailWith = RuntimeException("network")))
        viewModel.refresh()

        assertTrue(viewModel.uiState.value is ScrapListUiState.Error)
    }

    // ── 재열람·토글 ────────────────────────────────────────────────────────────

    @Test
    fun 재열람은_스크랩된_상태로_문제_모범답안을_불러온다() = runTest {
        val viewModel = ScrapDetailViewModel(FakeScrapRepository(seeds = listOf(detail(7L))), problemId = 7L)

        val state = viewModel.uiState.value as ScrapDetailUiState.Ready
        assertEquals("문제 7", state.detail.question)
        assertEquals(listOf("답7"), state.detail.acceptableAnswers)
        assertTrue(state.scrapped, "재열람은 스크랩된 상태에서 열린다")
    }

    @Test
    fun 토글하면_서버에_반영되고_상태가_해제된다() = runTest {
        val repository = FakeScrapRepository(seeds = listOf(detail(7L)))
        val viewModel = ScrapDetailViewModel(repository, problemId = 7L)

        viewModel.toggleScrap()

        assertFalse((viewModel.uiState.value as ScrapDetailUiState.Ready).scrapped)
        assertFalse(repository.isScrapped(7L), "해제가 서버(저장소)에 반영돼야 한다")
    }

    @Test
    fun 토글에_실패하면_이전_스크랩_상태로_되돌린다() = runTest {
        // 해제 요청이 실패하면 스크랩 상태를 유지해야 한다(낙관적 업데이트 되돌림).
        val repository = FakeScrapRepository(
            seeds = listOf(detail(7L)),
            toggleFailWith = RuntimeException("network"),
        )
        val viewModel = ScrapDetailViewModel(repository, problemId = 7L)

        viewModel.toggleScrap()

        assertTrue(
            (viewModel.uiState.value as ScrapDetailUiState.Ready).scrapped,
            "전송 실패 시 이전 상태(스크랩됨)로 되돌려야 한다",
        )
    }
}
