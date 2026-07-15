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
 * 07 콘텐츠 오류 신고 화면의 상태 홀더(team-plan.md §B [계약 v2]).
 *
 * ⭐️ 제출 가능 조건은 **유형 선택**([ReportUiState.category] != null)뿐이다 — 상세 내용은 선택이라
 * 비어 있어도 제출된다. 전송 실패는 오답과 무관한 시스템 오류이므로 입력을 유지한 채 재시도 경로를 남긴다(§5.12).
 */
class ReportViewModel(
    private val repository: ContentReportRepository,
    private val problemId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    /** 유형 선택(단일 선택, 필수). 고르는 순간 직전 전송 실패 표시를 지운다. */
    fun onCategorySelect(category: ReportCategory) {
        _uiState.update { it.copy(category = category, submitFailed = false) }
    }

    /** 상세 내용 변경(선택 입력). 고치는 순간 직전 전송 실패 표시를 지운다. */
    fun onMessageChange(text: String) {
        _uiState.update { it.copy(message = text, submitFailed = false) }
    }

    /**
     * 신고 전송. 유형 미선택·전송 중·이미 접수됨이면 무시한다(이중 전송 방지).
     * 상세 내용은 비어 있어도 보낸다 — 빈 문자열은 null로 정규화해 "미기재"를 명확히 한다.
     */
    fun submit() {
        val current = _uiState.value
        val category = current.category
        if (category == null || current.isSubmitting || current.submitted) return

        _uiState.value = current.copy(isSubmitting = true, submitFailed = false)
        viewModelScope.launch {
            try {
                repository.report(problemId, category, current.message.trim().ifBlank { null })
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
 *  - [category]: 유형(필수, 단일 선택). null이면 아직 선택 전.
 *  - [message]: 상세 내용(선택). 비어도 제출 가능.
 *  - [submitted]: 접수 완료(감사 안내 표시, 입력 종료).
 *  - [submitFailed]: 전송 실패(시스템 오류) — 입력 유지, 재시도 가능. 오답 취급 아님.
 */
data class ReportUiState(
    val category: ReportCategory? = null,
    val message: String = "",
    val isSubmitting: Boolean = false,
    val submitFailed: Boolean = false,
    val submitted: Boolean = false,
)
