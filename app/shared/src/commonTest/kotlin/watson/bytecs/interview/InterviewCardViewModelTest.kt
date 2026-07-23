package watson.bytecs.interview

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

/** 홈 면접 연습 카드 상태 홀더: 서버 단일 출처([InterviewStatus])를 4개 카드 상태로 정확히 분기하는지 검증한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class InterviewCardViewModelTest {

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
    fun 게스트면_후보_수를_담아_가입_유도_상태다() = runTest {
        val repository = FakeInterviewRepository(statusResult = InterviewStatus(guest = true, candidateCount = 4, remainingToday = 0))
        val viewModel = InterviewCardViewModel(repository)

        viewModel.refresh()

        assertEquals(InterviewCardUiState.Guest(4), viewModel.uiState.value)
    }

    @Test
    fun 후보가_0개면_긍정_빈_상태다() = runTest {
        val repository = FakeInterviewRepository(statusResult = InterviewStatus(guest = false, candidateCount = 0, remainingToday = 1))
        val viewModel = InterviewCardViewModel(repository)

        viewModel.refresh()

        assertEquals(InterviewCardUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun 후보가_있고_쿼터가_남으면_진입_CTA다() = runTest {
        val repository = FakeInterviewRepository(statusResult = InterviewStatus(guest = false, candidateCount = 2, remainingToday = 1))
        val viewModel = InterviewCardViewModel(repository)

        viewModel.refresh()

        assertEquals(InterviewCardUiState.Ready(2), viewModel.uiState.value)
    }

    @Test
    fun 후보가_있지만_오늘_쿼터를_소진했으면_담백_안내다() = runTest {
        val repository = FakeInterviewRepository(statusResult = InterviewStatus(guest = false, candidateCount = 2, remainingToday = 0))
        val viewModel = InterviewCardViewModel(repository)

        viewModel.refresh()

        assertEquals(InterviewCardUiState.Exhausted, viewModel.uiState.value)
    }

    @Test
    fun 상태_조회에_실패하면_카드를_조용히_숨긴다() = runTest {
        val repository = FakeInterviewRepository().apply { statusError = RuntimeException("network") }
        val viewModel = InterviewCardViewModel(repository)

        viewModel.refresh()

        assertEquals(InterviewCardUiState.Hidden, viewModel.uiState.value)
    }
}
