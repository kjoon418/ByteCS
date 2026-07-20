package watson.bytecs.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import watson.bytecs.PhysicalEnterKey
import watson.bytecs.report.ReportCategory
import watson.bytecs.scrap.ScrapRepository
import watson.bytecs.ui.components.AnswerTextField
import watson.bytecs.ui.components.BcsHint
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.CategoryBadge
import watson.bytecs.ui.components.CodeSnippetBlock
import watson.bytecs.ui.components.ConceptChips
import watson.bytecs.ui.components.ConfirmedAnswerField
import watson.bytecs.ui.components.DifficultyIndicator
import watson.bytecs.ui.components.EnrichmentBlock
import watson.bytecs.ui.components.ErrorBanner
import watson.bytecs.ui.components.GhostButton
import watson.bytecs.ui.components.HintStepper
import watson.bytecs.ui.components.MisconceptionHintCard
import watson.bytecs.ui.components.NearMissNudge
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.RevealAnswerButton
import watson.bytecs.ui.components.RevealedAnswerField
import watson.bytecs.ui.components.ScrapToggle
import watson.bytecs.ui.components.SessionProgress
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.components.TypeAlongField
import watson.bytecs.ui.components.categoryLabel
import watson.bytecs.ui.components.difficultyLabel
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.BcsMotion
import watson.bytecs.ui.theme.LocalBcsColors
import watson.bytecs.ui.theme.LocalBcsType
import kotlin.coroutines.cancellation.CancellationException

/** D2: 이 횟수 이상 같은 칸에서 비정답이 누적되면 "표기가 달라 인식 못 했을 수 있어요" 안내를 보탠다. */
private const val RETRY_HINT_THRESHOLD = 2

/**
 * 03 문제 풀이(세션 연동). 오늘의 한입 중 문제 하나를 푼다. 서비스의 히어로 화면.
 *
 * ⭐️ 무낙인: 불일치·근접에 빨강·경고·벌점 금지. 정답은 긍정, 불일치·근접은 중립·격려 톤으로 또렷하게.
 * ⭐️ 정답 공개 후에는 모범답안을 직접 따라 입력해야 진행된다(벌이 아니라 손으로 익히기).
 *
 * @param onCompleted 세션 완료(일회성 이벤트) → 04 완료 화면.
 * @param onExit 부담 없는 나가기 → 홈(언제든 이어서).
 * @param onReport 신고 화면으로 이동(문제 id + 프리셋 유형). 상단 바 '오류 신고'는 프리셋 없이(null) 부르고,
 * 정답 공개 패널의 '내 답이 맞았던 것 같아요'는 [ReportCategory.WRONG_ANSWER]를 프리셋으로 넘긴다(D2).
 * @param startNext '조금 더 풀기'로 진입했는지(D6·D9 일원화 — 추가 학습 폐지). true면 `GET /today` 대신
 * `POST /today/next`로 새 세션을 만들어(오늘 최신이 완료 상태일 때) 재진입한다. 기본(false)은 기존 시작·이어서.
 */
@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    onCompleted: (CompletionSummary) -> Unit,
    onExit: () -> Unit,
    onReport: (Long, ReportCategory?) -> Unit,
    scrapRepository: ScrapRepository,
    startNext: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 화면 진입(홈에서 시작·이어서·조금 더 풀기)마다 오늘 상태를 새로 반영한다(뷰모델이 내비게이션 간 재사용돼도 재개가 정확).
    LaunchedEffect(Unit) {
        viewModel.loadSession(startNext = startNext)
    }

    // 세션 완료는 일회성 이벤트 — 정확히 한 번 04로 넘긴다.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is SessionEvent.Completed) onCompleted(event.summary)
        }
    }

    // ⭐️ 스크랩 토글은 정답 접근이 이미 가능한 맥락에만 노출한다(정답/공개 후·지난 문제) — 미해결 문제 유출 방지.
    //    상태는 낙관적으로 문제 id별로 들고, 서버 반영 실패 시 되돌린다(재열람 화면과 같은 규칙). 세션을 벗어나면 사라진다.
    val scrapped = remember { mutableStateMapOf<Long, Boolean>() }
    val scope = rememberCoroutineScope()
    val toggleScrap: (Long) -> Unit = { problemId ->
        val target = !(scrapped[problemId] ?: false)
        scrapped[problemId] = target
        scope.launch {
            try {
                if (target) scrapRepository.add(problemId) else scrapRepository.remove(problemId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // 반영 실패 — 낙관적 표시를 이전 상태로 되돌린다(멱등 서버라 재시도는 다음 토글로 자연히 이뤄진다).
                scrapped[problemId] = !target
            }
        }
    }

    SessionScreenContent(
        state = state,
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        onAdvance = viewModel::advance,
        onFinish = viewModel::finishSession,
        onReveal = viewModel::requestReveal,
        onRevealHint = viewModel::revealNextHint,
        onOpenPast = viewModel::openPast,
        onClosePast = viewModel::closePast,
        onRetry = viewModel::loadSession,
        onExit = onExit,
        onReport = onReport,
        scrappedProblemIds = scrapped.filterValues { it }.keys,
        onToggleScrap = toggleScrap,
        modifier = modifier,
    )
}

@Composable
internal fun SessionScreenContent(
    state: SessionUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAdvance: () -> Unit,
    onFinish: () -> Unit,
    onReveal: () -> Unit,
    onOpenPast: (Int) -> Unit,
    onClosePast: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onRevealHint: () -> Unit = {},
    onReport: (Long, ReportCategory?) -> Unit = { _, _ -> },
    scrappedProblemIds: Set<Long> = emptySet(),
    onToggleScrap: (Long) -> Unit = {},
) {
    val active = state as? SessionUiState.Active

    BcsScaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active != null) {
                    SessionProgressEntry(
                        active = active,
                        onOpenPast = { onOpenPast(active.position - 1) },
                    )
                }
                Spacer(Modifier.weight(1f))
                // 콘텐츠 오류 신고(07) — 풀이 중(공개 전)에도 언제든 열려 있다. 신고 화면은 유형만 받으므로
                //    정답을 유출하지 않는다. 키보드가 올라오면 좁아지는 세로 공간을 아끼려 본문 아래 행에서 탑바로 올린다.
                if (active != null && active.past == null) {
                    TextLink(
                        text = "오류 신고",
                        onClick = { onReport(active.problem.id, null) },
                        color = LocalBcsColors.current.textTertiary,
                        contentDescription = "이 문제의 콘텐츠 오류 신고",
                    )
                    Spacer(Modifier.width(BcsDimens.space4))
                }
                // 부담 없는 나가기(경고 모달 없음).
                // ⭐️ '세션'은 내부 용어다 — 눈으로 보는 사용자가 어디서도 볼 수 없는 단어를 스크린리더
                //    사용자만 듣게 두지 않는다. 사용자에게 이건 '오늘의 한입'이다.
                TextLink(
                    text = "나가기",
                    onClick = onExit,
                    color = LocalBcsColors.current.textSecondary,
                    contentDescription = "오늘의 한입에서 나가기, 언제든 이어서 할 수 있어요",
                )
            }
        },
        bottomBar = {
            if (active != null && active.past == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                ) {
                    // ⭐️ [결정 2026-07-16] 세션의 마지막 본 문제를 맞히면 CTA가 [다음 문제] 대신
                    //    [한입 마치기]로 바뀐다 — 눌러야 04 완료 화면으로 넘어간다(§ SessionViewModel.finishSession).
                    PrimaryButton(
                        text = when {
                            active.isLastProblem -> "한입 마치기"
                            active.solved -> "다음 문제"
                            else -> "제출하기"
                        },
                        onClick = when {
                            active.isLastProblem -> onFinish
                            active.solved -> onAdvance
                            else -> onSubmit
                        },
                        enabled = active.solved || active.inputText.isNotBlank(),
                        loading = active.isSubmitting,
                    )
                }
            }
        },
    ) {
        when (state) {
            SessionUiState.Loading -> SessionSkeleton(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = BcsDimens.space5),
            )

            SessionUiState.Error -> SessionError(
                onRetry = onRetry,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = BcsDimens.space5),
            )

            is SessionUiState.Active -> {
                val past = state.past
                if (past != null) {
                    PastItemView(
                        past = past,
                        onClose = onClosePast,
                        scrappedProblemIds = scrappedProblemIds,
                        onToggleScrap = onToggleScrap,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = BcsDimens.space5),
                    )
                } else {
                    ActiveContent(
                        state = state,
                        onInputChange = onInputChange,
                        onSubmit = onSubmit,
                        onAdvance = onAdvance,
                        onFinish = onFinish,
                        onReveal = onReveal,
                        onRevealHint = onRevealHint,
                        onReport = onReport,
                        scrappedProblemIds = scrappedProblemIds,
                        onToggleScrap = onToggleScrap,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = BcsDimens.space5),
                    )
                }
            }
        }
    }
}

/**
 * 진행 인디케이터 자체가 '지난 문제 다시 보기' 진입점이다(시안 A) — 되돌아볼 칸이 있을 때만 눌린다.
 *
 * 진입점을 진행 표시에 겹쳐 둔 이유: 되돌아보고 싶은 순간 사용자가 보는 곳이 "지금 몇 번째인가"이고,
 * 별도 링크를 나란히 두면 상단 바에 secondary 액션이 둘로 늘어 '나가기'와 경쟁한다.
 * 눌림 라벨만 따로 실어 준다 — 진행도 낭독("총 N문제 중 M번째")을 덮어쓰지 않기 위해서다.
 */
@Composable
private fun SessionProgressEntry(
    active: SessionUiState.Active,
    onOpenPast: () -> Unit,
) {
    val colors = LocalBcsColors.current
    val canOpenPast = active.hasPast && active.past == null

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(BcsDimens.radiusSm))
            .clickable(
                enabled = canOpenPast,
                onClickLabel = "지난 문제 다시 보기",
                onClick = onOpenPast,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionProgress(current = active.current, total = active.total)
        // 누를 수 있다는 신호. 장식이므로 시맨틱을 비운다 — 의미는 위 눌림 라벨이 전달한다.
        if (canOpenPast) {
            Text(
                text = "›",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textTertiary,
                modifier = Modifier
                    .padding(start = BcsDimens.space1)
                    .clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun ActiveContent(
    state: SessionUiState.Active,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAdvance: () -> Unit,
    onFinish: () -> Unit,
    onReveal: () -> Unit,
    onRevealHint: () -> Unit,
    onReport: (Long, ReportCategory?) -> Unit,
    scrappedProblemIds: Set<Long>,
    onToggleScrap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current

    // ⭐️ 엔터는 하단 CTA와 **같은 곳으로** 간다. 정답 상태의 CTA는 '다음 문제'(마지막 문제면 '한입 마치기')인데
    //    엔터만 제출로 남으면, 엔터로 맞힌 사람이 자연히 한 번 더 누를 때 낡은 입력이 다시 나간다(뷰모델이
    //    막지만, 그러면 엔터가 아무 일도 하지 않아 막힌 느낌을 준다). 같은 자리에서 같은 뜻이어야 한다.
    val onImeSubmit = when {
        state.isLastProblem -> onFinish
        state.solved -> onAdvance
        else -> onSubmit
    }

    // ⭐️ 정답 후 물리 Enter를 하단 CTA와 같은 동작(다음 문제 / 마지막이면 마치기)으로 잇는다.
    //    정답 순간 편집 입력칸이 확정 표시로 교체돼 IME 액션(Enter)을 받을 곳이 사라진다. 웹에서는
    //    Compose 포커스만으론 캔버스가 브라우저 키를 못 받아(클릭 전까지 무반응) window 레벨 Enter
    //    리스너로 우회한다(모바일은 물리 키가 없어 no-op). solved 동안만 활성 — 정답을 제출한 그 Enter는
    //    리스너 등록 이전 이벤트라 곧장 다음 문제로 건너뛰는 이중 발화가 없다.
    PhysicalEnterKey(enabled = state.solved, onEnter = onImeSubmit)

    // 새 문제가 오면 입력에 자동 포커스. 공개 후에는 따라 입력 칸으로 바뀌어 이 requester가 붙을 곳이 없다.
    LaunchedEffect(state.problem.id) {
        if (state.reveal == null) focusRequester.requestFocus()
    }
    // 오답(불일치) 순간 문제 영역을 주황으로 잠깐 물들였다 되돌린다 — 텍스트 카드 대신 무낙인 색 이펙트로 알린다.
    var retryFlashing by remember { mutableStateOf(false) }
    val problemTint by animateColorAsState(
        targetValue = if (retryFlashing) colors.retryFlash else Color.Transparent,
        animationSpec = tween(BcsMotion.durFast, easing = BcsMotion.easing),
        label = "retryFlash",
    )

    // 정답 순간 햅틱(긍정 피드백을 손끝으로) · 오답(불일치) 순간 주황 플래시(같은 피드백 훅에 얹는다).
    LaunchedEffect(state.feedback) {
        when (state.feedback) {
            is SessionFeedback.Correct -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            is SessionFeedback.Mismatch -> {
                // 주황으로 물들었다(durFast) 잠깐 유지 후 원색으로 돌아온다.
                retryFlashing = true
                delay(300)
                retryFlashing = false
            }
            else -> Unit
        }
    }

    Column(modifier = modifier) {
        Spacer(Modifier.height(BcsDimens.space2))

        // ⭐️ 문제 영역의 감쇠(03 정답 상태 시안 43행) — 정답을 맞힌 뒤에는 문제가 더 이상 화면의 초점이
        //    아니므로 시각적으로 한 발 물러난다. opacity만 낮출 뿐 트리에서 지우지 않으므로 스크린리더
        //    낭독은 그대로 유지된다(§7).
        Column(
            modifier = Modifier
                .then(if (state.solved || state.reveal != null) Modifier.alpha(0.6f) else Modifier)
                .clip(RoundedCornerShape(BcsDimens.radiusCard))
                .background(problemTint)
                .padding(BcsDimens.space3),
        ) {
            // 난이도(왼쪽)와 '다시 볼래요' 스크랩(오른쪽)을 한 행에 둔다. ⭐️ 스크랩은 개인 북마크일 뿐
            //    모범답안을 노출하지 않으므로(도메인 §5) 풀이 중에도 열어 둔다 — 정답 접근 게이트와 무관하다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 난이도 + 대표 분류 배지(§7, 풀기 전부터) — 개념 칩과 달리 스포일 위험이 낮아 항상 함께 보인다.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(BcsDimens.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    difficultyLabel(state.problem.difficulty)?.let { label ->
                        DifficultyIndicator(label)
                    }
                    categoryLabel(state.problem.category)?.let { label ->
                        CategoryBadge(label)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "다시 볼래요",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.width(BcsDimens.space2))
                ScrapToggle(
                    scrapped = state.problem.id in scrappedProblemIds,
                    onToggle = { onToggleScrap(state.problem.id) },
                )
            }
            Spacer(Modifier.height(BcsDimens.space2))

            Text(text = state.problem.question, style = LocalBcsType.current.question, color = colors.textPrimary)

            state.problem.codeSnippet?.let { snippet ->
                Spacer(Modifier.height(BcsDimens.space4))
                CodeSnippetBlock(code = snippet)
            }
        }

        Spacer(Modifier.height(BcsDimens.space6))

        val reveal = state.reveal
        if (reveal == null) {
            if (state.solved) {
                // ⭐️ 정답 입력란의 확정 전환(시안 55-60행) — 더 이상 편집 대상이 아니므로 입력칸 대신
                //    확정 표시로 바꾼다. 은은한 축하 연출(§2.2): 짧게 페이드·스케일 인 되고, 04 세션 완료의
                //    컨페티보다 약하게 절제한다(위계: 문제 정답 < 세션 완료).
                val celebrate = remember(state.problem.id) {
                    MutableTransitionState(false).apply { targetState = true }
                }
                AnimatedVisibility(
                    visibleState = celebrate,
                    enter = fadeIn(tween(BcsMotion.durBase)) + scaleIn(tween(BcsMotion.durBase), initialScale = 0.97f),
                ) {
                    Column {
                        // 확정 입력란은 제출 텍스트가 아니라 대표 정답을 보여준다([2026-07-16] 오너 결정).
                        val representativeAnswer =
                            (state.feedback as? SessionFeedback.Correct)?.representativeAnswer ?: state.inputText
                        ConfirmedAnswerField(representativeAnswer = representativeAnswer)
                        state.feedback?.let { feedback ->
                            Spacer(Modifier.height(BcsDimens.space4))
                            FeedbackCard(feedback, problemId = state.problem.id)
                        }
                    }
                }
            } else {
                AnswerTextField(
                    value = state.inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.focusRequester(focusRequester),
                    placeholder = "정답을 입력해 보세요",
                    onImeSubmit = onImeSubmit,
                )

                // 피드백(있을 때만). 불일치·근접 모두 비처벌.
                state.feedback?.let { feedback ->
                    Spacer(Modifier.height(BcsDimens.space4))
                    FeedbackCard(feedback, problemId = state.problem.id)
                }

                // ⭐️ [실기기 QA] 오개념 교정 힌트 — feedback과 독립(stickyMisconceptionHint)이라 답을 고치는
                //    중에도 남는다. 큐레이션된 오답에만 실린다(보통 null이라 카드가 없다).
                state.stickyMisconceptionHint?.let { hint ->
                    Spacer(Modifier.height(BcsDimens.space4))
                    MisconceptionHintCard(text = hint)
                }

                // D2: 거짓 오답 완충 ③ — 재시도 N회(임계 RETRY_HINT_THRESHOLD)째도 계속 어긋나면, 큐레이션
                // 여부와 무관하게 "표기 차이일 수 있음"을 안내한다. wrongAttemptCount는 서버가 원천이라
                // 재진입해도(이 화면을 나갔다 돌아와도) 안내가 그대로 정확하다 — 이번 방문의 feedback 유무와 무관.
                if (state.problem.wrongAttemptCount >= RETRY_HINT_THRESHOLD) {
                    Spacer(Modifier.height(BcsDimens.space3))
                    Text(
                        text = "표기가 달라 인식 못 했을 수 있어요",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }

                // 힌트(pull) — 요청해야만 하나씩 열린다. 정답을 맞힌 뒤에는 더 볼 이유가 없어 감춘다
                // (§9 정보 위계 정돈). hintCount 0이면 HintStepper가 진입점을 그리지 않는다.
                if (state.problem.hintCount > 0) {
                    Spacer(Modifier.height(BcsDimens.space4))
                    HintStepper(
                        hints = state.hintList(),
                        revealedCount = state.revealedHintCount,
                        onRevealNext = onRevealHint,
                    )
                }

                // [정답 보기] — 최소 한 번 시도한 뒤에만(중립·secondary). 정답·공개 상태에선 숨김.
                if (state.canReveal) {
                    Spacer(Modifier.height(BcsDimens.space4))
                    RevealAnswerButton(onClick = onReveal)
                }
            }
        } else {
            // ⭐️ [2026-07-17 QA #4] 공개 레이아웃을 정답 시 배치와 통일한다 — 입력칸 자리에 정답 표시 필드를
            //    두고 **바로 아래**에 따라 입력 칸을 붙여, 입력 요소가 원래 자리 근처에 남게 한다(입력창이
            //    사라졌다는 착각 방지). "이 정답을 따라 적어 보세요"가 성립하려면 답이 위에 먼저 보여야 한다.
            RevealedAnswerField(representativeAnswer = reveal.representativeAnswer)
            Spacer(Modifier.height(BcsDimens.space4))
            TypeAlongField(
                value = state.inputText,
                onValueChange = onInputChange,
                onImeSubmit = onImeSubmit,
            )

            // D2: 거짓 오답 완충 ② — 순화 문구("아직이에요")×허용답 누락이 겹치면 맞았는지 틀렸는지 알 수
            // 없는 신뢰 붕괴가 생긴다. 정답 공개 후에도 여전히 안 맞았다면 "사실 내 답이 맞았을 수도 있다"는
            // 의심을 곧장 신고로 잇는다 — 기존 신고 유형(WRONG_ANSWER)을 재사용하므로 서버 계약 변경이 없다.
            Spacer(Modifier.height(BcsDimens.space2))
            TextLink(
                text = "내 답이 맞았던 것 같아요",
                onClick = { onReport(state.problem.id, ReportCategory.WRONG_ANSWER) },
                color = colors.textTertiary,
            )

            // 따라 적다 어긋나도 처벌이 아니다 — 같은 비처벌 넛지를 그대로 쓴다.
            state.feedback?.let { feedback ->
                Spacer(Modifier.height(BcsDimens.space4))
                FeedbackCard(feedback, problemId = state.problem.id)
            }

            // ⭐️ [실기기 QA] 오개념 교정 힌트 — 공개 후 따라 입력 중에도 편집과 무관하게 유지된다.
            state.stickyMisconceptionHint?.let { hint ->
                Spacer(Modifier.height(BcsDimens.space4))
                MisconceptionHintCard(text = hint)
            }

            // 해설·개념·심화는 정답 시(FeedbackCard Correct)와 같은 flat 순서로 그 아래에 둔다.
            //    개념은 공개 이후에만 — 풀기 전 노출은 정답 스포일이다(§5.9). 여러 개면 칩이 늘어난다(태깅 순).
            Spacer(Modifier.height(BcsDimens.space4))
            ConceptChips(reveal.concepts)
            reveal.explanation?.let {
                Spacer(Modifier.height(BcsDimens.space3))
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.textBody)
            }
            // '더 알아보기'(§5.7) — 정답 공개도 정답 접근이 허용된 맥락이라 노출한다.
            Spacer(Modifier.height(BcsDimens.space3))
            EnrichmentBlock(enrichment = reveal.enrichment)
        }

        // 시스템 오류(전송 실패) — 오답과 구분(§5.12), 안심 문구 우선 + 재시도 경로.
        if (state.systemError) {
            Spacer(Modifier.height(BcsDimens.space4))
            ErrorBanner(
                message = "잠시 연결이 원활하지 않았어요. 다시 시도해 주세요.",
                onRetry = onSubmit,
            )
        }

        Spacer(Modifier.height(BcsDimens.space6))
    }
}

/**
 * 세 피드백 상태 카드. 정답=긍정, 불일치=중립 넛지, 근접=info(정답·개념 비노출).
 * 시각 구현은 §5.6 공용 컴포넌트를 조립한다(추가 학습 화면도 같은 프리미티브로 동형 카드를 그린다).
 * 상태 변화를 스크린리더가 즉시 읽도록 라이브 리전을 씌운다.
 */
@Composable
private fun FeedbackCard(feedback: SessionFeedback, problemId: Long) {
    val colors = LocalBcsColors.current
    val announce = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (feedback) {
        // ⭐️ [2026-07-16] 확인("완벽해요! 정확한 정답입니다.")은 [ConfirmedAnswerField]가 이미 보여준다 —
        //    옛 "맞았어요!" 카드를 여기서 또 그리면 같은 뜻의 확인이 두 번 중복된다. 그 아래 시안 위계
        //    (개념 칩 → 해설 → 더 알아보기)만 잇는다. 해설(explanation)은 정답 시 반드시 노출돼야 하는
        //    정보(명세)이므로 카드로 감싸지 않고 본문 텍스트로 흘린다 — 시안도 확인 라인 다음을 별도
        //    카드 없이 개념 칩 → 심화 정보 카드로 바로 잇는 flat 구조라 같은 결을 따른다.
        is SessionFeedback.Correct -> Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space3)) {
            if (!feedback.concepts.isNullOrEmpty()) {
                ConceptChips(feedback.concepts)
            }
            feedback.explanation?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.textBody)
            }
            // '더 알아보기'(§5.7) — 정답 처리 즉시 바로 보인다(2026-07-16 결정, 토글 없음).
            EnrichmentBlock(enrichment = feedback.enrichment)
        }

        // 불일치엔 텍스트 카드를 두지 않는다 — 시각 신호는 문제 영역의 주황 플래시가 맡는다(세로 공간 절약).
        //    비시각 사용자에겐 라이브 리전으로만 짧게 안내한다: '오답/틀림/실패' 어휘 없이(무낙인).
        //    ⭐️ 큐레이션된 오답의 교정 힌트는 여기(feedback)가 아니라 stickyMisconceptionHint로 본문에서
        //    따로 렌더한다 — 답을 고치는 중에도 힌트가 남아야 하는데, feedback은 편집 시 지워지기 때문이다.
        is SessionFeedback.Mismatch -> Box(
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "정답과 달라요, 다시 시도해 보세요"
            },
        )

        SessionFeedback.NearMiss -> NearMissNudge(modifier = announce)
    }
}

/**
 * [HintStepper]에 넘길 [BcsHint] 목록. ⭐️ no-leak: 미공개 힌트 본문은 클라에 없으므로, 공개된 것만 실제 본문으로
 * 채우고 나머지는 **렌더되지 않는** 자리표시자로 채운다(HintStepper는 revealedCount 미만만 그린다). 목록 길이가
 * 전체 hintCount여야 '더 보기' 노출 판정(revealedCount < size)이 맞는다.
 */
private fun SessionUiState.Active.hintList(): List<BcsHint> {
    val revealed = revealedHints.map { BcsHint(text = it.text, codeSnippet = it.codeSnippet) }
    val hidden = (problem.hintCount - revealed.size).coerceAtLeast(0)
    return revealed + List(hidden) { BcsHint(text = "") }
}

/** 지난 문제 읽기 전용 뷰. 문제·내 답·모범답안·개념·해설. 이미 통과한 칸이라 스크랩 토글도 함께 둔다. */
@Composable
private fun PastItemView(
    past: PastView,
    onClose: () -> Unit,
    scrappedProblemIds: Set<Long>,
    onToggleScrap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BcsDimens.space4)) {
        Spacer(Modifier.height(BcsDimens.space2))
        when (past) {
            PastView.Loading -> Text("불러오는 중…", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            PastView.Error -> {
                Text("지난 문제를 불러오지 못했어요.", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                GhostButton(text = "돌아가기", onClick = onClose)
            }
            is PastView.Loaded -> {
                val item = past.item
                // 지난 문제 헤더 옆에 스크랩 토글 — 이미 정답 접근이 가능한 맥락이라 유출 우려가 없다.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(BcsDimens.space2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("지난 문제 ${item.position + 1}", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        categoryLabel(item.category)?.let { label -> CategoryBadge(label) }
                    }
                    Spacer(Modifier.weight(1f))
                    ScrapToggle(
                        scrapped = item.problemId in scrappedProblemIds,
                        onToggle = { onToggleScrap(item.problemId) },
                    )
                }
                Text(item.question, style = LocalBcsType.current.question, color = colors.textPrimary)
                item.codeSnippet?.let { CodeSnippetBlock(code = it) }
                LabeledBlock("내가 쓴 답", item.submittedAnswer ?: "—")
                LabeledBlock("모범답안", item.representativeAnswer)
                ConceptChips(item.concepts)
                item.explanation?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textBody)
                }
                // '더 알아보기'(§5.7) — 이미 정답 접근이 가능한 맥락이라 바로 보인다.
                EnrichmentBlock(enrichment = item.enrichment)
                GhostButton(text = "돌아가기", onClick = onClose)
                Spacer(Modifier.height(BcsDimens.space6))
            }
        }
    }
}

@Composable
private fun LabeledBlock(label: String, value: String) {
    val colors = LocalBcsColors.current
    Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space1)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BcsDimens.radiusCard))
                .background(colors.surfaceSubtle)
                .padding(BcsDimens.space3),
        )
    }
}

@Composable
private fun SessionSkeleton(modifier: Modifier = Modifier) {
    val colors = LocalBcsColors.current
    Column(modifier = modifier.padding(top = BcsDimens.space6), verticalArrangement = Arrangement.spacedBy(BcsDimens.space3)) {
        Box(Modifier.fillMaxWidth().height(BcsDimens.skeletonLine).clip(RoundedCornerShape(BcsDimens.radiusCard)).background(colors.borderSubtle))
        Box(Modifier.fillMaxWidth(0.7f).height(BcsDimens.skeletonLine).clip(RoundedCornerShape(BcsDimens.radiusCard)).background(colors.borderSubtle))
        Spacer(Modifier.height(BcsDimens.space6))
        Box(Modifier.fillMaxWidth().height(BcsDimens.inputHeight).clip(RoundedCornerShape(BcsDimens.radiusCard)).background(colors.surfaceSubtle))
    }
}

@Composable
private fun SessionError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBcsColors.current
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("문제를 불러오지 못했어요", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text("학습 기록은 안전해요. 잠시 후 다시 시도해 주세요.", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        PrimaryButton(text = "다시 시도하기", onClick = onRetry)
    }
}
