package watson.bytecs.problem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 03 문제 풀이 화면의 상태 홀더.
 *
 * 무낙인 상태 모델: 제출은 [Feedback]만 바꾼다. 불일치·근접이어도 입력([Ready.inputText])은
 * 유지되고, 사용자가 답을 고치면([onInputChange]) 이전 피드백은 자연스럽게 지워진다.
 *
 * 시스템 오류는 사용자 오답과 엄격히 구분한다(§5.12):
 *  - 문제 로드 실패 → [ProblemUiState.Error](재시도 가능).
 *  - 제출 전송 실패 → [Ready.submitFailed](입력·피드백 유지, 재시도 가능). 오답 취급 아님.
 */
class ProblemViewModel(
    private val repository: ProblemRepository,
    private val totalProblems: Int = DEFAULT_TOTAL,
    private val currentIndex: Int = 1,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProblemUiState>(ProblemUiState.Loading)
    val uiState: StateFlow<ProblemUiState> = _uiState.asStateFlow()

    // 현재 세션에서 몇 번째 본 문제인지. 다음 문제로 넘어갈 때 증가한다(총량은 [totalProblems] 상수).
    private var position = currentIndex.coerceIn(1, totalProblems)

    init {
        load()
    }

    /** 현재 위치의 문제를 (다시) 불러온다. 오류 재시도에도 쓰인다(위치를 진전시키지 않음). */
    fun loadProblem() = load()

    /**
     * M6: 정답 후 다음 문제로. 진행도를 한 칸 올리고 새 문제를 불러온다(입력·피드백 초기화).
     * 마지막 문제(total 도달)면 위치를 유지한 채 안전하게 정지한다(04 세션 완료는 이 슬라이스 밖).
     */
    fun loadNext() {
        if (position < totalProblems) position++
        load()
    }

    private fun load() {
        _uiState.value = ProblemUiState.Loading
        viewModelScope.launch {
            try {
                val problem = repository.getNext()
                _uiState.value = ProblemUiState.Ready(
                    problem = problem,
                    inputText = "",
                    feedback = null,
                    current = position,
                    total = totalProblems,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation // 스코프 취소는 오류가 아니므로 그대로 전파한다.
            } catch (error: Throwable) {
                _uiState.value = ProblemUiState.Error
            }
        }
    }

    /** 답 입력 변경. 답을 고치는 순간 직전 피드백·전송 실패 표시를 지워 다음 제출을 깨끗이 시작한다. */
    fun onInputChange(text: String) {
        _uiState.update { state ->
            if (state is ProblemUiState.Ready) {
                state.copy(inputText = text, feedback = null, submitFailed = false)
            } else {
                state
            }
        }
    }

    /**
     * 정답 확인. 판정 결과를 피드백으로 반영한다(입력은 유지).
     * ⭐️ 이중 제출 가드: 이미 전송 중이면 무시해 같은 답이 두 번 채점되지 않게 한다.
     */
    fun submit() {
        val current = _uiState.value
        if (current !is ProblemUiState.Ready || current.inputText.isBlank() || current.isSubmitting) return

        _uiState.value = current.copy(isSubmitting = true, submitFailed = false)
        viewModelScope.launch {
            try {
                val result = repository.submitAttempt(current.problem.id, current.inputText)
                // L8(마이너·미처리): 전송 중 입력을 고쳐도 인플라이트 결과가 그대로 반영된다.
                _uiState.update { state ->
                    if (state is ProblemUiState.Ready) state.copy(feedback = result.toFeedback()) else state
                }
            } catch (cancellation: CancellationException) {
                throw cancellation // 취소는 오답·시스템오류가 아니므로 전파(플래그는 finally에서 해제).
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state is ProblemUiState.Ready) state.copy(submitFailed = true) else state
                }
            } finally {
                // 취소 포함 어떤 종료 경로에서도 버튼 잠금을 반드시 푼다.
                _uiState.update { state ->
                    if (state is ProblemUiState.Ready) state.copy(isSubmitting = false) else state
                }
            }
        }
    }

    private fun AttemptResult.toFeedback(): Feedback = when (result) {
        JudgeResult.CORRECT -> Feedback.Correct(concept = concept, explanation = explanation)
        JudgeResult.NEAR_MISS -> Feedback.NearMiss
        JudgeResult.MISMATCH -> Feedback.Mismatch
    }

    companion object {
        const val DEFAULT_TOTAL = 5
    }
}

/** 화면 상태. 로딩·준비·오류 세 국면. */
sealed interface ProblemUiState {
    data object Loading : ProblemUiState

    data class Ready(
        val problem: ProblemView,
        val inputText: String,
        val feedback: Feedback?,
        val current: Int,
        val total: Int,
        val isSubmitting: Boolean = false,
        val submitFailed: Boolean = false,
    ) : ProblemUiState

    /** 문제 로드 실패(시스템 오류). 재시도 가능. */
    data object Error : ProblemUiState
}

/**
 * 제출 피드백. 세 상태 모두 비처벌.
 *  - [Correct]: 긍정(success). 개념·해설 노출 가능.
 *  - [Mismatch]: 중립 재시도 넛지(neutralNudge). 정답·개념 비노출.
 *  - [NearMiss]: 오탈자 안내(info). 정답·개념 비노출.
 */
sealed interface Feedback {
    data class Correct(val concept: String?, val explanation: String?) : Feedback
    data object Mismatch : Feedback
    data object NearMiss : Feedback
}
