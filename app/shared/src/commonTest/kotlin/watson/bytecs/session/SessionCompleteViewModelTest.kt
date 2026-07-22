package watson.bytecs.session

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.bytecs.account.FakeAccountRepository
import watson.bytecs.account.PreferredDifficulty
import watson.bytecs.account.SessionManager
import watson.bytecs.account.data.SettingsTokenStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SessionCompleteViewModel 검증 — 04 완료 화면 선호 난이도 제안 카드(DF1).
 * 노출은 서버가 준 플래그만 따르고, 선택·거절 모두 [SessionManager]를 거쳐 저장한 뒤 다시 열리지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionCompleteViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private suspend fun memberViewModel(
        needsDifficultyPrompt: Boolean,
        repository: FakeAccountRepository = FakeAccountRepository(),
    ): Pair<SessionCompleteViewModel, FakeAccountRepository> {
        val manager = SessionManager(repository, SettingsTokenStore(MapSettings()))
        manager.login("a@b.com", "pw12345678")
        return SessionCompleteViewModel(manager, needsDifficultyPrompt) to repository
    }

    @Test
    fun needsPrompt_true_showsCard() = runTest {
        val (viewModel, _) = memberViewModel(needsDifficultyPrompt = true)

        assertTrue(viewModel.uiState.value.visible)
    }

    @Test
    fun needsPrompt_false_hidesCard() = runTest {
        val (viewModel, _) = memberViewModel(needsDifficultyPrompt = false)

        assertFalse(viewModel.uiState.value.visible)
    }

    @Test
    fun select_happyPath_savesPreference_andClosesCard() = runTest {
        val (viewModel, repository) = memberViewModel(needsDifficultyPrompt = true)

        viewModel.select(PreferredDifficulty.HARD)

        assertEquals(PreferredDifficulty.HARD, repository.lastUpdatedPreferredDifficulty, "서버에 선택한 선호가 전달돼야 한다")
        assertFalse(viewModel.uiState.value.visible, "저장 성공 후 카드는 닫힌다")
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun select_failure_keepsCardVisible_forRetry() = runTest {
        val (viewModel, repository) = memberViewModel(needsDifficultyPrompt = true)
        repository.updatePreferredDifficultyError = RuntimeException("down")

        viewModel.select(PreferredDifficulty.EASY)

        assertTrue(viewModel.uiState.value.visible, "실패해도 카드는 유지된다 — 재시도 가능")
        assertNotNull(viewModel.uiState.value.error, "비처벌 안내가 남는다")
        assertFalse(viewModel.uiState.value.saving)
    }

    /** 실패 후 같은(또는 다른) 항목을 다시 골라 재시도할 수 있다 — 죽은 카드가 아니다. */
    @Test
    fun select_retryAfterFailure_succeeds() = runTest {
        val (viewModel, repository) = memberViewModel(needsDifficultyPrompt = true)
        repository.updatePreferredDifficultyError = RuntimeException("down")
        viewModel.select(PreferredDifficulty.EASY)
        assertTrue(viewModel.uiState.value.visible)

        repository.updatePreferredDifficultyError = null
        viewModel.select(PreferredDifficulty.EASY)

        assertEquals(PreferredDifficulty.EASY, repository.lastUpdatedPreferredDifficulty)
        assertFalse(viewModel.uiState.value.visible)
        assertNull(viewModel.uiState.value.error, "재시도 성공 시 이전 오류는 걷힌다")
    }

    @Test
    fun dismiss_happyPath_recordsRejection_andShowsNotice() = runTest {
        val (viewModel, repository) = memberViewModel(needsDifficultyPrompt = true)

        viewModel.dismiss()

        assertEquals(1, repository.dismissDifficultyPromptCount, "거절이 서버에 기록돼야 한다")
        assertTrue(viewModel.uiState.value.visible, "안내를 잠깐 보여주는 동안엔 카드가 남아 있다")
        assertTrue(viewModel.uiState.value.dismissedNotice, "'설정에서 언제든 바꿀 수 있어요' 안내를 세운다")
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun dismiss_failure_keepsCardVisible_withoutNotice() = runTest {
        val (viewModel, repository) = memberViewModel(needsDifficultyPrompt = true)
        repository.dismissDifficultyPromptError = RuntimeException("down")

        viewModel.dismiss()

        assertTrue(viewModel.uiState.value.visible)
        assertFalse(viewModel.uiState.value.dismissedNotice, "실패했으면 안내를 보이면 안 된다")
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun closeAfterDismissNotice_hidesCard_withoutExtraServerCall() = runTest {
        val (viewModel, repository) = memberViewModel(needsDifficultyPrompt = true)
        viewModel.dismiss()
        assertTrue(viewModel.uiState.value.dismissedNotice)

        viewModel.closeAfterDismissNotice()

        assertFalse(viewModel.uiState.value.visible)
        assertFalse(viewModel.uiState.value.dismissedNotice)
        assertEquals(1, repository.dismissDifficultyPromptCount, "닫기 자체는 서버를 다시 부르지 않는다")
    }

    /** 이미 응답이 끝나(닫힌) 카드는 다시 선택해도 아무 일도 하지 않는다(막다른 길 방지 대칭 — 중복 전송 금지). */
    @Test
    fun select_afterCardClosed_isNoOp() = runTest {
        val (viewModel, repository) = memberViewModel(needsDifficultyPrompt = false)

        viewModel.select(PreferredDifficulty.HARD)

        assertNull(repository.lastUpdatedPreferredDifficulty)
    }

    @Test
    fun dismiss_afterCardClosed_isNoOp() = runTest {
        val (viewModel, repository) = memberViewModel(needsDifficultyPrompt = false)

        viewModel.dismiss()

        assertEquals(0, repository.dismissDifficultyPromptCount)
    }
}
