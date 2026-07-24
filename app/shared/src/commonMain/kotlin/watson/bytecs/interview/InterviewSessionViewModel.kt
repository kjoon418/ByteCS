package watson.bytecs.interview

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
 * 08 면접 세션 화면 상태 홀더. 질문 제시 → 자기 말로 설명 작성 → 제출 → 채점 로딩(수 초) → 결과(체크리스트 또는
 * 폴백) → 다음 문항, 마지막이면 완료 요약 후 홈 복귀(일회성 이벤트).
 *
 * ⭐️ 무낙인: 채점 결과는 점수·합불이 아니라 "짚은 포인트 / 보완하면 좋은 포인트"로만 표현한다(화면 책임).
 * ⭐️ 재제출 없음: 1문항 1채점. 제출 후에는 입력이 잠기고 곧장 채점 로딩으로 전환된다.
 * ⭐️ 폴백(콘텐츠 채점 실패)은 [ExplanationOutcome.result]의 fallback으로 온다(에러 아님) — 시스템 오류(전송 실패)와 구분한다.
 */
class InterviewSessionViewModel(
    private val repository: InterviewRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<InterviewUiState>(InterviewUiState.Loading)
    val uiState: StateFlow<InterviewUiState> = _uiState.asStateFlow()

    private val _events = Channel<InterviewEvent>(Channel.BUFFERED)
    val events: Flow<InterviewEvent> = _events.receiveAsFlow()

    // '면접 연습 마치기' 이중 탭·백 버튼 경계 — 완료 이벤트를 정확히 한 번만 보낸다.
    private var finishing = false

    // ⭐️ 로드는 화면 진입([InterviewSessionScreen]의 LaunchedEffect)에서만 트리거한다(중복 로드 방지 — 세션 슬라이스와 동일 규율).

    /**
     * 오늘의 면접 세션을 시작하거나 이어서 받는다. 이미 완료 상태로 진입하면(막다른 길 방지) 곧장 홈으로 되돌린다.
     * 오류 재시도에도 쓰인다.
     */
    fun load() {
        _uiState.value = InterviewUiState.Loading
        viewModelScope.launch {
            try {
                val session = repository.startOrResumeToday()
                val item = session.currentItem
                if (session.isCompleted || item == null) {
                    // 오늘 몫을 이미 마친 상태로 진입 — 홈으로 되돌린다(홈 카드가 이런 진입을 애초에 거른다).
                    _events.send(InterviewEvent.Finished)
                    return@launch
                }
                _uiState.value = InterviewUiState.Active(
                    item = item,
                    position = session.position,
                    total = session.totalCount,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (unavailable: InterviewUnavailableException) {
                // 낡은 홈 카드로 진입(후보 없음·쿼터 소진·이미 완료 등) — 재시도로 풀리지 않으므로 홈으로 되돌린다
                // (홈 카드가 실제 사유로 다시 그려진다). 무한 재시도 막다른 길 방지.
                _events.send(InterviewEvent.Finished)
            } catch (error: Throwable) {
                _uiState.value = InterviewUiState.Error
            }
        }
    }

    /** 설명 입력 변경. 직전 전송 실패 표시는 지운다(고친 입력은 아직 재전송 전이므로). 채점 중·결과 단계에서는 무시. */
    fun onInputChange(text: String) {
        _uiState.update { state ->
            if (state is InterviewUiState.Active && state.phase == InterviewPhase.Writing) {
                state.copy(inputText = text, systemError = false)
            } else {
                state
            }
        }
    }

    /**
     * 설명 제출 → 입력 잠금 → 채점 로딩 → 결과. 빈 입력·채점 중·결과 단계에서는 무시한다(이중 제출·재제출 가드).
     * 전송 자체가 실패하면(네트워크 등) 폴백(콘텐츠 채점 실패)과 달리 **시스템 오류**로 다뤄 입력을 살린 채
     * 재시도 경로를 남긴다(무낙인 — §5.12). 서버가 준 채점 폴백(judge.fallback)은 정상 결과로 흐른다.
     */
    fun submit() {
        val current = _uiState.value
        if (current !is InterviewUiState.Active || current.phase != InterviewPhase.Writing) return
        if (current.inputText.isBlank()) return

        _uiState.value = current.copy(phase = InterviewPhase.Grading, systemError = false)
        viewModelScope.launch {
            try {
                val outcome = repository.submitExplanation(current.position, current.inputText)
                _uiState.update { state ->
                    if (state !is InterviewUiState.Active) return@update state
                    state.copy(
                        phase = InterviewPhase.Result,
                        result = ItemResult(
                            judge = outcome.result,
                            modelAnswer = outcome.modelAnswer,
                            conceptName = outcome.conceptName,
                            reviewProblemId = outcome.reviewProblemId,
                        ),
                        pendingNext = outcome.nextItem,
                        completion = outcome.completion,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (unavailable: InterviewUnavailableException) {
                // 세션이 다른 경로로 이미 끝났다(예: 다른 기기에서 완료). 재제출은 불가하므로 홈으로 되돌린다.
                _events.send(InterviewEvent.Finished)
            } catch (mapping: InterviewResponseMappingException) {
                // 서버는 이미 답을 반영(커서 전진)했으나 응답을 해석하지 못함 — 같은 답 재제출은 다음 문항에 답하게 되므로 금지.
                // 세션을 다시 불러와(서버 커서 기준) 이어서 복구하도록 오류 상태로 전환한다(재시도=load).
                _uiState.value = InterviewUiState.Error
            } catch (error: Throwable) {
                // 전송 실패(시스템 오류) — 입력을 살리고 다시 쓰기 단계로 되돌려 재시도할 수 있게 한다.
                _uiState.update { state ->
                    if (state is InterviewUiState.Active) {
                        state.copy(phase = InterviewPhase.Writing, systemError = true)
                    } else {
                        state
                    }
                }
            }
        }
    }

    /** 결과 확인 후 다음 문항으로 진행한다(입력·결과 초기화). 다음 문항이 없으면(마지막) no-op. */
    fun advance() {
        _uiState.update { state ->
            if (state !is InterviewUiState.Active) return@update state
            val next = state.pendingNext ?: return@update state
            InterviewUiState.Active(
                item = next,
                position = state.position + 1,
                total = state.total,
            )
        }
    }

    /**
     * 마지막 문항까지 마친 뒤 '면접 연습 마치기'로 홈에 복귀한다. 완료 대상이 아니거나 이미 한 번 보냈으면 무시한다.
     * ⭐️ 완료 요약 블록은 [InterviewUiState.Active.completion]으로 계속 보여야 하므로 상태를 비우지 않고,
     * 별도 [finishing] 가드로 이중 발화만 막는다.
     */
    fun finish() {
        val current = _uiState.value
        if (current !is InterviewUiState.Active || current.completion == null || finishing) return
        finishing = true
        viewModelScope.launch {
            _events.send(InterviewEvent.Finished)
        }
    }
}

/** 08 면접 세션 화면 상태. */
sealed interface InterviewUiState {
    /** 첫 질문을 불러오는 중(콘텐츠 스켈레톤). */
    data object Loading : InterviewUiState

    /** 세션 로드 실패(시스템 오류). 재시도 가능. */
    data object Error : InterviewUiState

    /**
     * 문항 진행 중. [phase]가 쓰기/채점/결과 단계를 가른다.
     *  - [item]: 지금 문항(결과 단계에서는 방금 답한 문항).
     *  - [position]: 지금 칸(0-based) → 표시용 번호 [current](1-based).
     *  - [result]: 결과 단계에서 채운다(채점 성공 체크리스트 또는 폴백).
     *  - [pendingNext]: 다음 문항(없으면 이번이 마지막 — CTA가 '면접 연습 마치기'로 바뀐다).
     *  - [completion]: 이 제출로 세션이 완료됐을 때만 채워진다(완료 요약 블록의 데이터 소스).
     *  - [systemError]: 제출 전송 실패(폴백과 구분되는 시스템 오류) — 입력 유지·재시도.
     */
    data class Active(
        val item: InterviewItem,
        val position: Int,
        val total: Int,
        val inputText: String = "",
        val phase: InterviewPhase = InterviewPhase.Writing,
        val result: ItemResult? = null,
        val pendingNext: InterviewItem? = null,
        val completion: InterviewCompletion? = null,
        val systemError: Boolean = false,
    ) : InterviewUiState {
        /** 표시용 진행 번호(1-based). */
        val current: Int get() = position + 1

        /** 채점 로딩 중 — 입력이 잠기고 CTA 없이 대기한다. */
        val isGrading: Boolean get() = phase == InterviewPhase.Grading

        /** 결과 단계 — 체크리스트/폴백과 모범 설명을 보여준다. */
        val isResult: Boolean get() = phase == InterviewPhase.Result

        /** 방금 답한 문항이 세션의 마지막이었는지 — CTA가 '다음 질문으로' 대신 '면접 연습 마치기'로 바뀐다. */
        val isLastItem: Boolean get() = phase == InterviewPhase.Result && completion != null
    }
}

/** 문항 진행 단계. 쓰기 → 채점 → 결과(단방향). 재제출 없음(1문항 1채점). */
enum class InterviewPhase {
    Writing,
    Grading,
    Result,
}

/**
 * 결과 단계에 보여줄 한 문항의 채점 산출물.
 *  - [reviewProblemId]: '그때 푼 문제 다시 보기'(DI10) 대상 — '검증됨' 미달이고 재열람할 문제가 있을 때만(없으면 null).
 */
data class ItemResult(
    val judge: ExplanationJudgeResult,
    val modelAnswer: String,
    val conceptName: String,
    val reviewProblemId: Long?,
)

/** 화면이 소비하는 일회성 이벤트. */
sealed interface InterviewEvent {
    /** 세션 완료(또는 이미 완료 상태로 진입) — 홈으로 복귀. */
    data object Finished : InterviewEvent
}
