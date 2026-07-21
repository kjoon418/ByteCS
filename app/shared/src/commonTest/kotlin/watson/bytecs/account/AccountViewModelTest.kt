package watson.bytecs.account

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.bytecs.account.data.SettingsTokenStore
import watson.bytecs.ui.theme.ThemeController
import watson.bytecs.ui.theme.ThemeMode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AccountViewModel 검증. 로드·설정 변경(정상/범위 밖)·삭제 2단계·로그아웃·테마 전환을 Fake로 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun themeController() = ThemeController(MapSettings())

    private suspend fun memberViewModel(
        repository: FakeAccountRepository = FakeAccountRepository(),
    ): Pair<AccountViewModel, FakeAccountRepository> {
        val manager = SessionManager(repository, SettingsTokenStore(MapSettings()))
        manager.login("a@b.com", "pw12345678")
        return AccountViewModel(manager, themeController()) to repository
    }

    @Test
    fun load_member_showsEmailAndSize() = runTest {
        val (viewModel, _) = memberViewModel()

        val state = viewModel.uiState.value
        assertTrue(state.isMember)
        assertEquals("a@b.com", state.email)
        assertEquals(5, state.sessionSize, "회원 화면은 서버가 준 세션 크기를 그대로 보여준다")
    }

    @Test
    fun defaultSessionSize_beforeAccountLoads_matchesSpecDecision() = runTest {
        // bootstrap 전이라 계정이 없다(AuthState.Loading) → 화면은 기본값으로 되돌아간다.
        val manager = SessionManager(FakeAccountRepository(), SettingsTokenStore(MapSettings()))
        val viewModel = AccountViewModel(manager, themeController())

        // 도메인 명세 [결정]: 세션 크기 기본값은 5(서버 UserSettings.DEFAULT_DAILY_SESSION_SIZE와 동일).
        assertEquals(5, viewModel.uiState.value.sessionSize, "계정 도착 전 기본 세션 크기는 명세 결정값 5")
        assertEquals(5, AccountViewModel.DEFAULT_SESSION_SIZE)
    }

    @Test
    fun load_guest_isNotMember() = runTest {
        val manager = SessionManager(FakeAccountRepository(), SettingsTokenStore(MapSettings()))
        manager.bootstrap()
        val viewModel = AccountViewModel(manager, themeController())

        assertFalse(viewModel.uiState.value.isMember, "게스트는 회원이 아니다")
    }

    @Test
    fun settingsUpdate_happyPath_patchesAndSyncs() = runTest {
        val (viewModel, repository) = memberViewModel()

        viewModel.onSessionSizeChange(12)
        assertTrue(viewModel.uiState.value.isSettingsDirty, "변경 시 저장 가능")
        assertNull(viewModel.uiState.value.sessionSizeError)

        viewModel.saveSettings()

        assertEquals(12, repository.lastUpdatedSize, "서버에 새 세션 크기가 전달돼야 한다")
        assertEquals(12, viewModel.uiState.value.sessionSize)
        assertFalse(viewModel.uiState.value.isSettingsDirty, "저장 후 변경 상태 해제")
        assertTrue(
            viewModel.uiState.value.sessionSizeAppliesNextSession,
            "저장 직후엔 '다음 세션부터 적용' 안내를 세운다(실기기 QA — 진행 중 세션엔 반영 안 됨)",
        )
    }

    /**
     * ⭐️ [실기기 QA] 저장 안내는 다시 편집하면 걷힌다(다음 저장에서 다시 안내한다). 평상시 안내가
     * 눌러붙어 있지 않게 한다.
     */
    @Test
    fun settingsSavedNotice_clearsOnNextEdit() = runTest {
        val (viewModel, _) = memberViewModel()

        viewModel.onSessionSizeChange(12)
        viewModel.saveSettings()
        assertTrue(viewModel.uiState.value.sessionSizeAppliesNextSession)

        viewModel.onSessionSizeChange(9)
        assertFalse(
            viewModel.uiState.value.sessionSizeAppliesNextSession,
            "다시 편집하면 직전 저장 안내는 걷힌다",
        )
    }

    @Test
    fun settingsUpdate_outOfRange_isGuarded_noPatch() = runTest {
        val (viewModel, repository) = memberViewModel()

        viewModel.onSessionSizeChange(51) // 1..50 밖

        assertNotNull(viewModel.uiState.value.sessionSizeError, "범위 밖은 안내를 남긴다")
        assertFalse(viewModel.uiState.value.isSettingsDirty, "범위 밖은 저장 불가")

        viewModel.saveSettings()
        assertNull(repository.lastUpdatedSize, "범위 밖은 PATCH하지 않는다")
    }

    @Test
    fun settingsUpdate_backToServerValue_isNotDirty_andSkipsPatch() = runTest {
        val (viewModel, repository) = memberViewModel() // 서버 세션 크기 5

        viewModel.onSessionSizeChange(11)
        assertTrue(viewModel.uiState.value.isSettingsDirty)

        viewModel.onSessionSizeChange(5) // 서버 값으로 되돌림
        assertFalse(viewModel.uiState.value.isSettingsDirty, "서버 값과 같으면 변경 아님")

        viewModel.saveSettings()
        assertNull(repository.lastUpdatedSize, "변경 없으면 PATCH하지 않는다")
    }

    @Test
    fun settingsUpdate_belowRange_isGuarded() = runTest {
        val (viewModel, repository) = memberViewModel()

        viewModel.onSessionSizeChange(0)

        assertNotNull(viewModel.uiState.value.sessionSizeError)
        viewModel.saveSettings()
        assertNull(repository.lastUpdatedSize)
    }

    // ── 선호 난이도 설정 (난이도 조절 1차) ──────────────────────────────────────

    @Test
    fun preferredDifficulty_defaultsToNull_meaningAutomatic() = runTest {
        val (viewModel, _) = memberViewModel()

        assertNull(viewModel.uiState.value.preferredDifficulty, "새 계정은 미설정(자동)이 기본값")
    }

    @Test
    fun preferredDifficultyUpdate_happyPath_patchesAndSyncs() = runTest {
        val (viewModel, repository) = memberViewModel()

        viewModel.onPreferredDifficultyChange(PreferredDifficulty.HARD)
        assertTrue(viewModel.uiState.value.isPreferredDifficultyDirty, "변경 시 저장 가능")

        viewModel.savePreferredDifficulty()

        assertEquals(PreferredDifficulty.HARD, repository.lastUpdatedPreferredDifficulty, "서버에 새 선호 난이도가 전달돼야 한다")
        assertEquals(PreferredDifficulty.HARD, viewModel.uiState.value.preferredDifficulty)
        assertFalse(viewModel.uiState.value.isPreferredDifficultyDirty, "저장 후 변경 상태 해제")
    }

    @Test
    fun preferredDifficultyUpdate_backToServerValue_isNotDirty_andSkipsPatch() = runTest {
        val (viewModel, repository) = memberViewModel() // 서버 값은 미설정(null)
        viewModel.onPreferredDifficultyChange(PreferredDifficulty.EASY)
        viewModel.savePreferredDifficulty()
        assertEquals(PreferredDifficulty.EASY, repository.lastUpdatedPreferredDifficulty)

        // 서버 값(EASY)과 같은 값을 다시 고르면 변경 아님 → PATCH 재전송 안 함.
        repository.lastUpdatedPreferredDifficulty = null
        viewModel.onPreferredDifficultyChange(PreferredDifficulty.EASY)
        assertFalse(viewModel.uiState.value.isPreferredDifficultyDirty, "서버 값과 같으면 변경 아님")

        viewModel.savePreferredDifficulty()
        assertNull(repository.lastUpdatedPreferredDifficulty, "변경 없으면 PATCH하지 않는다")
    }

    @Test
    fun preferredDifficultyUpdate_failure_showsNotice_keepsDraft() = runTest {
        val (viewModel, repository) = memberViewModel()
        repository.updatePreferredDifficultyError = RuntimeException("down")

        viewModel.onPreferredDifficultyChange(PreferredDifficulty.MEDIUM)
        viewModel.savePreferredDifficulty()

        assertNotNull(viewModel.uiState.value.noticeError, "저장 실패는 비처벌 안내로 남는다")
        assertFalse(viewModel.uiState.value.isPreferredDifficultySaving)
    }

    @Test
    fun delete_requiresTwoStepConfirm() = runTest {
        val (viewModel, repository) = memberViewModel()
        var deleted = false

        viewModel.requestDelete()
        assertEquals(DeletePhase.Confirming, viewModel.uiState.value.deletePhase)
        // 확인 단계 진입만으로는 아직 삭제하지 않는다.
        assertEquals(0, repository.deleteMeCount)

        viewModel.confirmDelete(onDeleted = { deleted = true })

        assertEquals(1, repository.deleteMeCount, "확인 후에만 삭제")
        assertTrue(deleted, "삭제 완료 콜백 호출")
    }

    @Test
    fun rapidConfirmDelete_deletesOnce() = runTest {
        // 지연 실행 디스패처로 첫 삭제를 인플라이트로 붙잡아, 두 번째 확인이 무시되는지 본다.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeAccountRepository()
        val manager = SessionManager(repository, SettingsTokenStore(MapSettings()))
        manager.login("a@b.com", "pw12345678")
        advanceUntilIdle()
        val viewModel = AccountViewModel(manager, themeController())
        viewModel.requestDelete()

        viewModel.confirmDelete { } // deletePhase=Deleting 동기 반영 후 예약
        viewModel.confirmDelete { } // 이미 Deleting 이므로 무시
        advanceUntilIdle()

        assertEquals(1, repository.deleteMeCount, "이중 확인은 한 번만 삭제한다")
    }

    @Test
    fun cancelDelete_returnsToNone_withoutDeleting() = runTest {
        val (viewModel, repository) = memberViewModel()

        viewModel.requestDelete()
        viewModel.cancelDelete()

        assertEquals(DeletePhase.None, viewModel.uiState.value.deletePhase)
        assertEquals(0, repository.deleteMeCount, "취소는 아무것도 삭제하지 않는다")
    }

    @Test
    fun confirmDelete_withoutRequest_isNoOp() = runTest {
        val (viewModel, repository) = memberViewModel()
        var deleted = false

        // 확인 단계가 아니면 삭제가 트리거되지 않는다(오작동 방지).
        viewModel.confirmDelete(onDeleted = { deleted = true })

        assertEquals(0, repository.deleteMeCount)
        assertFalse(deleted)
    }

    @Test
    fun logout_switchesToGuest() = runTest {
        val (viewModel, _) = memberViewModel()
        assertTrue(viewModel.uiState.value.isMember)

        viewModel.logout()

        assertFalse(viewModel.uiState.value.isMember, "로그아웃하면 게스트로 전환")
    }

    @Test
    fun setThemeMode_updatesState() = runTest {
        val (viewModel, _) = memberViewModel()
        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)

        viewModel.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
    }

    // ── E: 화면 진입 초기화 + 삭제 확인 중 로그아웃 상충 방지 ────────────────────

    @Test
    fun resetForEntry_clearsDeletePhaseAndDraft() = runTest {
        val (viewModel, _) = memberViewModel()
        viewModel.requestDelete()
        viewModel.onSessionSizeChange(12)
        assertEquals(DeletePhase.Confirming, viewModel.uiState.value.deletePhase)
        assertTrue(viewModel.uiState.value.isSettingsDirty)

        viewModel.resetForEntry()

        assertEquals(DeletePhase.None, viewModel.uiState.value.deletePhase, "진입 시 삭제 단계 초기화")
        assertFalse(viewModel.uiState.value.isSettingsDirty, "진입 시 편집 초안 초기화")
    }

    @Test
    fun logout_isBlockedWhileDeleteConfirmOpen() = runTest {
        val (viewModel, _) = memberViewModel()
        viewModel.requestDelete()

        viewModel.logout()

        // 확인 카드가 열려 있는 동안 로그아웃은 끼어들지 못한다.
        assertEquals(DeletePhase.Confirming, viewModel.uiState.value.deletePhase)
        assertTrue(viewModel.uiState.value.isMember, "로그아웃이 실행되지 않아 여전히 회원")
    }

    @Test
    fun profileErrorMember_isSurfacedButStaysMember() = runTest {
        // 토큰 저장 후 getMe 실패 → 프로필 미상 Member.
        val repository = FakeAccountRepository().apply { getMeError = RuntimeException("down") }
        val manager = SessionManager(repository, SettingsTokenStore(MapSettings()))
        manager.login("a@b.com", "pw12345678")
        val viewModel = AccountViewModel(manager, themeController())

        val state = viewModel.uiState.value
        assertTrue(state.isMember, "프로필만 못 불러왔을 뿐 회원")
        assertTrue(state.profileError, "가벼운 프로필 오류 표시")
    }
}
