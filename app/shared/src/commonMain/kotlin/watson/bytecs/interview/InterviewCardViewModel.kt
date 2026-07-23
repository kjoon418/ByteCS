package watson.bytecs.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 02 홈의 면접 연습 진입 카드(디자인 02 3-b) 상태 홀더. `GET /api/interview/status` 하나로 카드의 4개 상태를
 * 그대로 반영한다(클라이언트가 재판단하지 않음 — 계획 §4.3).
 *
 * ⭐️ 오늘의 한입(1단·히어로)과 독립된 부차 데이터라, 상태 로드가 실패하면 카드를 **조용히 숨긴다**([Hidden]) —
 * 홈의 핵심 흐름을 막지 않고 에러 소음도 내지 않는다(무낙인). C4-β 서버 연동 전에는 항상 이 경로로 숨는다.
 */
class InterviewCardViewModel(
    private val repository: InterviewRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<InterviewCardUiState>(InterviewCardUiState.Hidden)
    val uiState: StateFlow<InterviewCardUiState> = _uiState.asStateFlow()

    // ⭐️ 로드는 화면 진입([HomeScreen]의 LaunchedEffect)에서만 트리거한다(init 로드 중복 호출 방지 — HomeViewModel과 동일 규율).

    /** 면접 상태를 (다시) 불러온다. 홈 진입·면접 세션 완료 후 복귀에서 호출한다(잔여 쿼터 갱신). */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = try {
                repository.status().toCardState()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // 실패 시 카드를 숨긴다 — 부차 정보라 홈을 막거나 에러를 띄우지 않는다.
                InterviewCardUiState.Hidden
            }
        }
    }
}

/**
 * [InterviewStatus] → 카드 상태(디자인 02 3-b의 4종). 서버 단일 출처를 그대로 분기할 뿐이다.
 *  - 게스트 → 가입 유도
 *  - 후보 0 → 긍정 빈 상태
 *  - 잔여 있음(후보≥1 ∧ 쿼터>0) → 진입 CTA
 *  - 오늘 소진(후보≥1 ∧ 쿼터=0) → 담백 안내
 */
internal fun InterviewStatus.toCardState(): InterviewCardUiState = when {
    guest -> InterviewCardUiState.Guest(candidateCount)
    candidateCount == 0 -> InterviewCardUiState.Empty
    remainingToday > 0 -> InterviewCardUiState.Ready(candidateCount)
    else -> InterviewCardUiState.Exhausted
}

/**
 * 02 홈 면접 연습 카드 상태.
 *  - [Hidden]: 로딩 전 또는 상태 로드 실패 — 카드를 렌더하지 않는다(조용한 미노출).
 */
sealed interface InterviewCardUiState {
    data object Hidden : InterviewCardUiState

    /** 게스트 — 가입 유도. [candidateCount]로 "익힌 개념 N개" 문구를 구체화한다. */
    data class Guest(val candidateCount: Int) : InterviewCardUiState

    /** 후보 0개(아직 승급된 개념 없음) — 긍정 빈 상태. */
    data object Empty : InterviewCardUiState

    /** 잔여 있음(오늘 쿼터 남음) — 진입 CTA. [candidateCount]로 "익힌 개념 N개" 문구를 구체화한다. */
    data class Ready(val candidateCount: Int) : InterviewCardUiState

    /** 오늘 소진(쿼터 다 씀) — 담백 안내. */
    data object Exhausted : InterviewCardUiState
}
