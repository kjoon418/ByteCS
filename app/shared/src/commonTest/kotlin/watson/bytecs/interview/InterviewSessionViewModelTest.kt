package watson.bytecs.interview

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import watson.bytecs.session.Streak

/** 08 면접 세션 화면 상태 홀더: 로드→쓰기→채점→결과→다음 문항, 완료·오류 분기를 검증한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class InterviewSessionViewModelTest {

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
    fun 로드에_성공하면_현재_문항으로_활성_상태가_된다() = runTest {
        val repository = FakeInterviewRepository(session = FakeInterviewRepository.activeSession(position = 0, total = 3))
        val viewModel = InterviewSessionViewModel(repository)

        viewModel.load()

        val state = assertIs<InterviewUiState.Active>(viewModel.uiState.value)
        assertEquals(0, state.position)
        assertEquals(3, state.total)
        assertEquals(InterviewPhase.Writing, state.phase)
    }

    @Test
    fun 이미_완료된_세션으로_진입하면_곧장_완료_이벤트를_보낸다() = runTest {
        val repository = FakeInterviewRepository(session = FakeInterviewRepository.completedSession())
        val viewModel = InterviewSessionViewModel(repository)

        viewModel.load()

        assertEquals(InterviewEvent.Finished, viewModel.events.first())
    }

    @Test
    fun 로드에_실패하면_오류_상태다() = runTest {
        val repository = FakeInterviewRepository().apply { startError = RuntimeException("network") }
        val viewModel = InterviewSessionViewModel(repository)

        viewModel.load()

        assertEquals(InterviewUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun 빈_입력은_제출되지_않는다() = runTest {
        val repository = FakeInterviewRepository()
        val viewModel = InterviewSessionViewModel(repository)
        viewModel.load()

        viewModel.submit()

        assertEquals(0, repository.submitCount)
    }

    @Test
    fun 제출에_성공하면_채점_결과가_결과_단계에_실린다() = runTest {
        val repository = FakeInterviewRepository(session = FakeInterviewRepository.activeSession(position = 0, total = 2))
        repository.onSubmit = { _, _ ->
            FakeInterviewRepository.outcome(
                result = FakeInterviewRepository.successResult(InterviewReadiness.PARTIAL, satisfied = 1, unsatisfied = 1),
                next = FakeInterviewRepository.item(position = 1),
                conceptName = "개념0",
            )
        }
        val viewModel = InterviewSessionViewModel(repository)
        viewModel.load()
        viewModel.onInputChange("제 설명이에요")

        viewModel.submit()

        val state = assertIs<InterviewUiState.Active>(viewModel.uiState.value)
        assertTrue(state.isResult)
        assertEquals(1, state.result?.judge?.satisfiedCount)
        assertEquals(1, state.result?.judge?.unsatisfiedCount)
        assertFalse(state.isLastItem)
        assertEquals(1, repository.submitCount)
        assertEquals(0 to "제 설명이에요", repository.submitted.single())
    }

    @Test
    fun 마지막_문항_제출이면_완료_요약이_실리고_CTA가_마치기로_바뀐다() = runTest {
        val repository = FakeInterviewRepository(session = FakeInterviewRepository.activeSession(position = 0, total = 1))
        repository.onSubmit = { _, _ ->
            FakeInterviewRepository.outcome(
                result = FakeInterviewRepository.successResult(InterviewReadiness.VERIFIED, satisfied = 2, unsatisfied = 0),
                next = null,
                completion = InterviewCompletion(practicedConceptCount = 1, streak = Streak(count = 3, lastStudyDate = "2026-07-23")),
            )
        }
        val viewModel = InterviewSessionViewModel(repository)
        viewModel.load()
        viewModel.onInputChange("설명")

        viewModel.submit()

        val state = assertIs<InterviewUiState.Active>(viewModel.uiState.value)
        assertTrue(state.isLastItem)
        assertEquals(1, state.completion?.practicedConceptCount)
    }

    @Test
    fun 제출_전송이_실패하면_입력을_보존한_채_시스템_오류로_되돌아간다() = runTest {
        val repository = FakeInterviewRepository(session = FakeInterviewRepository.activeSession())
        repository.submitError = RuntimeException("network")
        val viewModel = InterviewSessionViewModel(repository)
        viewModel.load()
        viewModel.onInputChange("제 설명")

        viewModel.submit()

        val state = assertIs<InterviewUiState.Active>(viewModel.uiState.value)
        assertEquals(InterviewPhase.Writing, state.phase)
        assertTrue(state.systemError)
        assertEquals("제 설명", state.inputText)
    }

    @Test
    fun 폴백_제출이면_결과에_폴백이_실리고_검증되지_않은_것으로_본다() = runTest {
        val repository = FakeInterviewRepository(session = FakeInterviewRepository.activeSession(position = 0, total = 1))
        repository.onSubmit = { _, _ ->
            FakeInterviewRepository.outcome(
                result = FakeInterviewRepository.fallbackResult(),
                next = null,
                completion = InterviewCompletion(practicedConceptCount = 1, streak = null),
                modelAnswer = "모범 설명 원문",
            )
        }
        val viewModel = InterviewSessionViewModel(repository)
        viewModel.load()
        viewModel.onInputChange("제 설명")

        viewModel.submit()

        val state = assertIs<InterviewUiState.Active>(viewModel.uiState.value)
        assertTrue(state.isResult)
        val judge = assertIs<ExplanationJudgeResult>(state.result?.judge)
        assertTrue(judge.fallback)
        assertFalse(judge.verified)
        assertEquals(0, judge.points.size)
        assertEquals("모범 설명 원문", state.result?.modelAnswer)
        assertEquals(null, state.result?.reviewProblemId)
    }

    @Test
    fun 소진이나_후보없음으로_진입하면_시스템오류_대신_홈으로_되돌린다() = runTest {
        val repository = FakeInterviewRepository().apply { startError = InterviewUnavailableException() }
        val viewModel = InterviewSessionViewModel(repository)

        viewModel.load()

        assertEquals(InterviewEvent.Finished, viewModel.events.first())
    }

    @Test
    fun 세션이_이미_끝나_제출이_거부되면_홈으로_되돌린다() = runTest {
        val repository = FakeInterviewRepository(session = FakeInterviewRepository.activeSession())
        repository.submitError = InterviewUnavailableException()
        val viewModel = InterviewSessionViewModel(repository)
        viewModel.load()
        viewModel.onInputChange("제 설명")

        viewModel.submit()

        assertEquals(InterviewEvent.Finished, viewModel.events.first())
    }

    @Test
    fun 제출_응답_해석에_실패하면_재제출이_아니라_오류상태로_복구한다() = runTest {
        // 서버가 2xx로 답을 반영했지만 응답을 해석하지 못한 경우 — 같은 답 재제출(systemError)이 아니라 재로드로 복구해야 한다.
        val repository = FakeInterviewRepository(session = FakeInterviewRepository.activeSession())
        repository.submitError = InterviewResponseMappingException(RuntimeException("malformed body"))
        val viewModel = InterviewSessionViewModel(repository)
        viewModel.load()
        viewModel.onInputChange("제 설명")

        viewModel.submit()

        assertEquals(InterviewUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun 다음으로_진행하면_다음_문항으로_전환되고_입력이_초기화된다() = runTest {
        val repository = FakeInterviewRepository(session = FakeInterviewRepository.activeSession(position = 0, total = 2))
        repository.onSubmit = { _, _ ->
            FakeInterviewRepository.outcome(
                result = FakeInterviewRepository.successResult(InterviewReadiness.VERIFIED, satisfied = 1, unsatisfied = 0),
                next = FakeInterviewRepository.item(position = 1),
            )
        }
        val viewModel = InterviewSessionViewModel(repository)
        viewModel.load()
        viewModel.onInputChange("설명")
        viewModel.submit()

        viewModel.advance()

        val state = assertIs<InterviewUiState.Active>(viewModel.uiState.value)
        assertEquals(1, state.position)
        assertEquals(InterviewPhase.Writing, state.phase)
        assertEquals("", state.inputText)
    }

    @Test
    fun 마치기는_완료_요약이_있을_때만_완료_이벤트를_한_번_보낸다() = runTest {
        val repository = FakeInterviewRepository(session = FakeInterviewRepository.activeSession(position = 0, total = 1))
        repository.onSubmit = { _, _ ->
            FakeInterviewRepository.outcome(
                result = FakeInterviewRepository.successResult(InterviewReadiness.VERIFIED, satisfied = 1, unsatisfied = 0),
                next = null,
                completion = InterviewCompletion(practicedConceptCount = 1, streak = null),
            )
        }
        val viewModel = InterviewSessionViewModel(repository)
        viewModel.load()
        viewModel.onInputChange("설명")
        viewModel.submit()

        viewModel.finish()

        assertEquals(InterviewEvent.Finished, viewModel.events.first())
    }
}
