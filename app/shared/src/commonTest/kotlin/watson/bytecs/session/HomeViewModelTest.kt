package watson.bytecs.session

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.bytecs.account.FakeAccountRepository
import watson.bytecs.account.SessionManager
import watson.bytecs.account.data.SettingsTokenStore
import watson.bytecs.session.FakeSessionRepository.Companion.activeSession
import watson.bytecs.session.FakeSessionRepository.Companion.completedSession
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HomeViewModel 검증. 오늘 세션 상태에 따른 fresh/진행중/완료 분기와 계정(게스트/회원) 진입점.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private suspend fun guestManager(): SessionManager {
        val manager = SessionManager(FakeAccountRepository(), SettingsTokenStore(MapSettings()))
        manager.bootstrap()
        return manager
    }

    private suspend fun memberManager(): SessionManager {
        val manager = SessionManager(FakeAccountRepository(), SettingsTokenStore(MapSettings()))
        manager.login("a@b.com", "pw12345678")
        return manager
    }

    private fun HomeViewModel.ready(): HomeUiState.Ready = uiState.value as HomeUiState.Ready

    @Test
    fun freshSession_isFresh() = runTest {
        val repo = FakeSessionRepository(today = activeSession(solved = 0))
        val viewModel = HomeViewModel(repo, guestManager()).apply { refresh() }

        val state = viewModel.ready()
        assertTrue(state.isFresh)
        assertFalse(state.isInProgress)
        assertFalse(state.isCompleted)
    }

    @Test
    fun inProgressSession_isInProgress() = runTest {
        val repo = FakeSessionRepository(today = activeSession(position = 2, total = 5, solved = 2))
        val viewModel = HomeViewModel(repo, guestManager()).apply { refresh() }

        assertTrue(viewModel.ready().isInProgress)
        assertEquals(2, viewModel.ready().session.solvedCount)
    }

    @Test
    fun completedSession_isCompleted() = runTest {
        val repo = FakeSessionRepository(today = completedSession(total = 5))
        val viewModel = HomeViewModel(repo, guestManager()).apply { refresh() }

        assertTrue(viewModel.ready().isCompleted)
    }

    @Test
    fun loadFailure_yieldsError() = runTest {
        val repo = FakeSessionRepository().apply { getTodayError = RuntimeException("down") }
        val viewModel = HomeViewModel(repo, guestManager()).apply { refresh() }

        assertTrue(viewModel.uiState.value is HomeUiState.Error)
    }

    @Test
    fun guest_isNotMember() = runTest {
        val repo = FakeSessionRepository(today = activeSession())
        val viewModel = HomeViewModel(repo, guestManager()).apply { refresh() }

        assertFalse(viewModel.ready().isMember, "게스트는 가입 유도 진입점을 본다")
    }

    @Test
    fun member_isMember() = runTest {
        val repo = FakeSessionRepository(today = activeSession())
        val viewModel = HomeViewModel(repo, memberManager()).apply { refresh() }

        assertTrue(viewModel.ready().isMember)
    }

    @Test
    fun streak_isCarriedThrough_whenPresent() = runTest {
        val repo = FakeSessionRepository(today = activeSession(streak = Streak(4, "2026-07-14")))
        val viewModel = HomeViewModel(repo, guestManager()).apply { refresh() }

        assertEquals(4, viewModel.ready().session.streak?.count)
    }

    @Test
    fun freshStreak_countZero_isSurfaced_notHidden() = runTest {
        // 백엔드는 이제 항상 streak를 준다(신규 사용자 = count 0). 홈은 0도 그대로 받아 '다시 시작해요' 톤으로 그린다.
        val repo = FakeSessionRepository(today = activeSession(streak = Streak(0, null)))
        val viewModel = HomeViewModel(repo, guestManager()).apply { refresh() }

        val streak = viewModel.ready().session.streak
        assertEquals(0, streak?.count, "count 0도 상태로 전달된다(숨기지 않음)")
    }
}
