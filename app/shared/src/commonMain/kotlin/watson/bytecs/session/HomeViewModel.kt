package watson.bytecs.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import watson.bytecs.account.AuthState
import watson.bytecs.account.SessionManager
import kotlin.coroutines.cancellation.CancellationException

/**
 * 02 홈('오늘의 한입') 상태 홀더. 오늘의 세션 상태를 불러와 진행 요약·상태별 CTA·스트릭을 그리게 하고,
 * 계정 상태([SessionManager])를 합쳐 게스트/회원 진입점을 정한다.
 *
 * ⭐️ 가벼운 초대·무낙인: 분량 기반 진행만(타이머 없음), 완료는 긍정 빈 상태, 스트릭은 긍정 동기(끊겨도 죄책감 없음).
 */
class HomeViewModel(
    private val sessionRepository: SessionRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val load = MutableStateFlow<SessionLoad>(SessionLoad.Loading)

    val uiState: StateFlow<HomeUiState> =
        combine(load, sessionManager.state) { sessionLoad, auth ->
            buildState(sessionLoad, auth)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildState(load.value, sessionManager.state.value),
        )

    // ⭐️ 로드는 화면 진입([HomeScreen]의 LaunchedEffect)에서만 트리거한다(init 로드 시 진입마다 getToday 중복 호출 방지).

    /** 오늘의 세션 상태를 (다시) 불러온다. 화면 진입·재개·완료 후 홈 복귀에서 호출한다. */
    fun refresh() {
        load.value = SessionLoad.Loading
        viewModelScope.launch {
            load.value = try {
                SessionLoad.Loaded(sessionRepository.getToday())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                SessionLoad.Failed
            }
        }
    }

    private fun buildState(sessionLoad: SessionLoad, auth: AuthState): HomeUiState = when (sessionLoad) {
        SessionLoad.Loading -> HomeUiState.Loading
        SessionLoad.Failed -> HomeUiState.Error
        is SessionLoad.Loaded -> HomeUiState.Ready(
            session = sessionLoad.session,
            isMember = auth is AuthState.Member,
        )
    }

    private sealed interface SessionLoad {
        data object Loading : SessionLoad
        data object Failed : SessionLoad
        data class Loaded(val session: DailySession) : SessionLoad
    }
}

/** 02 홈 상태. */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** 오늘의 세션을 불러온 상태. [session]으로 진행·CTA·스트릭을, [isMember]로 계정 진입점을 정한다. */
    data class Ready(
        val session: DailySession,
        val isMember: Boolean,
    ) : HomeUiState {
        /** 아직 한 문제도 안 풀었고 완료도 아님 → 시작. */
        val isFresh: Boolean get() = !session.isCompleted && session.solvedCount == 0

        /** 진행 중(한 개 이상 풀었고 완료 아님) → 이어서. */
        val isInProgress: Boolean get() = !session.isCompleted && session.solvedCount > 0

        /** 오늘 몫 완료. */
        val isCompleted: Boolean get() = session.isCompleted
    }

    /** 세션 로드 실패(시스템 오류). 재시도 가능. */
    data object Error : HomeUiState
}
