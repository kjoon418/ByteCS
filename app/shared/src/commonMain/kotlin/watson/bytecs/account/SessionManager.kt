package watson.bytecs.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import watson.bytecs.account.data.TokenStore
import kotlin.coroutines.cancellation.CancellationException

/**
 * 앱 전역 인증 상태의 단일 출처. 토큰 영속([TokenStore])과 서버([AccountRepository])를 오케스트레이션해
 * 게스트/회원 상태를 [state]로 노출한다.
 *
 * ⭐️ 무낙인·저마찰: 게스트도 즉시 학습할 수 있도록 최초 진입에 게스트를 자동 발급하고, 가입은 강요하지 않는다.
 * 가입은 **제자리 승격**(같은 userId 유지)이라 게스트로 쌓은 기록이 그대로 이어진다.
 *
 * ⭐️ 막다른 길 금지: 게스트 발급이 오프라인 등으로 실패하면 Loading에 영구 고착되지 않도록
 * [AuthState.BootstrapFailed]로 떨어뜨리고, [retry]로 언제든 복구할 수 있게 한다.
 */
class SessionManager(
    private val repository: AccountRepository,
    private val tokenStore: TokenStore,
) {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /**
     * 앱 시작 시 인증 상태를 복원한다.
     *  - 토큰 없음 → 게스트 발급·저장 후 Guest(발급 실패 시 BootstrapFailed).
     *  - 토큰 있음 → getMe로 신원 확인(역할에 따라 Guest/Member). 만료·손상 토큰이면 정리 후 게스트 재발급.
     */
    suspend fun bootstrap() {
        _state.value = AuthState.Loading
        if (tokenStore.get() == null) {
            issueFreshGuest()
            return
        }
        try {
            _state.value = repository.getMe().toState()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            // 만료·손상 토큰(예: 401): 막다른 길을 만들지 않도록 조용히 정리하고 새 게스트로 복구한다.
            tokenStore.clear()
            issueFreshGuest()
        }
    }

    /** BootstrapFailed 등에서 다시 시도한다. 저장된 토큰 유무에 따라 재발급/재확인을 자동 선택한다. */
    suspend fun retry() = bootstrap()

    /**
     * 회원 가입. 현재 게스트 토큰이 클라이언트 헤더에 실려 서버가 제자리 승격하고, 새 회원 토큰을 저장한다.
     * 토큰 저장이 성공 경계다: 저장 후 getMe가 실패해도 이미 유효한 회원 토큰을 가졌으므로 인증 실패로 되돌리지 않고
     * 프로필 미상 Member로 **전진**시킨다(다음 진입/설정 저장 때 프로필이 채워진다).
     * 저장 전 실패([EmailAlreadyInUseException] 등)만 그대로 전파해 뷰모델이 친절히 안내한다.
     */
    suspend fun register(email: String, password: String) {
        val session = repository.register(email, password)
        tokenStore.set(session.token)
        _state.value = loadMemberOrProvisional()
    }

    /** 로그인. 토큰 저장을 성공 경계로 삼는다(가입과 동일 규칙). 저장 전 실패(401 등)만 전파한다. */
    suspend fun login(email: String, password: String) {
        val session = repository.login(email, password)
        tokenStore.set(session.token)
        _state.value = loadMemberOrProvisional()
    }

    /** 학습 설정(세션 크기)을 변경하고, 갱신된 프로필로 상태를 다시 그린다. */
    suspend fun updateSettings(dailySessionSize: Int) {
        val account = repository.updateSettings(dailySessionSize)
        _state.value = account.toState()
    }

    /** 선호 난이도를 변경하고, 갱신된 프로필로 상태를 다시 그린다. */
    suspend fun updatePreferredDifficulty(value: PreferredDifficulty) {
        val account = repository.updatePreferredDifficulty(value)
        _state.value = account.toState()
    }

    /** 선호 난이도를 미설정(자동)으로 되돌리고, 갱신된 프로필로 상태를 다시 그린다. */
    suspend fun resetPreferredDifficulty() {
        val account = repository.resetPreferredDifficulty()
        _state.value = account.toState()
    }

    /** 세션 완료 화면의 난이도 제안 거절을 기록하고, 갱신된 프로필로 상태를 다시 그린다. */
    suspend fun dismissDifficultyPrompt() {
        val account = repository.dismissDifficultyPrompt()
        _state.value = account.toState()
    }

    /** 로그아웃. 토큰을 지우고 새 게스트를 발급해 학습을 이어갈 수 있게 한다(발급 실패 시 BootstrapFailed). */
    suspend fun logout() {
        tokenStore.clear()
        _state.value = AuthState.Loading
        issueFreshGuest()
    }

    /**
     * 계정 삭제. 서버에서 지운 뒤 토큰을 정리하고 새 게스트로 복구한다.
     * [AccountRepository.deleteMe] 자체가 실패하면 토큰·계정을 보존한 채 예외를 전파한다(삭제되지 않았으므로).
     * 삭제 성공 후 게스트 재발급만 실패하면 BootstrapFailed(토큰은 이미 정리됨, 계정은 이미 삭제됨).
     */
    suspend fun deleteAccount() {
        repository.deleteMe()
        tokenStore.clear()
        _state.value = AuthState.Loading
        issueFreshGuest()
    }

    /**
     * 게스트를 발급·저장하고 Guest 상태로 만든다.
     * 발급 자체가 실패하면(토큰 미저장) [AuthState.BootstrapFailed]로 떨어뜨려 재시도 경로를 남긴다.
     * 발급은 됐지만 프로필 조회만 실패하면(토큰은 유효) 프로필 미상 Guest로 둔다.
     */
    private suspend fun issueFreshGuest() {
        val guest = try {
            repository.issueGuest()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            _state.value = AuthState.BootstrapFailed
            return
        }
        tokenStore.set(guest.token)
        _state.value = try {
            repository.getMe().toState()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            // 조회 실패해도 인증 자체는 성립하므로, 프로필 미상 게스트로 둔다(다음 진입에 재조회).
            AuthState.Guest(account = null)
        }
    }

    /** 토큰 저장 후 프로필 조회. 실패해도 인증은 성립하므로 프로필 미상 Member로 전진한다. */
    private suspend fun loadMemberOrProvisional(): AuthState = try {
        repository.getMe().toState()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        AuthState.Member(account = null, profileError = true)
    }

    private fun Account.toState(): AuthState =
        if (isMember) AuthState.Member(this) else AuthState.Guest(this)
}

/**
 * 앱 전역 인증 상태.
 *  - [Loading]: 부트스트랩·전환 중.
 *  - [BootstrapFailed]: 게스트 발급 실패(오프라인 등). [SessionManager.retry]로 복구 가능.
 *  - [Guest]: 게스트로 이용 중. 프로필([account])은 조회 실패 시 null일 수 있다.
 *  - [Member]: 가입·로그인한 회원. 토큰은 유효하나 프로필 조회가 실패하면 [account]=null·[profileError]=true.
 */
sealed interface AuthState {
    data object Loading : AuthState
    data object BootstrapFailed : AuthState
    data class Guest(val account: Account?) : AuthState
    data class Member(val account: Account?, val profileError: Boolean = false) : AuthState
}
