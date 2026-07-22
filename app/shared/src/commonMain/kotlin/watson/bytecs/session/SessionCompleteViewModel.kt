package watson.bytecs.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.bytecs.account.PreferredDifficulty
import watson.bytecs.account.SessionManager
import kotlin.coroutines.cancellation.CancellationException

/**
 * 04 완료 화면의 선호 난이도 제안 카드(구성 8, DF1) 상태 홀더.
 *
 * ⭐️ 노출 여부는 [needsDifficultyPrompt] 하나만 따른다 — 서버가 단일 출처로 판단해 완료 요약에 실어 준
 * 신호를 그대로 초기값으로 쓸 뿐, 클라이언트가 "선호 미설정 && 미응답" 조건을 다시 계산하지 않는다.
 * ⭐️ 완결 축하·스트릭의 성취 위계를 가리지 않는 '가벼운 초대'라, 저장이 실패해도 danger 톤 없이 카드를
 * 유지하고 재시도만 열어둔다(UX 에러 응답 가이드 — 해결 방법을 알려준다·부정적 감정 최소화).
 */
class SessionCompleteViewModel(
    private val sessionManager: SessionManager,
    needsDifficultyPrompt: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DifficultyPromptUiState(visible = needsDifficultyPrompt))
    val uiState: StateFlow<DifficultyPromptUiState> = _uiState.asStateFlow()

    /** 상태 서술형 3택 중 하나 선택 — 그 선호로 저장하고 카드를 닫는다. */
    fun select(value: PreferredDifficulty) {
        val current = _uiState.value
        if (!current.visible || current.saving) return

        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                sessionManager.updatePreferredDifficulty(value)
                _uiState.update { it.copy(visible = false, saving = false) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(saving = false, error = "저장하지 못했어요. 잠시 후 다시 시도해 주세요.")
                }
            }
        }
    }

    /** "지금은 괜찮아요" — 거절을 기록한다. 성공하면 은은한 안내([dismissedNotice])를 잠깐 보여준다. */
    fun dismiss() {
        val current = _uiState.value
        if (!current.visible || current.saving) return

        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                sessionManager.dismissDifficultyPrompt()
                _uiState.update { it.copy(saving = false, dismissedNotice = true) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(saving = false, error = "저장하지 못했어요. 잠시 후 다시 시도해 주세요.")
                }
            }
        }
    }

    /** 거절 안내를 은은히 보여준 뒤 화면이 부른다 — 카드를 완전히 닫는다(추가 서버 호출 없음). */
    fun closeAfterDismissNotice() {
        _uiState.update { it.copy(visible = false, dismissedNotice = false) }
    }
}

/**
 * 04 선호 난이도 제안 카드 상태.
 *  - [visible]=false면 카드를 렌더하지 않는다(애초에 대상이 아니었거나, 응답이 끝나 완전히 닫힌 상태).
 *  - [dismissedNotice]: 거절 저장이 성공한 직후 — 선택지 대신 "설정에서 언제든 바꿀 수 있어요" 안내만
 *    잠깐 보여준다. 화면이 일정 시간 뒤 [SessionCompleteViewModel.closeAfterDismissNotice]를 불러 닫는다.
 */
data class DifficultyPromptUiState(
    val visible: Boolean,
    val saving: Boolean = false,
    val error: String? = null,
    val dismissedNotice: Boolean = false,
)
