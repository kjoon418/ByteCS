package watson.bytecs.account

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import watson.bytecs.account.data.SettingsTokenStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AuthViewModel 검증. 검증 게이팅·모드 전환·성공(일회성 이벤트)/실패 전이·승계 배너 조건을 Fake 세션으로 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private suspend fun guestSession(repository: FakeAccountRepository = FakeAccountRepository()): SessionManager {
        val manager = SessionManager(repository, SettingsTokenStore(MapSettings()))
        manager.bootstrap() // Guest 상태 확보
        return manager
    }

    private suspend fun memberSession(): SessionManager {
        val manager = SessionManager(FakeAccountRepository(), SettingsTokenStore(MapSettings()))
        manager.login("a@b.com", "pw12345678") // Member 상태 확보
        return manager
    }

    private fun AuthViewModel.fillValid() {
        onEmailChange("a@b.com")
        onPasswordChange("pw12345678")
    }

    @Test
    fun invalidEmail_blocksSubmit() = runTest {
        val viewModel = AuthViewModel(guestSession())

        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("pw12345678")

        assertFalse(viewModel.uiState.value.canSubmit, "이메일 형식이 아니면 제출 불가")
        viewModel.submit()
        assertEquals(SubmitStatus.Idle, viewModel.uiState.value.status, "무효 입력 제출은 아무 전이도 만들지 않는다")
    }

    @Test
    fun validInput_enablesSubmit() = runTest {
        val viewModel = AuthViewModel(guestSession())
        viewModel.fillValid()
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    // ── 비밀번호 최소 8자: 서버 RawPassword와 같은 기준 ────────────────────────

    @Test
    fun shortPassword_blocksSubmit_withValidEmail() = runTest {
        // 이메일은 유효하게 두어 **비밀번호 규칙만** 게이팅을 결정하는지 본다
        // (합성 조건의 다른 절반이 잠금을 대신해 주지 못하게).
        val viewModel = AuthViewModel(guestSession())
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("pw1")

        assertFalse(viewModel.uiState.value.isPasswordValid, "3자는 서버가 거절하므로 무효")
        assertFalse(viewModel.uiState.value.canSubmit, "8자 미만이면 CTA 비활성")
    }

    @Test
    fun shortPassword_submitIsIgnored_andNeverReachesServer() = runTest {
        // 서버까지 가서 튕기는 게 아니라 화면에서 멈춰야 한다(사용자가 이유 없이 실패를 겪지 않게).
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, SettingsTokenStore(MapSettings()))
        manager.bootstrap()
        val viewModel = AuthViewModel(manager)
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("pw1")

        viewModel.submit()

        assertEquals(0, repository.calls.count { it == "login" }, "8자 미만은 서버로 보내지 않는다")
        assertEquals(SubmitStatus.Idle, viewModel.uiState.value.status, "미달 입력 제출은 아무 전이도 만들지 않는다")
    }

    @Test
    fun shortPassword_staysSilent_withoutFailureMessage() = runTest {
        // 무낙인: 입력 중 미완성을 경고로 낙인찍지 않는다. 안내는 침묵 + 비활성 CTA로만.
        val viewModel = AuthViewModel(guestSession())
        viewModel.onEmailChange("a@b.com")

        for (password in listOf("p", "pw1", "pw12345")) {
            viewModel.onPasswordChange(password)
            assertEquals(
                SubmitStatus.Idle,
                viewModel.uiState.value.status,
                "미달($password)은 실패 메시지를 만들지 않는다",
            )
        }
    }

    @Test
    fun passwordBoundary_sevenBlocked_eightAllowed() = runTest {
        // 경계값: 서버 RawPassword.MINIMUM_LENGTH(=8)와 정확히 같은 지점에서 갈린다.
        val viewModel = AuthViewModel(guestSession())
        viewModel.onEmailChange("a@b.com")

        viewModel.onPasswordChange("pw12345") // 7자
        assertFalse(viewModel.uiState.value.canSubmit, "7자는 서버가 거절하므로 막는다")

        viewModel.onPasswordChange("pw123456") // 8자
        assertTrue(viewModel.uiState.value.canSubmit, "정확히 8자는 서버가 받으므로 통과시킨다")
    }

    @Test
    fun longPassword_isNotBlockedByClient() = runTest {
        // 클라이언트가 서버보다 빡빡하면 정상 가입이 막힌다 — 상한을 두지 않는다.
        val viewModel = AuthViewModel(guestSession(), initialMode = AuthMode.Register)
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("p".repeat(64))

        assertTrue(viewModel.uiState.value.canSubmit, "서버가 받는 긴 비밀번호를 클라이언트가 막으면 안 된다")
    }

    @Test
    fun toggleMode_switchesBetweenLoginAndRegister() = runTest {
        val viewModel = AuthViewModel(guestSession())
        assertEquals(AuthMode.Login, viewModel.uiState.value.mode)

        viewModel.toggleMode()
        assertEquals(AuthMode.Register, viewModel.uiState.value.mode)

        viewModel.toggleMode()
        assertEquals(AuthMode.Login, viewModel.uiState.value.mode)
    }

    @Test
    fun initialModeRegister_startsInRegisterMode() = runTest {
        val viewModel = AuthViewModel(guestSession(), initialMode = AuthMode.Register)
        assertEquals(AuthMode.Register, viewModel.uiState.value.mode, "게스트 가입 CTA 진입은 가입 모드")
        assertTrue(viewModel.uiState.value.isGuestUpgrade, "게스트에서 왔으므로 승계 배너 조건 참")
    }

    // ── B: 성공은 상태가 아니라 일회성 이벤트 ──────────────────────────────────

    @Test
    fun loginSuccess_emitsExactlyOneEvent_andDoesNotStickAsStatus() = runTest {
        val viewModel = AuthViewModel(guestSession())
        viewModel.fillValid()

        viewModel.submit()

        assertEquals(AuthEvent.Succeeded, viewModel.events.first(), "성공은 이벤트로 한 번 방출")
        // 성공 후 상태는 Idle로 되돌아간다(눌러붙지 않음).
        assertEquals(SubmitStatus.Idle, viewModel.uiState.value.status)
        // 두 번째 이벤트는 없다.
        assertNull(withTimeoutOrNull(100) { viewModel.events.first() }, "성공 이벤트는 중복 방출되지 않는다")
    }

    @Test
    fun registerSuccess_emitsEvent() = runTest {
        val viewModel = AuthViewModel(guestSession(), initialMode = AuthMode.Register)
        viewModel.fillValid()

        viewModel.submit()

        assertEquals(AuthEvent.Succeeded, viewModel.events.first())
    }

    @Test
    fun reEntryAfterSuccess_doesNotAutoFire_andFlowIsReusable() = runTest {
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, SettingsTokenStore(MapSettings()))
        manager.bootstrap()
        val viewModel = AuthViewModel(manager)

        viewModel.fillValid()
        viewModel.submit()
        assertEquals(AuthEvent.Succeeded, viewModel.events.first())

        // 로그아웃 후 재진입: 잔류 성공 이벤트로 화면을 튕기지 않아야 한다.
        manager.logout()
        viewModel.resetForEntry(AuthMode.Login)
        assertNull(withTimeoutOrNull(100) { viewModel.events.first() }, "재진입 시 자동으로 성공 이벤트가 나오면 안 된다")

        // 다시 로그인 가능(재사용).
        viewModel.fillValid()
        viewModel.submit()
        assertEquals(AuthEvent.Succeeded, viewModel.events.first(), "이전 성공 이후에도 로그인 흐름은 재사용된다")
    }

    @Test
    fun rapidDoubleSubmit_callsRepositoryOnce() = runTest {
        // 지연 실행 디스패처로 첫 제출을 인플라이트로 붙잡아, 두 번째 제출이 무시되는지 본다.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, SettingsTokenStore(MapSettings()))
        manager.bootstrap()
        advanceUntilIdle()
        val viewModel = AuthViewModel(manager)
        viewModel.fillValid()

        viewModel.submit() // status=Submitting 동기 반영 후 코루틴 예약
        viewModel.submit() // 전송 중이므로 무시되어야 한다
        advanceUntilIdle()

        assertEquals(1, repository.calls.count { it == "login" }, "이중 제출은 한 번만 호출된다")
    }

    @Test
    fun resetForEntry_setsModeAndClearsFields() = runTest {
        val viewModel = AuthViewModel(guestSession())
        viewModel.onEmailChange("x@y.com")
        viewModel.onPasswordChange("secret12")

        viewModel.resetForEntry(AuthMode.Register)

        val state = viewModel.uiState.value
        assertEquals(AuthMode.Register, state.mode)
        assertEquals("", state.email, "진입 시 입력 초기화")
        assertEquals("", state.password)
    }

    // ── 실패는 비처벌·원인 비구분(로그인) ─────────────────────────────────────

    @Test
    fun loginFailure_yieldsFriendlyFailed_withoutDistinguishingCause() = runTest {
        val repository = FakeAccountRepository().apply { loginError = InvalidCredentialsException() }
        val manager = SessionManager(repository, SettingsTokenStore(MapSettings()))
        manager.bootstrap()
        val viewModel = AuthViewModel(manager)

        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("wrongpass")
        viewModel.submit()

        val status = viewModel.uiState.value.status
        assertTrue(status is SubmitStatus.Failed, "실패는 Failed로")
        assertEquals("이메일 또는 비밀번호를 다시 확인해 주세요.", status.message)
    }

    @Test
    fun registerDuplicateEmail_yieldsSpecificFriendlyMessage() = runTest {
        val repository = FakeAccountRepository().apply { registerError = EmailAlreadyInUseException() }
        val manager = SessionManager(repository, SettingsTokenStore(MapSettings()))
        manager.bootstrap()
        val viewModel = AuthViewModel(manager, initialMode = AuthMode.Register)

        viewModel.fillValid()
        viewModel.submit()

        val status = viewModel.uiState.value.status
        assertTrue(status is SubmitStatus.Failed)
        assertTrue(status.message.contains("이미 사용 중인 이메일"), "중복 이메일은 구체적으로 안내")
    }

    @Test
    fun editingInput_clearsPriorFailure() = runTest {
        val repository = FakeAccountRepository().apply { loginError = InvalidCredentialsException() }
        val manager = SessionManager(repository, SettingsTokenStore(MapSettings()))
        manager.bootstrap()
        val viewModel = AuthViewModel(manager)

        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("wrongpass")
        viewModel.submit()
        assertTrue(viewModel.uiState.value.status is SubmitStatus.Failed)

        viewModel.onPasswordChange("newpass123")
        assertEquals(SubmitStatus.Idle, viewModel.uiState.value.status, "입력을 고치면 실패 표시가 지워진다")
    }

    @Test
    fun guestSession_setsGuestUpgradeFlag() = runTest {
        val viewModel = AuthViewModel(guestSession())
        assertTrue(viewModel.uiState.value.isGuestUpgrade, "게스트에서 진입하면 승계 배너 조건이 참")
    }

    @Test
    fun memberSession_doesNotSetGuestUpgradeFlag() = runTest {
        val viewModel = AuthViewModel(memberSession())
        assertFalse(viewModel.uiState.value.isGuestUpgrade, "회원 상태면 승계 배너 조건은 거짓")
    }
}
