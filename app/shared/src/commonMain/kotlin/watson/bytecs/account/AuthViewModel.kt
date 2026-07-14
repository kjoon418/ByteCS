package watson.bytecs.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 05 로그인·가입 화면의 상태 홀더. 같은 화면에서 로그인↔가입을 전환하고, 제출을 [SessionManager]에 위임한다.
 *
 * ⭐️ 무낙인·안심 원칙:
 *  - 실패는 처벌이 아니다. 시스템 오류·검증 실패 모두 사용자 언어로 안내하고 "학습 기록은 안전해요"를 먼저 고지한다.
 *  - 로그인 실패는 이메일 없음/비밀번호 불일치를 **구분하지 않는다**(계정 열거 방지).
 *  - 가입은 강요하지 않는다(화면에 "나중에 하기" 경로 유지). 게스트에서 왔으면 승계 배너를 노출한다.
 *
 * ⭐️ 성공은 **일회성 이벤트**([events])로 알린다. 뷰모델이 내비게이션 간 재사용(앱 수명 싱글턴)되어도
 * "성공" 상태가 눌러붙어 재진입 시 화면을 자동으로 튕겨내지 않게 한다(로그아웃→재로그인 재사용 가능).
 */
class AuthViewModel(
    private val sessionManager: SessionManager,
    initialMode: AuthMode = AuthMode.Login,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        // 게스트 상태에서 진입했으면 가입=승격이므로 승계 안내 배너를 켠다.
        AuthUiState(mode = initialMode, isGuestUpgrade = sessionManager.state.value is AuthState.Guest),
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // 성공 알림용 일회성 이벤트 채널. 화면이 collect해 정확히 한 번 복귀 처리한다(replay 없음).
    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    init {
        // 승계 배너 조건을 라이브로 따라간다(뷰모델이 내비게이션 간 재사용되어도 최신 게스트/회원 상태를 반영).
        viewModelScope.launch {
            sessionManager.state.collect { auth ->
                _uiState.update { it.copy(isGuestUpgrade = auth is AuthState.Guest) }
            }
        }
    }

    /**
     * 화면 진입 시 폼을 초기화한다(재사용되는 뷰모델의 이전 입력·실패 표시를 지우고, 진입 모드를 지정).
     * 성공 이벤트는 채널이라 잔류하지 않으므로 여기서 따로 지울 것이 없다.
     */
    fun resetForEntry(mode: AuthMode) {
        _uiState.value = AuthUiState(
            mode = mode,
            isGuestUpgrade = sessionManager.state.value is AuthState.Guest,
        )
    }

    /** 로그인↔가입 전환. 입력은 유지하고, 직전 제출 실패 표시만 지운다(깨끗한 재시작). */
    fun toggleMode() {
        _uiState.update { state ->
            val next = if (state.mode == AuthMode.Login) AuthMode.Register else AuthMode.Login
            state.copy(mode = next, status = SubmitStatus.Idle)
        }
    }

    /** 이메일 입력 변경. 입력을 고치는 순간 직전 실패 표시를 지운다. */
    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, status = it.status.clearedOnEdit()) }
    }

    /** 비밀번호 입력 변경. 입력을 고치는 순간 직전 실패 표시를 지운다. */
    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, status = it.status.clearedOnEdit()) }
    }

    /**
     * 제출. 검증 통과 시 모드에 따라 로그인/가입을 수행한다.
     * 이중 제출 가드: 이미 전송 중이면 무시한다. 성공은 [events]로 한 번만 알린다.
     */
    fun submit() {
        val current = _uiState.value
        if (!current.canSubmit) return

        val email = current.email.trim()
        val password = current.password
        _uiState.update { it.copy(status = SubmitStatus.Submitting) }

        viewModelScope.launch {
            try {
                when (current.mode) {
                    AuthMode.Login -> sessionManager.login(email, password)
                    AuthMode.Register -> sessionManager.register(email, password)
                }
                // 버튼 상태만 원위치(성공은 눌러붙지 않게)하고, 복귀는 일회성 이벤트로.
                _uiState.update { it.copy(status = SubmitStatus.Idle) }
                _events.send(AuthEvent.Succeeded)
            } catch (cancellation: CancellationException) {
                throw cancellation // 취소는 실패가 아니므로 그대로 전파.
            } catch (error: Throwable) {
                _uiState.update { it.copy(status = SubmitStatus.Failed(friendlyMessage(error))) }
            }
        }
    }

    /**
     * 실패 원인을 사용자 언어로 번역한다. 도메인 예외(중복 이메일·자격 불일치)는 구체 안내로,
     * 그 밖(네트워크·서버 오류)은 일반 안내로. 어느 경우도 비난하지 않는다.
     */
    private fun friendlyMessage(error: Throwable): String = when (error) {
        is EmailAlreadyInUseException -> "이미 사용 중인 이메일이에요. 로그인으로 이어가 볼까요?"
        is InvalidCredentialsException -> "이메일 또는 비밀번호를 다시 확인해 주세요."
        else -> "잠시 연결이 원활하지 않았어요. 다시 시도해 주세요."
    }
}

/** 로그인/가입 모드. 같은 화면에서 토글된다. */
enum class AuthMode { Login, Register }

/** 화면이 소비하는 일회성 이벤트. */
sealed interface AuthEvent {
    /** 로그인·가입 성공(승계 완료). 화면은 정확히 한 번 이전 맥락으로 복귀한다. */
    data object Succeeded : AuthEvent
}

/**
 * 05 화면 상태. 폼 입력은 상태 전이와 무관하게 유지되도록 한 데이터 클래스에 모은다.
 * 성공은 상태가 아니라 [AuthViewModel.events]로 다룬다(눌러붙지 않게).
 */
data class AuthUiState(
    val mode: AuthMode,
    val email: String = "",
    val password: String = "",
    val isGuestUpgrade: Boolean = false,
    val status: SubmitStatus = SubmitStatus.Idle,
) {
    /** 이메일 형식 최소 검증(공백 아님 + @ 포함). 상세 규칙은 서버가 강제한다(클라이언트는 가벼운 가드만). */
    val isEmailValid: Boolean
        get() = email.trim().let { it.isNotEmpty() && it.contains('@') && !it.startsWith('@') && !it.endsWith('@') }

    /** 비밀번호 최소 검증(공백 아님). */
    val isPasswordValid: Boolean get() = password.isNotBlank()

    /** 제출 가능 조건: 입력 유효 + 전송 중 아님. */
    val canSubmit: Boolean get() = isEmailValid && isPasswordValid && status != SubmitStatus.Submitting
}

/** 제출 진행 상태(성공 제외 — 성공은 일회성 이벤트). */
sealed interface SubmitStatus {
    data object Idle : SubmitStatus
    data object Submitting : SubmitStatus

    /** 실패(친절 메시지). 처벌 아님. */
    data class Failed(val message: String) : SubmitStatus

    /** 입력 편집 시 실패 표시만 지우고, 진행 상태는 유지한다. */
    fun clearedOnEdit(): SubmitStatus = if (this is Failed) Idle else this
}
