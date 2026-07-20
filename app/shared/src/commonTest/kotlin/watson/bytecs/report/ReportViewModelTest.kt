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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ReportViewModel 단위 테스트(team-plan.md §B [계약 v2]).
 * 유형 미선택 제출 차단·상세 내용 선택(빈 값 제출 허용)·이중 전송 방지·전송 실패 처리(무낙인)를 검증한다.
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

    /** ⭐️ 제출 가능 조건은 유형 선택뿐이다 — 상세 내용은 무관하게, 유형 없이는 막는다. */
    @Test
    fun 유형을_선택하지_않으면_전송하지_않는다() = runTest {
        val repository = FakeContentReportRepository()
        val viewModel = ReportViewModel(repository, problemId = 7L)

        viewModel.onMessageChange("상세 내용만 적었어요")
        viewModel.submit()

        assertTrue(repository.submitted.isEmpty(), "유형 없이는 서버로 나가면 안 된다")
        assertFalse(viewModel.uiState.value.submitted)
    }

    /** ⭐️ 상세 내용은 선택이다 — 유형만 골라도 제출된다. */
    @Test
    fun 유형만_선택해도_전송된다() = runTest {
        val repository = FakeContentReportRepository()
        val viewModel = ReportViewModel(repository, problemId = 7L)

        viewModel.onCategorySelect(ReportCategory.WRONG_ANSWER)
        viewModel.submit()

        assertEquals(1, repository.submitted.size)
        val (problemId, category, message) = repository.submitted[0]
        assertEquals(7L, problemId)
        assertEquals(ReportCategory.WRONG_ANSWER, category)
        assertNull(message, "빈 상세 내용은 null로 정규화해 미기재를 표현한다")
        assertTrue(viewModel.uiState.value.submitted)
    }

    @Test
    fun 유형과_상세_내용을_함께_보내면_다듬은_메시지가_접수된다() = runTest {
        val repository = FakeContentReportRepository()
        val viewModel = ReportViewModel(repository, problemId = 7L)

        viewModel.onCategorySelect(ReportCategory.QUESTION_ERROR)
        viewModel.onMessageChange("  설명이 이상해요  ")
        viewModel.submit()

        val (_, category, message) = repository.submitted[0]
        assertEquals(ReportCategory.QUESTION_ERROR, category)
        assertEquals("설명이 이상해요", message)
    }

    @Test
    fun 접수된_뒤에는_다시_전송하지_않는다() = runTest {
        val repository = FakeContentReportRepository()
        val viewModel = ReportViewModel(repository, problemId = 7L)

        viewModel.onCategorySelect(ReportCategory.HINT_ERROR)
        viewModel.submit()
        viewModel.submit() // 접수 후 재클릭.

        assertEquals(1, repository.submitted.size, "이미 접수된 신고를 이중 전송하지 않는다")
    }

    @Test
    fun 전송_실패는_오답이_아니라_시스템_오류로_표시하고_입력을_유지한다() = runTest {
        val repository = FakeContentReportRepository(failWith = RuntimeException("network"))
        val viewModel = ReportViewModel(repository, problemId = 7L)

        viewModel.onCategorySelect(ReportCategory.HINT_ERROR)
        viewModel.onMessageChange("힌트에 오류가 있어요")
        viewModel.submit()

        val state = viewModel.uiState.value
        assertTrue(state.submitFailed, "전송 실패를 시스템 오류로 표시한다")
        assertFalse(state.submitted)
        assertFalse(state.isSubmitting)
        assertEquals(ReportCategory.HINT_ERROR, state.category, "재시도할 수 있도록 선택을 유지한다")
        assertEquals("힌트에 오류가 있어요", state.message, "재시도할 수 있도록 입력을 유지한다")
    }

    @Test
    fun 입력을_고치면_직전_전송_실패_표시가_사라진다() = runTest {
        val repository = FakeContentReportRepository(failWith = RuntimeException("network"))
        val viewModel = ReportViewModel(repository, problemId = 7L)

        viewModel.onCategorySelect(ReportCategory.OTHER)
        viewModel.submit()
        assertTrue(viewModel.uiState.value.submitFailed)

        viewModel.onMessageChange("오타 신고 수정")
        assertFalse(viewModel.uiState.value.submitFailed, "입력을 고치면 다음 제출을 깨끗이 시작한다")
    }

    // ── D2: 프리셋 유형('내 답이 맞았던 것 같아요') ──────────────────────────────

    /** 정답 공개 패널의 프리셋 진입은 유형을 미리 선택한 채로 시작해, 바로 제출할 수 있다. */
    @Test
    fun 프리셋_유형으로_진입하면_해당_유형이_이미_선택돼_있다() = runTest {
        val repository = FakeContentReportRepository()
        val viewModel = ReportViewModel(repository, problemId = 7L, presetCategory = ReportCategory.WRONG_ANSWER)

        assertEquals(ReportCategory.WRONG_ANSWER, viewModel.uiState.value.category)

        viewModel.submit()

        assertEquals(1, repository.submitted.size)
        val (_, category, _) = repository.submitted[0]
        assertEquals(ReportCategory.WRONG_ANSWER, category)
    }

    /** 프리셋은 기본값일 뿐 고정이 아니다 — 사용자가 여전히 다른 유형으로 바꿀 수 있다. */
    @Test
    fun 프리셋_유형이_있어도_다른_유형으로_바꿀_수_있다() = runTest {
        val repository = FakeContentReportRepository()
        val viewModel = ReportViewModel(repository, problemId = 7L, presetCategory = ReportCategory.WRONG_ANSWER)

        viewModel.onCategorySelect(ReportCategory.HINT_ERROR)
        viewModel.submit()

        val (_, category, _) = repository.submitted[0]
        assertEquals(ReportCategory.HINT_ERROR, category)
    }

    /** 프리셋이 없는 일반 진입(기존 '오류 신고')은 그대로 유형 미선택 상태로 시작한다(회귀 방지). */
    @Test
    fun 프리셋이_없으면_유형_미선택_상태로_시작한다() = runTest {
        val repository = FakeContentReportRepository()
        val viewModel = ReportViewModel(repository, problemId = 7L)

        assertNull(viewModel.uiState.value.category)
    }

    @Test
    fun 유형을_다시_고르면_직전_전송_실패_표시가_사라진다() = runTest {
        val repository = FakeContentReportRepository(failWith = RuntimeException("network"))
        val viewModel = ReportViewModel(repository, problemId = 7L)

        viewModel.onCategorySelect(ReportCategory.WRONG_ANSWER)
        viewModel.submit()
        assertTrue(viewModel.uiState.value.submitFailed)

        viewModel.onCategorySelect(ReportCategory.OTHER)
        assertFalse(viewModel.uiState.value.submitFailed, "유형을 다시 고르면 다음 제출을 깨끗이 시작한다")
    }
}
