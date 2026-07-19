package watson.bytecs.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.bytecs.ui.theme.ThemeController
import watson.bytecs.ui.theme.ThemeMode
import kotlin.coroutines.cancellation.CancellationException

/**
 * 06 계정·설정 화면의 상태 홀더. 인증 상태([SessionManager])와 테마 선택([ThemeController])을 합쳐
 * 화면 상태를 만들고, 설정 변경·로그아웃·계정 삭제를 조율한다.
 *
 * ⭐️ 무낙인·데이터 통제권:
 *  - 게스트는 로그아웃/프로필 대신 가입 유도 CTA를 본다.
 *  - 계정 삭제는 화면에서 danger가 허용되는 **유일한** 지점이며, 무엇이 사라지는지 명시하는 확인 단계를 강제한다.
 *  - 삭제 문구는 공포 연출 없이 사실 중심.
 */
class AccountViewModel(
    private val sessionManager: SessionManager,
    private val themeController: ThemeController,
) : ViewModel() {

    // 편집 중 값·전송 상태·삭제 단계 등 서버 상태와 무관한 화면 전용 상태.
    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<AccountUiState> =
        combine(sessionManager.state, themeController.mode, transient) { auth, theme, local ->
            buildState(auth, theme, local)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildState(sessionManager.state.value, themeController.mode.value, transient.value),
        )

    /** 화면 진입 시 화면 전용 상태를 초기화한다(재사용되는 뷰모델의 이전 삭제 단계·편집값 잔류 제거). */
    fun resetForEntry() {
        transient.value = Transient()
    }

    /** 화면 테마 선택. 즉시 반영·영속된다. */
    fun setThemeMode(mode: ThemeMode) {
        themeController.setMode(mode)
    }

    /**
     * 세션 크기 편집. 1..50 밖이면 저장을 막고 사실 기반 안내를 남긴다(입력값 자체는 유지).
     */
    fun onSessionSizeChange(value: Int) {
        transient.update {
            it.copy(
                sessionSizeDraft = value,
                sessionSizeError = if (value in MIN_SESSION_SIZE..MAX_SESSION_SIZE) null
                else "세션 크기는 ${MIN_SESSION_SIZE}~${MAX_SESSION_SIZE}문제 사이로 정할 수 있어요.",
                noticeError = null,
            )
        }
    }

    /** 변경한 세션 크기를 저장(PATCH). 범위 밖·전송 중·변경 없음(=서버 값과 동일)이면 아무 일도 하지 않는다. */
    fun saveSettings() {
        val local = transient.value
        val draft = local.sessionSizeDraft ?: return
        if (local.sessionSizeError != null || local.settingsSaving) return

        // ⭐️ 서버 값과 같으면 불필요한 PATCH를 보내지 않는다(+1 후 −1로 되돌린 경우 등).
        val serverSize = (sessionManager.state.value as? AuthState.Member)?.account?.dailySessionSize
        if (draft == serverSize) {
            transient.update { it.copy(sessionSizeDraft = null) }
            return
        }

        transient.update { it.copy(settingsSaving = true, noticeError = null) }
        viewModelScope.launch {
            try {
                sessionManager.updateSettings(draft)
                // 성공: draft를 비워 서버 값(갱신됨)을 따르게 한다.
                transient.update { it.copy(sessionSizeDraft = null, settingsSaving = false) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                transient.update {
                    it.copy(settingsSaving = false, noticeError = "설정을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.")
                }
            }
        }
    }

    /**
     * 로그아웃. 완료되면 SessionManager가 새 게스트로 상태를 바꾼다("다시 로그인하면 기록이 그대로예요").
     * ⭐️ 삭제 확인 카드가 열려 있는 동안에는 로그아웃이 끼어들지 못하게 막는다(상충 방지).
     */
    fun logout() {
        val local = transient.value
        if (local.busy || local.deletePhase == DeletePhase.Confirming) return
        transient.update { it.copy(loggingOut = true, noticeError = null) }
        viewModelScope.launch {
            try {
                sessionManager.logout()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                transient.update { it.copy(noticeError = "로그아웃하지 못했어요. 잠시 후 다시 시도해 주세요.") }
            } finally {
                transient.update { it.copy(loggingOut = false) }
            }
        }
    }

    /** 계정 삭제 확인 단계 진입. 아직 삭제하지 않는다(무엇이 사라지는지 고지 후 명시적 확인 필요). */
    fun requestDelete() {
        transient.update { it.copy(deletePhase = DeletePhase.Confirming, deleteError = null) }
    }

    /** 삭제 확인 취소. 아무 데이터도 건드리지 않는다. */
    fun cancelDelete() {
        transient.update { it.copy(deletePhase = DeletePhase.None) }
    }

    /**
     * 삭제 확인 완료 → 실제 삭제. 성공 시 [onDeleted] 콜백으로 화면 전환을 알린다(→ 온보딩/문제 화면).
     * SessionManager가 삭제 후 새 게스트를 발급하므로, 앱은 막다른 길 없이 계속 이용 가능하다.
     */
    fun confirmDelete(onDeleted: () -> Unit) {
        val local = transient.value
        if (local.deletePhase != DeletePhase.Confirming || local.busy) return

        transient.update { it.copy(deletePhase = DeletePhase.Deleting, deleteError = null) }
        viewModelScope.launch {
            try {
                sessionManager.deleteAccount()
                transient.update { it.copy(deletePhase = DeletePhase.None) }
                onDeleted()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                transient.update {
                    it.copy(
                        deletePhase = DeletePhase.Confirming,
                        deleteError = "계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.",
                    )
                }
            }
        }
    }

    private fun buildState(auth: AuthState, theme: ThemeMode, local: Transient): AccountUiState {
        val account = when (auth) {
            is AuthState.Member -> auth.account
            is AuthState.Guest -> auth.account
            AuthState.Loading, AuthState.BootstrapFailed -> null
        }
        val sessionSize = local.sessionSizeDraft ?: account?.dailySessionSize ?: DEFAULT_SESSION_SIZE
        return AccountUiState(
            isLoading = auth is AuthState.Loading,
            isMember = auth is AuthState.Member,
            email = account?.email,
            // 토큰은 유효하나 프로필 조회가 실패한 회원: 인증 실패가 아니라 가벼운 안내만.
            profileError = (auth as? AuthState.Member)?.profileError == true,
            sessionSize = sessionSize,
            sessionSizeError = local.sessionSizeError,
            // ⭐️ 변경 여부는 서버 값과 비교한다(서버 값으로 되돌리면 dirty 아님 → 불필요한 저장 방지).
            isSettingsDirty = local.sessionSizeDraft != null &&
                local.sessionSizeDraft != account?.dailySessionSize &&
                local.sessionSizeError == null,
            isSettingsSaving = local.settingsSaving,
            themeMode = theme,
            deletePhase = local.deletePhase,
            isLoggingOut = local.loggingOut,
            noticeError = local.noticeError,
            deleteError = local.deleteError,
        )
    }

    private data class Transient(
        val sessionSizeDraft: Int? = null,
        val sessionSizeError: String? = null,
        val settingsSaving: Boolean = false,
        val loggingOut: Boolean = false,
        val deletePhase: DeletePhase = DeletePhase.None,
        // 오류 채널 분리: 설정 저장·로그아웃은 [noticeError], 계정 삭제는 [deleteError]로 나눠
        // 서로 다른 액션의 메시지가 섞이지 않게 한다.
        val noticeError: String? = null,
        val deleteError: String? = null,
    ) {
        /** 로그아웃·삭제전송 등 되돌릴 수 없는 작업이 진행 중인지. 중복 트리거를 막는 가드에 쓴다. */
        val busy: Boolean get() = loggingOut || deletePhase == DeletePhase.Deleting
    }

    companion object {
        // 서버 도메인 VO(UserSettings)의 MINIMUM..MAXIMUM과 동기화된 값.
        const val MIN_SESSION_SIZE = 1
        const val MAX_SESSION_SIZE = 50

        /**
         * 계정 정보를 아직 못 불러왔을 때 보여줄 세션 크기. 도메인 명세 [결정] 기본값이자
         * 서버 `UserSettings.DEFAULT_DAILY_SESSION_SIZE`와 같은 5 — 값이 어긋나면 계정이 도착하는 순간
         * 숫자가 튄다.
         */
        const val DEFAULT_SESSION_SIZE = 5
    }
}

/**
 * 06 화면 상태.
 *  - 게스트: [isMember]=false → 로그아웃/프로필 대신 가입 CTA를 노출한다.
 *  - 회원: 이메일·로그아웃·계정 삭제. [profileError]면 프로필만 못 불러온 회원(인증은 유효).
 */
data class AccountUiState(
    val isLoading: Boolean,
    val isMember: Boolean,
    val email: String?,
    val profileError: Boolean,
    val sessionSize: Int,
    val sessionSizeError: String?,
    val isSettingsDirty: Boolean,
    val isSettingsSaving: Boolean,
    val themeMode: ThemeMode,
    val deletePhase: DeletePhase,
    val isLoggingOut: Boolean,
    /** 설정 저장·로그아웃 등 일반 액션의 오류(비처벌). 삭제 카드에 새지 않도록 [deleteError]와 분리. */
    val noticeError: String?,
    /** 계정 삭제 확인 카드 안에서만 보이는 오류. */
    val deleteError: String?,
)

/** 계정 삭제 단계. 실수 방지를 위해 확인 단계를 강제한다. */
enum class DeletePhase {
    /** 평상시. */
    None,

    /** 무엇이 사라지는지 고지하고 명시적 확인을 기다리는 중. */
    Confirming,

    /** 삭제 요청 전송 중. */
    Deleting,
}
