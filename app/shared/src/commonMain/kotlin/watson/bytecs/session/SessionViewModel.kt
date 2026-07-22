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
import watson.bytecs.problem.Enrichment
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

    // '조금 더 풀기'(D6·D9 일원화 — 추가 학습 폐지) 재진입 여부. 이 뷰모델 인스턴스는 여러 화면 진입에 걸쳐
    // 재사용될 수 있어(홈↔세션 왕복) 생성자 파라미터가 아니라 진입마다 [loadSession]으로 갱신한다.
    // 재시도([onRetry] = loadSession())는 인자 없이 불리므로 마지막 진입 방식을 그대로 재사용한다.
    private var startNext = false

    /**
     * 오늘의 세션을 불러온다. 이미 완료됐으면 곧장 완료 이벤트로 넘긴다(막다른 길 방지). 오류 재시도에도 쓰인다.
     * [startNext]가 true면 `GET /today` 대신 `POST /today/next`로 '조금 더 풀기' 재진입을 수행한다 —
     * 생략하면(재시도) 직전 진입에서 정한 방식을 그대로 따른다.
     */
    fun loadSession(startNext: Boolean = this.startNext) {
        this.startNext = startNext
        load()
    }

    private fun load() {
        _uiState.value = SessionUiState.Loading
        viewModelScope.launch {
            try {
                val session = if (startNext) repository.startNextSession() else repository.getToday()
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
                reportStarted()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.value = SessionUiState.Error
            }
        }
    }

    /**
     * 풀이 화면 진입을 서버에 표시한다(테스터 지표 수집). 별도 코루틴으로 발사해 화면 렌더를 막지 않고,
     * 실패해도 조용히 무시한다 — 지표 기록은 부수 효과이며 학습 흐름을 막지 않는다(무낙인). 서버가 멱등이라
     * 재진입(홈↔세션 왕복·재시도)에 매번 불려도 최초 진입 시각만 남는다.
     */
    private fun reportStarted() {
        viewModelScope.launch {
            try {
                repository.markStarted()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // 지표 기록 실패는 풀이를 막지 않는다 — 조용히 무시한다.
            }
        }
    }

    /**
     * 답 입력 변경. 직전 제출의 **판정 피드백**(불일치 주황 플래시·'정답과 달라요' 라이브 리전·정답 햅틱)과
     * 전송 실패 표시는 지운다 — 고친 답은 아직 채점 전이므로 이전 판정을 붙여 두지 않는다(그래야 다음
     * 제출에서 같은 유형 오답이어도 플래시·낭독이 다시 발화한다).
     *
     * ⭐️ [실기기 QA] 다만 **오개념 교정 힌트**는 [SessionUiState.Active.stickyMisconceptionHint]로 따로
     * 보존해, 사용자가 힌트를 읽으면서 답을 고칠 수 있게 한다(예전엔 한 글자만 바꿔도 힌트가 사라져 읽으려면
     * 타이핑을 멈춰야 했다). 힌트는 다음 제출([submit])이 새 값으로 갈아끼우거나 다음 칸([advance])에서 비운다.
     */
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
                // ⭐️ [결정 2026-07-16] 마지막 본 문제를 맞혀 세션이 완료돼도 곧장 완료 이벤트를 보내지 않는다.
                //    이 문제의 피드백(개념·해설·심화)을 다른 문제와 동등하게 보여준 뒤, 사용자가 [한입 마치기]를
                //    눌러야([finishSession]) 완료 이벤트가 나간다 — 그래야 마지막 문제라는 이유로 해설을
                //    못 보고 넘어가는 일이 없다. 완료 요약은 이 응답에 이미 실려 있으므로 [pendingCompletion]에
                //    보존해 두고, 전환은 순수하게 화면 타이밍 문제로 미룬다.
                _uiState.update { state ->
                    if (state !is SessionUiState.Active) return@update state
                    when (outcome.result) {
                        JudgeResult.CORRECT -> state.copy(
                            feedback = SessionFeedback.Correct(
                                concepts = outcome.concepts,
                                explanation = outcome.explanation,
                                enrichment = outcome.enrichment,
                                representativeAnswer = outcome.representativeAnswer,
                            ),
                            // 다음 칸은 [advance]에서 실제로 이동한다(정답 후 [다음 문제] CTA).
                            // 완료된 경우 서버가 currentProblem을 null로 주므로 pendingNext는 자연히 비고,
                            // [advance]는 no-op이 된다 — CTA가 [finishSession]으로 갈라지는 이유다.
                            pendingNext = outcome.currentProblem,
                            pendingPosition = outcome.position,
                            pendingCompletion = if (outcome.isCompleted) outcome.toSummary() else null,
                            solvedCount = outcome.solvedCount,
                            // 공개 후 따라 입력해 맞힌 경우, 정답 카드 옆에 낡은 공개 패널이 남지 않게 지운다.
                            reveal = null,
                            isRevealing = false,
                            isSubmitting = false,
                            // 정답을 맞혔으면 직전 오답의 교정 힌트는 더 볼 이유가 없어 비운다.
                            stickyMisconceptionHint = null,
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
     * 마지막 본 문제까지 맞혀 세션이 완료됐을 때, [한입 마치기]를 눌러 완료 화면으로 넘어간다.
     * [pendingCompletion]이 없으면(완료 대상이 아니거나 이미 한 번 보냈으면) 아무것도 하지 않는다.
     *
     * ⭐️ 이중 탭·백 버튼 경계: 보내기 전에 [pendingCompletion]을 먼저 비운다 — 그래서 화면 전환 전에
     * 버튼을 두 번 눌러도 두 번째 호출은 여기서 조용히 막히고, 완료 이벤트는 정확히 한 번만 나간다.
     */
    fun finishSession() {
        val current = _uiState.value
        if (current !is SessionUiState.Active) return
        val summary = current.pendingCompletion ?: return

        _uiState.value = current.copy(pendingCompletion = null)
        viewModelScope.launch {
            _events.send(SessionEvent.Completed(summary))
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
     * 그때는 피드백을 얹고 문제 데이터도 서버 응답으로 갱신한다(입력은 고칠 수 있게 남긴다). 갱신이
     * 필요한 이유(D2): 같은 칸이어도 [SessionProblem.wrongAttemptCount]는 이 제출로 늘었으므로, 서버가
     * 준 최신 값을 반영해야 재시도 안내("표기가 달라 인식 못 했을 수 있어요")가 즉시 정확해진다.
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
        // 오개념 교정 힌트는 편집 중에도 읽을 수 있게 따로 보존한다 — 불일치일 때만 채워지고, 근접이면 비운다.
        val sticky = (feedback as? SessionFeedback.Mismatch)?.misconceptionHint
        val serverProblem = outcome.currentProblem
            ?: return copy(feedback = feedback, isSubmitting = false, stickyMisconceptionHint = sticky)
        if (serverProblem.id == problem.id) {
            return copy(problem = serverProblem, feedback = feedback, isSubmitting = false, stickyMisconceptionHint = sticky)
        }
        return SessionUiState.Active(
            problem = serverProblem,
            position = outcome.position,
            total = outcome.totalCount,
            solvedCount = outcome.solvedCount,
            revealedHints = serverProblem.revealedHints,
        )
    }

    private fun DailySession.toSummary() = CompletionSummary(solvedCount, totalCount, streak, needsDifficultyPrompt)
    private fun AttemptOutcome.toSummary() = CompletionSummary(solvedCount, totalCount, streak, needsDifficultyPrompt)
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
        // 오개념 교정 힌트(불일치일 때만). feedback과 달리 답 편집으로는 지워지지 않아, 힌트를 읽으면서
        // 답을 고칠 수 있다(실기기 QA). 다음 제출이 갈아끼우거나 다음 칸으로 넘어가면 비워진다.
        val stickyMisconceptionHint: String? = null,
        val isSubmitting: Boolean = false,
        val systemError: Boolean = false,
        val reveal: Reveal? = null,
        val isRevealing: Boolean = false,
        val pendingNext: SessionProblem? = null,
        val pendingPosition: Int? = null,
        // 마지막 본 문제를 맞혀 세션이 완료됐을 때만 채워진다(§ SessionViewModel.submit). [finishSession]이
        // 이걸 넣어 완료 이벤트를 보내고 나면 다시 null로 비운다(이중 탭 가드). 이 값이 있는 동안은 이
        // 문제의 피드백(개념·해설·심화)이 04로 즉시 넘어가지 않고 화면에 그대로 남는다(2026-07-16 결정).
        val pendingCompletion: CompletionSummary? = null,
        val past: PastView? = null,
        // 공개된 힌트 본문(약→강). 서버 응답이 원천 — 새 문제 진입 시 problem.revealedHints로 복원한다(재진입 복원).
        val revealedHints: List<SessionHint> = emptyList(),
        val isRevealingHint: Boolean = false,
    ) : SessionUiState {
        /** 표시용 진행 번호(1-based). */
        val current: Int get() = position + 1

        /** 정답을 맞혀 다음으로 넘어갈 수 있는 상태. */
        val solved: Boolean get() = feedback is SessionFeedback.Correct

        /** 방금 맞힌 문제가 세션의 마지막 본 문제였는지 — CTA가 [다음 문제] 대신 [한입 마치기]로 바뀐다. */
        val isLastProblem: Boolean get() = pendingCompletion != null

        /** 되돌아볼 지난 문제가 있는지. */
        val hasPast: Boolean get() = position > 0

        /** 지금까지 공개한 힌트 수(서버가 원천 — 목록 크기로만 센다). */
        val revealedHintCount: Int get() = revealedHints.size

        /** 아직 더 열 힌트가 있는지(전체 hintCount 대비). 없으면 진입점을 그리지 않는다. */
        val hasMoreHints: Boolean get() = revealedHints.size < problem.hintCount

        /**
         * '정답 보기'를 제안할 수 있는지 — ⭐️ [결정 2026-07-17] 시도 전에도 사용자가 원하면 열 수 있다(선행
         * 오답 요구 폐지, 무낙인). 이미 공개했거나 정답을 맞힌 상태에서만 제안하지 않는다(불변식 3: 요청 시에만 공개).
         */
        val canReveal: Boolean
            get() = reveal == null && !solved
    }
}

/** 제출 피드백. 세 상태 모두 비처벌. 개념·해설은 정답일 때만. */
sealed interface SessionFeedback {
    /**
     * [concepts]: 태깅 순서를 보존한 개념 목록(첫 번째가 대표 개념). 문제가 개념 여러 개에 태깅될 수 있다.
     * [enrichment]: '더 알아보기'(§5.7) — 없어도 되는 선택 콘텐츠.
     * [representativeAnswer]: 화면 표시용 대표 정답 — 확정 입력란은 제출 텍스트가 아니라 이 값을 보여준다
     * ([2026-07-16] 오너 결정).
     */
    data class Correct(
        val concepts: List<String>?,
        val explanation: String?,
        val enrichment: Enrichment? = null,
        val representativeAnswer: String? = null,
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

/**
 * 04 완료 화면에 넘길 요약.
 *  - [needsDifficultyPrompt]: 선호 난이도 제안 카드(구성 8, DF1) 노출 여부 — 서버가 준 단일 출처 신호를
 *    그대로 실어 나른다(클라이언트가 조건을 재계산하지 않는다).
 */
data class CompletionSummary(
    val solvedCount: Int,
    val totalCount: Int,
    val streak: Streak?,
    val needsDifficultyPrompt: Boolean = false,
)
