package watson.bytecs.extrastudy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.bytecs.problem.Enrichment
import watson.bytecs.problem.JudgeResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * 추가 학습('조금 더 풀기') 상태 홀더. 세션 풀이와 시각·동작이 같되, 세션 전용 요소(진행 위치·분량·완료·
 * 스트릭·지난 문제)를 전부 뺀다. 판정·선정은 전적으로 서버에 위임한다.
 *
 * ⭐️ 무낙인: 불일치·근접은 처벌이 아니다(빨강·경고 금지). 시스템 오류는 오답과 엄격히 구분한다(§5.12).
 * ⭐️ 진행을 요구하는 유일한 지점은 '정답 공개 후 따라 입력'뿐(불변식 19) — 그 외엔 강제하지 않는다.
 * ⭐️ 세션과 달리 진행은 클라 상태의 다음 칸으로 가는 게 아니라 [advance]가 [getCurrent]를 다시 불러
 *    다음 문제(또는 소진)를 받는다 — 단일 열린 항목 모델이라 조회를 원천으로 삼는다.
 */
class ExtraStudyViewModel(
    private val repository: ExtraStudyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExtraStudyUiState>(ExtraStudyUiState.Loading)
    val uiState: StateFlow<ExtraStudyUiState> = _uiState.asStateFlow()

    // ⭐️ 로드는 화면 진입([ExtraStudyScreen]의 LaunchedEffect)에서만 트리거한다(세션 관례).

    /** 현재(이어 풀) 문제를 불러온다. 소진이면 소진 상태로, 오류 재시도에도 쓰인다. */
    fun load() {
        _uiState.value = ExtraStudyUiState.Loading
        viewModelScope.launch {
            try {
                when (val current = repository.getCurrent()) {
                    is ExtraStudyState.Available ->
                        _uiState.value = ExtraStudyUiState.Active(
                            problem = current.problem,
                            revealedHints = current.problem.revealedHints,
                        )
                    ExtraStudyState.Exhausted ->
                        _uiState.value = ExtraStudyUiState.Exhausted
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.value = ExtraStudyUiState.Error
            }
        }
    }

    /** 답 입력 변경. 답을 고치면 직전 피드백·전송 실패 표시를 지운다(공개된 모범답안은 유지 — 따라 적는 중). */
    fun onInputChange(text: String) {
        _uiState.update { state ->
            if (state is ExtraStudyUiState.Active) state.copy(inputText = text, feedback = null, systemError = false)
            else state
        }
    }

    /**
     * 정답 확인. 정답이면 피드백(개념·해설·심화)을 보이고, 사용자가 [다음 문제]([advance])를 눌러 다음을 받는다.
     * 이중 제출·정답 후 재제출은 무시한다. 열린 항목이 없다는 경합([ExtraStudyNoOpenItemException])은
     * 시스템 오류가 아니라 [load]로 재동기화한다(무낙인).
     */
    fun submit() {
        val current = _uiState.value
        if (current !is ExtraStudyUiState.Active || current.inputText.isBlank() || current.isSubmitting) return
        // 정답을 맞힌 뒤에는 제출이 아니라 [advance](다음 문제 조회)만 한다.
        if (current.solved) return

        _uiState.value = current.copy(isSubmitting = true, systemError = false)
        viewModelScope.launch {
            try {
                val attempt = repository.submitAttempt(current.inputText)
                _uiState.update { state ->
                    if (state !is ExtraStudyUiState.Active) return@update state
                    when (attempt.result) {
                        JudgeResult.CORRECT -> state.copy(
                            feedback = ExtraStudyFeedback.Correct(
                                concepts = attempt.concepts,
                                explanation = attempt.explanation,
                                enrichment = attempt.enrichment,
                                representativeAnswer = attempt.representativeAnswer,
                            ),
                            // 공개 후 따라 입력해 맞힌 경우, 정답 카드 옆에 낡은 공개 패널이 남지 않게 지운다.
                            reveal = null,
                            isRevealing = false,
                            isSubmitting = false,
                        )
                        JudgeResult.NEAR_MISS ->
                            state.copy(feedback = ExtraStudyFeedback.NearMiss, isSubmitting = false)
                        JudgeResult.MISMATCH ->
                            state.copy(
                                feedback = ExtraStudyFeedback.Mismatch(attempt.misconceptionHint),
                                isSubmitting = false,
                            )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (noOpenItem: ExtraStudyNoOpenItemException) {
                // 열린 항목이 사라진 경합(다른 기기 등) — 막다른 길을 만들지 않고 현재 상태로 재동기화한다.
                load()
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state is ExtraStudyUiState.Active) state.copy(isSubmitting = false, systemError = true) else state
                }
            }
        }
    }

    /**
     * 정답 맞힌 뒤 [다음 문제]로 진행한다. 세션처럼 클라 상태의 다음 칸으로 가는 게 아니라 [getCurrent]를
     * 다시 불러(=[load]) 다음 문제 또는 소진을 받는다. 정답 상태에서만 동작한다.
     */
    fun advance() {
        val current = _uiState.value
        if (current !is ExtraStudyUiState.Active || !current.solved) return
        load()
    }

    /**
     * 정답 공개(안전판). 모범답안·개념·해설을 받아 보여 준다. 시도 전에도 허용(선행 오답 요구 없음, [결정 2026-07-17]).
     * 공개 후에도 **직접 따라 입력**해야 다음으로 넘어간다(서버는 정답 제출에만 진행하므로 자연히 강제 — 손으로 익히기).
     */
    fun requestReveal() {
        val current = _uiState.value
        if (current !is ExtraStudyUiState.Active || current.isRevealing || current.reveal != null) return

        _uiState.value = current.copy(isRevealing = true, systemError = false)
        viewModelScope.launch {
            try {
                val reveal = repository.reveal()
                _uiState.update { state ->
                    if (state is ExtraStudyUiState.Active) state.copy(reveal = reveal, isRevealing = false) else state
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (noOpenItem: ExtraStudyNoOpenItemException) {
                load()
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state is ExtraStudyUiState.Active) state.copy(isRevealing = false, systemError = true) else state
                }
            }
        }
    }

    /**
     * 다음 힌트 하나를 연다(pull, 약→강). 서버 왕복이다 — 공개 목록은 **서버가 원천**이라 성공했을 때만
     * [ExtraStudyUiState.Active.revealedHints]가 늘어난다.
     *
     * ⭐️ 열람 실패(네트워크·경합 등)는 진행을 막지 않는다 — 진행 표시만 내리고 조용히 둔다(무낙인, systemError 아님).
     * 이미 요청 중이거나 더 열 힌트가 없으면 아무 것도 하지 않는다.
     */
    fun revealNextHint() {
        val current = _uiState.value
        if (current !is ExtraStudyUiState.Active || current.isRevealingHint || !current.hasMoreHints) return

        _uiState.value = current.copy(isRevealingHint = true)
        viewModelScope.launch {
            try {
                val result = repository.revealHint(current.revealedHintCount)
                _uiState.update { state ->
                    if (state is ExtraStudyUiState.Active) {
                        state.copy(revealedHints = result.revealedHints, isRevealingHint = false)
                    } else {
                        state
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // 진행을 막지 않는다 — 다음 제출로 자연히 재동기화된다. 진행 표시만 내린다.
                _uiState.update { state ->
                    if (state is ExtraStudyUiState.Active) state.copy(isRevealingHint = false) else state
                }
            }
        }
    }
}

/** 추가 학습 화면 상태. 세션과 달리 진행·완료·지난 문제 상태가 없고, 소진([Exhausted])을 별도 국면으로 둔다. */
sealed interface ExtraStudyUiState {
    data object Loading : ExtraStudyUiState

    /** 로드 실패(시스템 오류). 재시도 가능. */
    data object Error : ExtraStudyUiState

    /** 소진 — 모두 풀었고 도래한 복습도 없다(무낙인·긍정 톤). 복습 주기가 돌아오면 다시 풀 문제가 생긴다. */
    data object Exhausted : ExtraStudyUiState

    /** 풀이 중. */
    data class Active(
        val problem: ExtraStudyProblem,
        val inputText: String = "",
        val feedback: ExtraStudyFeedback? = null,
        val isSubmitting: Boolean = false,
        val systemError: Boolean = false,
        val reveal: ExtraStudyReveal? = null,
        val isRevealing: Boolean = false,
        // 공개된 힌트 본문(약→강). 서버 응답이 원천 — 새 문제 진입 시 problem.revealedHints로 복원한다(재진입 복원).
        val revealedHints: List<ExtraStudyHint> = emptyList(),
        val isRevealingHint: Boolean = false,
    ) : ExtraStudyUiState {
        /** 정답을 맞혀 다음으로 넘어갈 수 있는 상태. */
        val solved: Boolean get() = feedback is ExtraStudyFeedback.Correct

        /** 지금까지 공개한 힌트 수(서버가 원천 — 목록 크기로만 센다). */
        val revealedHintCount: Int get() = revealedHints.size

        /** 아직 더 열 힌트가 있는지(전체 hintCount 대비). 없으면 진입점을 그리지 않는다. */
        val hasMoreHints: Boolean get() = revealedHints.size < problem.hintCount

        /**
         * '정답 보기'를 제안할 수 있는지 — 시도 전에도 열 수 있다([결정 2026-07-17], 무낙인·사용자 주도).
         * 이미 공개했거나 정답을 맞힌 상태에서만 제안하지 않는다(불변식 3: 요청 시에만 공개).
         */
        val canReveal: Boolean
            get() = reveal == null && !solved
    }
}

/** 제출 피드백. 세 상태 모두 비처벌. 개념·해설은 정답일 때만(no-leak). */
sealed interface ExtraStudyFeedback {
    /**
     * [concepts]: 태깅 순서를 보존한 개념 목록(첫 번째가 대표 개념).
     * [enrichment]: '더 알아보기'(§5.7) — 없어도 되는 선택 콘텐츠.
     * [representativeAnswer]: 화면 표시용 대표 정답 — 확정 입력란은 제출 텍스트가 아니라 이 값을 보여준다
     * ([2026-07-16] 오너 결정).
     */
    data class Correct(
        val concepts: List<String>?,
        val explanation: String?,
        val enrichment: Enrichment? = null,
        val representativeAnswer: String? = null,
    ) : ExtraStudyFeedback

    /**
     * 불일치. [misconceptionHint]는 큐레이션된 예상 오답과 일치할 때만 실린다(push·자동, 기능 2.5) — 보통 null이다.
     * 무낙인: 있어도 오답 확정 아님, 정답 비노출. danger 톤 금지(교정 카드는 info 톤).
     */
    data class Mismatch(val misconceptionHint: String? = null) : ExtraStudyFeedback
    data object NearMiss : ExtraStudyFeedback
}
