package watson.bytecs.problem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 03 문제 풀이 화면의 상태 홀더.
 *
 * 무낙인 상태 모델: 제출은 [Feedback]만 바꾼다. 불일치·근접이어도 입력([Ready.inputText])은
 * 유지되고, 사용자가 답을 고치면([onInputChange]) 이전 피드백은 자연스럽게 지워진다.
 */
class ProblemViewModel(
    private val repository: ProblemRepository,
    private val totalProblems: Int = DEFAULT_TOTAL,
    private val currentIndex: Int = 1,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProblemUiState>(ProblemUiState.Loading)
    val uiState: StateFlow<ProblemUiState> = _uiState.asStateFlow()

    init {
        loadProblem()
    }

    fun loadProblem() {
        _uiState.value = ProblemUiState.Loading
        viewModelScope.launch {
            val problem = repository.getNext()
            _uiState.value = ProblemUiState.Ready(
                problem = problem,
                inputText = "",
                feedback = null,
                current = currentIndex,
                total = totalProblems,
            )
        }
    }

    /** 답 입력 변경. 답을 고치는 순간 직전 피드백을 지워 다음 제출을 깨끗한 상태로 만든다. */
    fun onInputChange(text: String) {
        _uiState.update { state ->
            if (state is ProblemUiState.Ready) {
                state.copy(inputText = text, feedback = null)
            } else {
                state
            }
        }
    }

    /** 정답 확인. 판정 결과를 피드백으로 반영한다(입력은 유지). */
    fun submit() {
        val current = _uiState.value
        if (current !is ProblemUiState.Ready || current.inputText.isBlank()) return

        viewModelScope.launch {
            val result = repository.submitAttempt(current.problem.id, current.inputText)
            _uiState.update { state ->
                if (state is ProblemUiState.Ready) {
                    state.copy(feedback = result.toFeedback())
                } else {
                    state
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

/** 화면 상태. 로딩·준비 두 국면. */
sealed interface ProblemUiState {
    data object Loading : ProblemUiState

    data class Ready(
        val problem: ProblemView,
        val inputText: String,
        val feedback: Feedback?,
        val current: Int,
        val total: Int,
    ) : ProblemUiState
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
