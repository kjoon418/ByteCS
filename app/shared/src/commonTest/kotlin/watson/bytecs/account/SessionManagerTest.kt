package watson.bytecs.account

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import watson.bytecs.account.data.SettingsTokenStore
import watson.bytecs.account.data.TokenStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SessionManager의 인증 상태 전이를 인메모리 저장소·토큰 스토어로 검증한다.
 * 판정·발급은 Fake가 결정적으로 재현하므로 네트워크 없이 상태 로직만 고정한다.
 */
class SessionManagerTest {

    private fun store(initialToken: String? = null): TokenStore =
        SettingsTokenStore(MapSettings()).apply { initialToken?.let { set(it) } }

    @Test
    fun bootstrap_withNoToken_issuesAndStoresGuest() = runTest {
        val tokenStore = store()
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, tokenStore)

        manager.bootstrap()

        assertNotNull(tokenStore.get(), "게스트 발급 토큰이 저장돼야 한다")
        assertTrue(manager.state.value is AuthState.Guest, "발급 후 Guest 상태")
        assertTrue("issueGuest" in repository.calls)
    }

    @Test
    fun bootstrap_withExistingMemberToken_restoresMember() = runTest {
        val tokenStore = store(initialToken = "member-jwt")
        val repository = FakeAccountRepository(
            current = Account(userId = 7, role = Role.MEMBER, email = "a@b.com", dailySessionSize = 9),
        )
        val manager = SessionManager(repository, tokenStore)

        manager.bootstrap()

        val state = manager.state.value
        assertTrue(state is AuthState.Member, "회원 토큰이면 Member로 복원")
        assertEquals("a@b.com", state.account?.email)
        // 발급을 다시 하지 않는다.
        assertTrue("issueGuest" !in repository.calls)
    }

    @Test
    fun bootstrap_withStaleToken_clearsAndReissuesGuest() = runTest {
        val tokenStore = store(initialToken = "stale-jwt")
        val repository = FakeAccountRepository().apply { getMeError = RuntimeException("401") }
        val manager = SessionManager(repository, tokenStore)

        manager.bootstrap()

        // 손상·만료 토큰: getMe 실패 → 정리 후 게스트 재발급.
        assertTrue("issueGuest" in repository.calls, "실패 시 게스트를 재발급해야 한다")
        assertNotNull(tokenStore.get())
    }

    @Test
    fun register_promotesInPlace_andStoresMemberToken() = runTest {
        val tokenStore = store()
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, tokenStore)
        manager.bootstrap() // 게스트 상태 확보
        val guestToken = tokenStore.get()
        val guestUserId = (manager.state.value as AuthState.Guest).account?.userId

        // getMe가 승격 결과를 반영하도록 오류를 지운다(기본은 없음).
        manager.register("a@b.com", "pw12345678")

        val state = manager.state.value
        assertTrue(state is AuthState.Member, "가입 후 Member 상태")
        assertEquals(guestUserId, state.account?.userId, "제자리 승격이라 userId가 유지된다")
        assertEquals("member-$guestUserId", tokenStore.get(), "회원 토큰으로 교체·저장")
        assertTrue(tokenStore.get() != guestToken, "토큰이 교체돼야 한다")
    }

    @Test
    fun login_storesToken_andSetsMember() = runTest {
        val tokenStore = store()
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, tokenStore)

        manager.login("a@b.com", "pw12345678")

        assertNotNull(tokenStore.get())
        assertTrue(manager.state.value is AuthState.Member)
    }

    @Test
    fun logout_clearsToken_andReissuesGuest() = runTest {
        val tokenStore = store()
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, tokenStore)
        manager.login("a@b.com", "pw12345678")
        val memberToken = tokenStore.get()

        manager.logout()

        assertTrue(manager.state.value is AuthState.Guest, "로그아웃 후 새 게스트")
        assertNotNull(tokenStore.get())
        assertTrue(tokenStore.get() != memberToken, "새 게스트 토큰으로 교체")
    }

    @Test
    fun deleteAccount_deletesClearsAndReissuesGuest() = runTest {
        val tokenStore = store()
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, tokenStore)
        manager.login("a@b.com", "pw12345678")

        manager.deleteAccount()

        assertEquals(1, repository.deleteMeCount, "삭제가 서버에 전달돼야 한다")
        assertTrue(manager.state.value is AuthState.Guest, "삭제 후 새 게스트로 복구")
        assertNotNull(tokenStore.get())
    }

    @Test
    fun updateSettings_refreshesMemberState() = runTest {
        val tokenStore = store()
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, tokenStore)
        manager.login("a@b.com", "pw12345678")

        manager.updateSettings(20)

        val state = manager.state.value
        assertTrue(state is AuthState.Member)
        assertEquals(20, state.account?.dailySessionSize)
    }

    @Test
    fun updatePreferredDifficulty_refreshesMemberState() = runTest {
        val tokenStore = store()
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, tokenStore)
        manager.login("a@b.com", "pw12345678")

        manager.updatePreferredDifficulty(PreferredDifficulty.HARD)

        val state = manager.state.value
        assertTrue(state is AuthState.Member)
        assertEquals(PreferredDifficulty.HARD, state.account?.preferredDifficulty)
    }

    @Test
    fun resetPreferredDifficulty_refreshesStateToAutomatic() = runTest {
        val tokenStore = store()
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, tokenStore)
        manager.login("a@b.com", "pw12345678")
        manager.updatePreferredDifficulty(PreferredDifficulty.HARD)

        manager.resetPreferredDifficulty()

        assertEquals(1, repository.resetPreferredDifficultyCount)
        val state = manager.state.value
        assertTrue(state is AuthState.Member)
        assertNull(state.account?.preferredDifficulty, "리셋 후 상태는 미설정(자동)으로 갱신된다")
    }

    @Test
    fun dismissDifficultyPrompt_refreshesMemberState() = runTest {
        val tokenStore = store()
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, tokenStore)
        manager.login("a@b.com", "pw12345678")

        manager.dismissDifficultyPrompt()

        assertEquals(1, repository.dismissDifficultyPromptCount)
        assertTrue(manager.state.value is AuthState.Member, "거절 후에도 계정 상태는 유지된다")
    }

    // ── A: 오프라인 게스트 발급 실패는 막다른 길이 아니라 복구 가능한 BootstrapFailed ─────

    @Test
    fun bootstrap_offline_yieldsBootstrapFailed_thenRetryRecovers() = runTest {
        val tokenStore = store()
        val repository = FakeAccountRepository().apply { issueGuestError = RuntimeException("offline") }
        val manager = SessionManager(repository, tokenStore)

        manager.bootstrap()

        assertEquals(AuthState.BootstrapFailed, manager.state.value, "게스트 발급 실패는 BootstrapFailed")
        assertNull(tokenStore.get(), "발급 실패 시 토큰은 저장되지 않는다")

        // 네트워크 회복 후 재시도 → 정상 게스트.
        repository.issueGuestError = null
        manager.retry()

        assertTrue(manager.state.value is AuthState.Guest, "재시도로 복구된다")
        assertNotNull(tokenStore.get())
    }

    // ── C: 토큰 저장이 성공 경계 — 이후 getMe 실패는 인증 실패가 아니라 프로필 미상 Member ──

    @Test
    fun register_getMeFailsAfterTokenStored_staysProvisionalMember_tokenRetained() = runTest {
        val tokenStore = store()
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, tokenStore)
        manager.bootstrap() // 게스트 확보

        // 가입은 성공(토큰 저장)하되 직후 프로필 조회만 실패하도록.
        repository.getMeError = RuntimeException("profile service down")
        manager.register("a@b.com", "pw12345678")

        val state = manager.state.value
        assertTrue(state is AuthState.Member, "토큰 저장이 성공 경계 — Member로 전진(게스트로 되돌리지 않음)")
        assertNull(state.account, "프로필은 아직 미상")
        assertTrue(state.profileError, "프로필 미로딩 플래그")
        assertTrue(tokenStore.get()?.startsWith("member-") == true, "회원 토큰은 유지된다")
    }

    // ── D: 로그아웃/삭제 후 게스트 재발급 실패도 Loading에 고착되지 않는다 ───────────────

    @Test
    fun deleteAccount_deletesButGuestReissueFails_yieldsBootstrapFailed_tokenCleared() = runTest {
        val tokenStore = store()
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, tokenStore)
        manager.login("a@b.com", "pw12345678")

        repository.issueGuestError = RuntimeException("offline")
        manager.deleteAccount()

        assertEquals(1, repository.deleteMeCount, "삭제 자체는 성공적으로 전달")
        assertEquals(AuthState.BootstrapFailed, manager.state.value, "재발급 실패는 BootstrapFailed")
        assertNull(tokenStore.get(), "삭제 후 토큰은 정리된다")

        // 회복 후 재시도 → 게스트.
        repository.issueGuestError = null
        manager.retry()
        assertTrue(manager.state.value is AuthState.Guest)
        assertNotNull(tokenStore.get())
    }

    @Test
    fun logout_guestReissueFails_yieldsBootstrapFailed_tokenCleared() = runTest {
        val tokenStore = store()
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, tokenStore)
        manager.login("a@b.com", "pw12345678")

        repository.issueGuestError = RuntimeException("offline")
        manager.logout()

        assertEquals(AuthState.BootstrapFailed, manager.state.value)
        assertNull(tokenStore.get(), "로그아웃은 토큰을 지운다(재발급 실패와 무관)")
    }
}
