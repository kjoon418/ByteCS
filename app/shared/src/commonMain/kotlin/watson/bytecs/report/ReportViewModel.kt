package watson.bytecs.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 07 콘텐츠 오류 신고 화면의 상태 홀더.
 *
 * ⭐️ 빈 신고는 보내지 않는다: [ReportUiState.message]가 blank면 제출이 막힌다(화면·서버 모두 거부).
 * 전송 실패는 오답과 무관한 시스템 오류이므로 입력을 유지한 채 재시도 경로를 남긴다(§5.12).
 */
class ReportViewModel(
    private val repository: ContentReportRepository,
    private val problemId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    /** 입력 변경. 고치는 순간 직전 전송 실패 표시를 지워 다음 제출을 깨끗이 시작한다. */
    fun onMessageChange(text: String) {
        _uiState.update { it.copy(message = text, submitFailed = false) }
    }

    /**
     * 신고 전송. 빈 입력·전송 중·이미 접수됨이면 무시한다(빈 신고·이중 전송 방지).
     */
    fun submit() {
        val current = _uiState.value
        if (current.message.isBlank() || current.isSubmitting || current.submitted) return

        _uiState.value = current.copy(isSubmitting = true, submitFailed = false)
        viewModelScope.launch {
            try {
                repository.report(problemId, current.message.trim())
                _uiState.update { it.copy(isSubmitting = false, submitted = true) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.update { it.copy(isSubmitting = false, submitFailed = true) }
            }
        }
    }
}

/**
 * 07 신고 화면 상태.
 *  - [submitted]: 접수 완료(감사 안내 표시, 입력 종료).
 *  - [submitFailed]: 전송 실패(시스템 오류) — 입력 유지, 재시도 가능. 오답 취급 아님.
 */
data class ReportUiState(
    val message: String = "",
    val isSubmitting: Boolean = false,
    val submitFailed: Boolean = false,
    val submitted: Boolean = false,
)
