package watson.bytecs.session

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
import watson.bytecs.problem.JudgeResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * 03 문제 풀이(세션 연동) 상태 홀더. 진행=세션 위치, 제출=세션 attempt, 정답 공개 후 따라 입력, 지난 문제 읽기 전용.
 *
 * ⭐️ 무낙인: 불일치·근접은 처벌이 아니다(빨강·경고 금지). 시스템 오류는 오답과 엄격히 구분한다(§5.12).
 * ⭐️ 진행을 요구하는 유일한 지점은 '정답 공개 후 따라 입력'뿐 — 그 외엔 강제하지 않는다.
 * 세션 완료는 상태로 눌러붙지 않게 **일회성 이벤트**([events])로 04 완료 화면에 넘긴다.
 */
class SessionViewModel(
    private val repository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private val _events = Channel<SessionEvent>(Channel.BUFFERED)
    val events: Flow<SessionEvent> = _events.receiveAsFlow()

    // ⭐️ 로드는 화면 진입([SessionScreen]의 LaunchedEffect)에서만 트리거한다. init 로드를 두면 진입 시 getToday가
    //    두 번 불려, 완료 상태 진입 시 완료 이벤트가 두 번 발사(축하 중복)되는 버그가 생긴다.

    /** 오늘의 세션을 불러온다. 이미 완료됐으면 곧장 완료 이벤트로 넘긴다(막다른 길 방지). 오류 재시도에도 쓰인다. */
    fun loadSession() = load()

    private fun load() {
        _uiState.value = SessionUiState.Loading
        viewModelScope.launch {
            try {
                val session = repository.getToday()
                val problem = session.currentProblem
                if (session.isCompleted || problem == null) {
                    // 이미 완료 상태로 진입 — 완료 화면으로 넘긴다(스트릭은 read 경로가 주면 함께).
                    _events.send(SessionEvent.Completed(session.toSummary()))
                    return@launch
                }
                _uiState.value = SessionUiState.Active(
                    problem = problem,
                    position = session.position,
                    total = session.totalCount,
                    solvedCount = session.solvedCount,
                    revealedHints = problem.revealedHints,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.value = SessionUiState.Error
            }
        }
    }

    /** 답 입력 변경. 답을 고치면 직전 피드백·전송 실패 표시를 지운다(공개된 모범답안은 유지 — 따라 적는 중). */
    fun onInputChange(text: String) {
        _uiState.update { state ->
            if (state is SessionUiState.Active) state.copy(inputText = text, feedback = null, systemError = false)
            else state
        }
    }

    /**
     * 정답 확인. 정답이면 피드백(개념·해설)을 보이고 [advance]로 다음 칸에 진행한다.
     * 마지막 칸을 맞히면 세션이 완료돼 완료 이벤트를 낸다. 이중 제출·정답 후 재제출은 무시한다.
     */
    fun submit() {
        val current = _uiState.value
        if (current !is SessionUiState.Active || current.inputText.isBlank() || current.isSubmitting) return
        // ⭐️ 정답을 맞힌 뒤에는 제출이 아니라 [advance]만 한다. 세션 제출은 **위치 기반**이라(제출에 문제 id가
        //    없다) 정답으로 이미 전진한 서버 커서에 낡은 입력이 한 번 더 닿으면, 사용자가 **본 적 없는**
        //    다음 문제가 그 입력으로 채점돼 통째로 소비된다. 화면이 어느 경로로 부르든 여기서 막는다.
        if (current.solved) return

        _uiState.value = current.copy(isSubmitting = true, systemError = false)
        viewModelScope.launch {
            try {
                val outcome = repository.submitAttempt(current.inputText)
                if (outcome.isCompleted) {
                    _events.send(SessionEvent.Completed(outcome.toSummary()))
                    return@launch
                }
                _uiState.update { state ->
                    if (state !is SessionUiState.Active) return@update state
                    when (outcome.result) {
                        JudgeResult.CORRECT -> state.copy(
                            feedback = SessionFeedback.Correct(outcome.concepts, outcome.explanation, outcome.enrichment),
                            // 다음 칸은 [advance]에서 실제로 이동한다(정답 후 [다음 문제] CTA).
                            pendingNext = outcome.currentProblem,
                            pendingPosition = outcome.position,
                            solvedCount = outcome.solvedCount,
                            // 공개 후 따라 입력해 맞힌 경우, 정답 카드 옆에 낡은 공개 패널이 남지 않게 지운다.
                            reveal = null,
                            isRevealing = false,
                            isSubmitting = false,
                        )
                        JudgeResult.NEAR_MISS -> state.withNonCorrect(outcome, SessionFeedback.NearMiss)
                        JudgeResult.MISMATCH ->
                            state.withNonCorrect(outcome, SessionFeedback.Mismatch(outcome.misconceptionHint))
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (completed: SessionCompletedException) {
                // 경합 등으로 이미 완료됐다면 막다른 길을 만들지 않고 완료 화면으로 재동기화한다.
                load()
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state is SessionUiState.Active) state.copy(isSubmitting = false, systemError = true) else state
                }
            }
        }
    }

    /** 정답 맞힌 뒤 다음 칸으로 진행한다(입력·피드백·공개 초기화). */
    fun advance() {
        _uiState.update { state ->
            if (state !is SessionUiState.Active) return@update state
            val next = state.pendingNext ?: return@update state
            SessionUiState.Active(
                problem = next,
                position = state.pendingPosition ?: state.position,
                total = state.total,
                solvedCount = state.solvedCount,
                revealedHints = next.revealedHints,
            )
        }
    }

    /**
     * 정답 공개(안전판). 모범답안·개념·해설을 받아 보여 준다. 공개 후에도 **직접 따라 입력**해야 다음으로 넘어간다
     * (서버는 정답 제출에만 진행하므로 자연히 강제된다 — 벌이 아니라 손으로 익히기).
     */
    fun requestReveal() {
        val current = _uiState.value
        if (current !is SessionUiState.Active || current.isRevealing || current.reveal != null) return

        _uiState.value = current.copy(isRevealing = true, systemError = false)
        viewModelScope.launch {
            try {
                val reveal = repository.reveal()
                _uiState.update { state ->
                    if (state is SessionUiState.Active) state.copy(reveal = reveal, isRevealing = false) else state
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (completed: SessionCompletedException) {
                load()
            } catch (notAllowed: RevealNotAllowedException) {
                // UI가 최소 한 번 시도한 뒤에만 공개 버튼을 보이므로 보통 도달하지 않는다.
                // 도달해도 처벌이 아니므로 조용히 진행 중 표시만 내린다.
                _uiState.update { state ->
                    if (state is SessionUiState.Active) state.copy(isRevealing = false) else state
                }
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state is SessionUiState.Active) state.copy(isRevealing = false, systemError = true) else state
                }
            }
        }
    }

    /**
     * 다음 힌트 하나를 연다(pull, 약→강). 서버 왕복이다 — 힌트 열람은 학습 기록이라 영속돼야 한다(§3.2).
     * 서버 응답의 공개 목록이 원천이므로, 성공했을 때만 [SessionUiState.Active.revealedHints]가 늘어난다.
     *
     * ⭐️ 열람 실패(네트워크 등)는 세션 진행을 막지 않는다 — 진행 표시만 내리고 조용히 둔다(무낙인, systemError 아님).
     * 이미 요청 중이거나 더 열 힌트가 없으면 아무 것도 하지 않는다.
     */
    fun revealNextHint() {
        val current = _uiState.value
        if (current !is SessionUiState.Active || current.isRevealingHint || !current.hasMoreHints) return

        _uiState.value = current.copy(isRevealingHint = true)
        viewModelScope.launch {
            try {
                val result = repository.revealHint(current.revealedHintCount)
                _uiState.update { state ->
                    if (state is SessionUiState.Active) {
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
                    if (state is SessionUiState.Active) state.copy(isRevealingHint = false) else state
                }
            }
        }
    }

    /** 지난 문제 다시 보기(읽기 전용) 열기. position은 이미 통과한 칸(0-based). */
    fun openPast(position: Int) {
        val current = _uiState.value
        if (current !is SessionUiState.Active) return
        if (position < 0 || position >= current.position) return

        _uiState.value = current.copy(past = PastView.Loading)
        viewModelScope.launch {
            _uiState.update { state ->
                if (state !is SessionUiState.Active) return@update state
                try {
                    state.copy(past = PastView.Loaded(repository.getPastItem(position)))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    state.copy(past = PastView.Error)
                }
            }
        }
    }

    /** 지난 문제 보기 닫고 현재 풀이로 복귀. */
    fun closePast() {
        _uiState.update { state ->
            if (state is SessionUiState.Active) state.copy(past = null) else state
        }
    }

    /**
     * 비정답(불일치·근접) 응답 반영. 서버는 정답에만 커서를 전진시키므로 보통 같은 칸이 돌아온다 —
     * 그때는 피드백만 얹는다(입력은 고칠 수 있게 남긴다).
     *
     * 다른 칸이 돌아왔다면 클라이언트가 낡은 것이므로 서버 쪽 칸으로 되맞춘다(서버가 진실). 응답의
     * position·currentProblem을 버리면 낡은 문제를 계속 그리게 되고, 그 사이 다음 문제는 화면에 한 번도
     * 뜨지 못한 채 소비된다 — 표시 문제가 아니라 학습 손실이다. 되맞출 때 오답 넛지는 얹지 않는다:
     * 사용자가 본 적 없는 문제에 '아직이에요'를 붙이면 없던 오답을 뒤집어씌우는 셈이다(무낙인).
     */
    private fun SessionUiState.Active.withNonCorrect(
        outcome: AttemptOutcome,
        feedback: SessionFeedback,
    ): SessionUiState.Active {
        val serverProblem = outcome.currentProblem
        if (serverProblem == null || serverProblem.id == problem.id) {
            return copy(feedback = feedback, isSubmitting = false)
        }
        return SessionUiState.Active(
            problem = serverProblem,
            position = outcome.position,
            total = outcome.totalCount,
            solvedCount = outcome.solvedCount,
            revealedHints = serverProblem.revealedHints,
        )
    }

    private fun DailySession.toSummary() = CompletionSummary(solvedCount, totalCount, streak)
    private fun AttemptOutcome.toSummary() = CompletionSummary(solvedCount, totalCount, streak)
}

/** 03 세션 풀이 화면 상태. */
sealed interface SessionUiState {
    data object Loading : SessionUiState

    /** 세션 로드 실패(시스템 오류). 재시도 가능. */
    data object Error : SessionUiState

    /**
     * 풀이 중. [position]은 지금 칸(0-based) → 표시용 번호는 position+1.
     */
    data class Active(
        val problem: SessionProblem,
        val position: Int,
        val total: Int,
        val solvedCount: Int,
        val inputText: String = "",
        val feedback: SessionFeedback? = null,
        val isSubmitting: Boolean = false,
        val systemError: Boolean = false,
        val reveal: Reveal? = null,
        val isRevealing: Boolean = false,
        val pendingNext: SessionProblem? = null,
        val pendingPosition: Int? = null,
        val past: PastView? = null,
        // 공개된 힌트 본문(약→강). 서버 응답이 원천 — 새 문제 진입 시 problem.revealedHints로 복원한다(재진입 복원).
        val revealedHints: List<SessionHint> = emptyList(),
        val isRevealingHint: Boolean = false,
    ) : SessionUiState {
        /** 표시용 진행 번호(1-based). */
        val current: Int get() = position + 1

        /** 정답을 맞혀 다음으로 넘어갈 수 있는 상태. */
        val solved: Boolean get() = feedback is SessionFeedback.Correct

        /** 되돌아볼 지난 문제가 있는지. */
        val hasPast: Boolean get() = position > 0

        /** 지금까지 공개한 힌트 수(서버가 원천 — 목록 크기로만 센다). */
        val revealedHintCount: Int get() = revealedHints.size

        /** 아직 더 열 힌트가 있는지(전체 hintCount 대비). 없으면 진입점을 그리지 않는다. */
        val hasMoreHints: Boolean get() = revealedHints.size < problem.hintCount

        /**
         * '정답 보기'를 제안할 수 있는지 — ⭐️ 최소 한 번 오답(불일치·근접)을 낸 뒤에만(no-leak 안전판).
         * 이미 공개했거나 정답을 맞힌 상태에선 제안하지 않는다. 서버의 REVEAL_NOT_ALLOWED(오답 전 공개 금지)와 일치.
         */
        val canReveal: Boolean
            get() = reveal == null && !solved &&
                (feedback is SessionFeedback.Mismatch || feedback is SessionFeedback.NearMiss)
    }
}

/** 제출 피드백. 세 상태 모두 비처벌. 개념·해설은 정답일 때만. */
sealed interface SessionFeedback {
    /**
     * [concepts]: 태깅 순서를 보존한 개념 목록(첫 번째가 대표 개념). 문제가 개념 여러 개에 태깅될 수 있다.
     * [enrichment]: '더 알아보기'(§5.7) — 없어도 되는 선택 콘텐츠.
     */
    data class Correct(
        val concepts: List<String>?,
        val explanation: String?,
        val enrichment: String? = null,
    ) : SessionFeedback

    /**
     * 불일치. [misconceptionHint]는 큐레이션된 예상 오답과 일치할 때만 실린다(push·자동, 기능 2.5) — 보통 null이다.
     * 무낙인: 있어도 오답 확정 아님, 정답 비노출. danger 톤 금지(교정 카드는 info 톤).
     */
    data class Mismatch(val misconceptionHint: String? = null) : SessionFeedback
    data object NearMiss : SessionFeedback
}

/** 지난 문제 읽기 전용 오버레이 상태. */
sealed interface PastView {
    data object Loading : PastView
    data class Loaded(val item: PastItem) : PastView
    data object Error : PastView
}

/** 화면이 소비하는 일회성 이벤트. */
sealed interface SessionEvent {
    /** 세션 완료 — 04 완료 화면으로. */
    data class Completed(val summary: CompletionSummary) : SessionEvent
}

/** 04 완료 화면에 넘길 요약. */
data class CompletionSummary(
    val solvedCount: Int,
    val totalCount: Int,
    val streak: Streak?,
)
