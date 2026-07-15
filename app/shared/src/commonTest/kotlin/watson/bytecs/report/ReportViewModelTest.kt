package watson.bytecs.report

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
 * ReportViewModel 단위 테스트. 빈 신고 차단·이중 전송 방지·전송 실패 처리(무낙인)를 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun 빈_메시지로는_전송하지_않는다() = runTest {
        val repository = FakeContentReportRepository()
        val viewModel = ReportViewModel(repository, problemId = 7L)

        // 공백만 있어도 blank로 간주해 막는다.
        viewModel.onMessageChange("   ")
        viewModel.submit()

        assertTrue(repository.submitted.isEmpty(), "빈 신고는 서버로 나가면 안 된다")
        assertFalse(viewModel.uiState.value.submitted)
    }

    @Test
    fun 메시지를_보내면_문제_id와_다듬은_메시지가_접수된다() = runTest {
        val repository = FakeContentReportRepository()
        val viewModel = ReportViewModel(repository, problemId = 7L)

        viewModel.onMessageChange("  정답이 틀렸어요  ")
        viewModel.submit()

        assertEquals(listOf(7L to "정답이 틀렸어요"), repository.submitted)
        assertTrue(viewModel.uiState.value.submitted)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun 접수된_뒤에는_다시_전송하지_않는다() = runTest {
        val repository = FakeContentReportRepository()
        val viewModel = ReportViewModel(repository, problemId = 7L)

        viewModel.onMessageChange("문제 설명에 오류가 있어요")
        viewModel.submit()
        viewModel.submit() // 접수 후 재클릭.

        assertEquals(1, repository.submitted.size, "이미 접수된 신고를 이중 전송하지 않는다")
    }

    @Test
    fun 전송_실패는_오답이_아니라_시스템_오류로_표시하고_입력을_유지한다() = runTest {
        val repository = FakeContentReportRepository(failWith = RuntimeException("network"))
        val viewModel = ReportViewModel(repository, problemId = 7L)

        viewModel.onMessageChange("힌트에 오류가 있어요")
        viewModel.submit()

        val state = viewModel.uiState.value
        assertTrue(state.submitFailed, "전송 실패를 시스템 오류로 표시한다")
        assertFalse(state.submitted)
        assertFalse(state.isSubmitting)
        assertEquals("힌트에 오류가 있어요", state.message, "재시도할 수 있도록 입력을 유지한다")
    }

    @Test
    fun 입력을_고치면_직전_전송_실패_표시가_사라진다() = runTest {
        val repository = FakeContentReportRepository(failWith = RuntimeException("network"))
        val viewModel = ReportViewModel(repository, problemId = 7L)

        viewModel.onMessageChange("오타 신고")
        viewModel.submit()
        assertTrue(viewModel.uiState.value.submitFailed)

        viewModel.onMessageChange("오타 신고 수정")
        assertFalse(viewModel.uiState.value.submitFailed, "입력을 고치면 다음 제출을 깨끗이 시작한다")
    }
}
