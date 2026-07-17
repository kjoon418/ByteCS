package watson.bytecs.extrastudy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import watson.bytecs.scrap.ScrapRepository
import watson.bytecs.ui.components.AnswerTextField
import watson.bytecs.ui.components.BcsHint
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.CodeSnippetBlock
import watson.bytecs.ui.components.ConceptChips
import watson.bytecs.ui.components.ConfirmedAnswerField
import watson.bytecs.ui.components.DifficultyIndicator
import watson.bytecs.ui.components.EnrichmentBlock
import watson.bytecs.ui.components.ErrorBanner
import watson.bytecs.ui.components.HintStepper
import watson.bytecs.ui.components.MisconceptionHintCard
import watson.bytecs.ui.components.NearMissNudge
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.RevealAnswerButton
import watson.bytecs.ui.components.RevealedAnswerField
import watson.bytecs.ui.components.ScrapToggle
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.components.TypeAlongField
import watson.bytecs.ui.components.difficultyLabel
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.BcsMotion
import watson.bytecs.ui.theme.BcsType
import watson.bytecs.ui.theme.LocalBcsColors
import kotlin.coroutines.cancellation.CancellationException

/**
 * 추가 학습('조금 더 풀기') 화면. 세션 풀이(03)와 **같은 `ui.components` 프리미티브**를 조립하되, 세션 전용
 * 요소(진행 인디케이터·지난 문제·완료 CTA·한입 마치기)를 뺀다. 한 번에 한 문제, 재진입 시 이어 푼다.
 *
 * ⭐️ 진행 인디케이터를 두지 않는다: 그 인디케이터는 정의상 '오늘의 한입 중 몇 번째'인데, 추가 학습은 목표
 * 분량에 속하지 않아 셀 분량 자체가 없다. 분량 없는 진행도를 그리면 없는 목표를 지어내고, 다 채운 것처럼
 * 보이는 순간 '조금 더 풀기'가 끝난 것처럼 읽힌다(레거시 ProblemScreen 근거 계승).
 *
 * ⭐️ 무낙인: 불일치·근접에 빨강·경고·벌점 금지. 오답은 문제 영역의 주황 플래시로만 알린다(텍스트 카드 없음).
 * ⭐️ 정답 공개 후에는 모범답안을 직접 따라 입력해야 진행된다(불변식 19 — 벌이 아니라 손으로 익히기).
 *
 * @param onBack 부담 없는 나가기 → 홈(언제든 다시 이어서).
 * @param onReport 콘텐츠 오류 신고(07) 진입 — 문제 id를 실어 넘긴다.
 */
@Composable
fun ExtraStudyScreen(
    viewModel: ExtraStudyViewModel,
    onBack: () -> Unit,
    onReport: (Long) -> Unit,
    scrapRepository: ScrapRepository,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 화면 진입마다 현재(이어 풀) 문제를 새로 반영한다(뷰모델이 내비게이션 간 재사용돼도 재개가 정확).
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    // ⭐️ 스크랩 토글은 낙관적으로 문제 id별로 들고, 서버 반영 실패 시 되돌린다(세션과 같은 규칙).
    //    스크랩은 개인 북마크일 뿐 모범답안을 노출하지 않으므로(도메인 §5) 풀이 중에도 열어 둔다.
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

    ExtraStudyScreenContent(
        state = state,
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        onAdvance = viewModel::advance,
        onReveal = viewModel::requestReveal,
        onRevealHint = viewModel::revealNextHint,
        onRetry = viewModel::load,
        onExit = onBack,
        onReport = onReport,
        scrappedProblemIds = scrapped.filterValues { it }.keys,
        onToggleScrap = toggleScrap,
        modifier = modifier,
    )
}

@Composable
internal fun ExtraStudyScreenContent(
    state: ExtraStudyUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAdvance: () -> Unit,
    onReveal: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onRevealHint: () -> Unit = {},
    onReport: (Long) -> Unit = {},
    scrappedProblemIds: Set<Long> = emptySet(),
    onToggleScrap: (Long) -> Unit = {},
) {
    val active = state as? ExtraStudyUiState.Active

    BcsScaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                // 콘텐츠 오류 신고(07) — 풀이 중(공개 전)에도 언제든 열려 있다. 신고 화면은 유형만 받으므로
                //    정답을 유출하지 않는다.
                if (active != null) {
                    TextLink(
                        text = "오류 신고",
                        onClick = { onReport(active.problem.id) },
                        color = LocalBcsColors.current.textTertiary,
                        contentDescription = "이 문제의 콘텐츠 오류 신고",
                    )
                    Spacer(Modifier.width(BcsDimens.space4))
                }
                // 부담 없는 나가기(경고 모달 없음) — 추가 학습은 언제 그만둬도 잃을 게 없다.
                // ⭐️ '세션'은 내부 용어라 어디에도(스크린리더 포함) 노출하지 않는다.
                TextLink(
                    text = "나가기",
                    onClick = onExit,
                    color = LocalBcsColors.current.textSecondary,
                    contentDescription = "나가기, 언제든 다시 이어서 할 수 있어요",
                )
            }
        },
        bottomBar = {
            if (active != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                ) {
                    // 미해결 → 제출, 정답 → 다음 문제(조회). 세션의 [한입 마치기] 분기는 없다.
                    PrimaryButton(
                        text = if (active.solved) "다음 문제" else "제출하기",
                        onClick = if (active.solved) onAdvance else onSubmit,
                        enabled = active.solved || active.inputText.isNotBlank(),
                        loading = active.isSubmitting,
                    )
                }
            }
        },
    ) {
        when (state) {
            ExtraStudyUiState.Loading -> ExtraStudySkeleton(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = BcsDimens.space5),
            )

            ExtraStudyUiState.Error -> ExtraStudyError(
                onRetry = onRetry,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = BcsDimens.space5),
            )

            ExtraStudyUiState.Exhausted -> ExhaustedState(
                onBack = onExit,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = BcsDimens.space5),
            )

            is ExtraStudyUiState.Active -> ActiveContent(
                state = state,
                onInputChange = onInputChange,
                onSubmit = onSubmit,
                onAdvance = onAdvance,
                onReveal = onReveal,
                onRevealHint = onRevealHint,
                scrappedProblemIds = scrappedProblemIds,
                onToggleScrap = onToggleScrap,
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = BcsDimens.space5),
            )
        }
    }
}

@Composable
private fun ActiveContent(
    state: ExtraStudyUiState.Active,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAdvance: () -> Unit,
    onReveal: () -> Unit,
    onRevealHint: () -> Unit,
    scrappedProblemIds: Set<Long>,
    onToggleScrap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current

    // ⭐️ 엔터는 하단 CTA와 같은 곳으로 간다 — 정답 상태에선 '다음 문제', 그 외엔 '제출하기'.
    val onImeSubmit = if (state.solved) onAdvance else onSubmit

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
            is ExtraStudyFeedback.Correct -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            is ExtraStudyFeedback.Mismatch -> {
                retryFlashing = true
                delay(300)
                retryFlashing = false
            }
            else -> Unit
        }
    }

    Column(modifier = modifier) {
        Spacer(Modifier.height(BcsDimens.space2))

        // ⭐️ 문제 영역의 감쇠 — 정답을 맞힌 뒤에는 문제가 더 이상 화면의 초점이 아니므로 한 발 물러난다.
        //    opacity만 낮출 뿐 트리에서 지우지 않으므로 스크린리더 낭독은 그대로 유지된다.
        Column(
            modifier = Modifier
                .then(if (state.solved || state.reveal != null) Modifier.alpha(0.6f) else Modifier)
                .clip(RoundedCornerShape(BcsDimens.radiusCard))
                .background(problemTint)
                .padding(BcsDimens.space3),
        ) {
            // 난이도(왼쪽)와 '다시 볼래요' 스크랩(오른쪽)을 한 행에 둔다. 스크랩은 모범답안을 노출하지
            //    않으므로 풀이 중에도 열어 둔다(정답 접근 게이트와 무관, QA-04).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                difficultyLabel(state.problem.difficulty)?.let { label ->
                    DifficultyIndicator(label)
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

            Text(text = state.problem.question, style = BcsType.question, color = colors.textPrimary)

            state.problem.codeSnippet?.let { snippet ->
                Spacer(Modifier.height(BcsDimens.space4))
                CodeSnippetBlock(code = snippet)
            }
        }

        Spacer(Modifier.height(BcsDimens.space6))

        val reveal = state.reveal
        if (reveal == null) {
            if (state.solved) {
                // ⭐️ 정답 입력란의 확정 전환 — 더 이상 편집 대상이 아니므로 확정 표시로 바꾼다(은은한 축하 연출).
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
                            (state.feedback as? ExtraStudyFeedback.Correct)?.representativeAnswer ?: state.inputText
                        ConfirmedAnswerField(representativeAnswer = representativeAnswer)
                        state.feedback?.let { feedback ->
                            Spacer(Modifier.height(BcsDimens.space4))
                            FeedbackCard(feedback)
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
                    FeedbackCard(feedback)
                }

                // 힌트(pull) — 요청해야만 하나씩 열린다. hintCount 0이면 HintStepper가 진입점을 그리지 않는다.
                if (state.problem.hintCount > 0) {
                    Spacer(Modifier.height(BcsDimens.space4))
                    HintStepper(
                        hints = state.hintList(),
                        revealedCount = state.revealedHintCount,
                        onRevealNext = onRevealHint,
                    )
                }

                // [정답 보기] — 시도 전에도 열 수 있다(중립·secondary). 정답·공개 상태에선 숨김.
                if (state.canReveal) {
                    Spacer(Modifier.height(BcsDimens.space4))
                    RevealAnswerButton(onClick = onReveal)
                }
            }
        } else {
            // ⭐️ 공개 레이아웃을 정답 시 배치와 통일한다 — 입력칸 자리에 정답 표시 필드를 두고 바로 아래에
            //    따라 입력 칸을 붙인다(입력창이 사라졌다는 착각 방지).
            RevealedAnswerField(representativeAnswer = reveal.representativeAnswer)
            Spacer(Modifier.height(BcsDimens.space4))
            TypeAlongField(
                value = state.inputText,
                onValueChange = onInputChange,
                onImeSubmit = onImeSubmit,
            )

            // 따라 적다 어긋나도 처벌이 아니다 — 같은 비처벌 넛지를 그대로 쓴다.
            state.feedback?.let { feedback ->
                Spacer(Modifier.height(BcsDimens.space4))
                FeedbackCard(feedback)
            }

            // 해설·개념·심화는 정답 시(FeedbackCard Correct)와 같은 flat 순서로 그 아래에 둔다.
            //    개념은 공개 이후에만 — 풀기 전 노출은 정답 스포일이다(§5.9).
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
 * 세 피드백 상태 카드. 정답=긍정(개념·해설·심화), 불일치=중립 넛지(라이브 리전만), 근접=info.
 * 세션 [watson.bytecs.session.SessionScreen]의 FeedbackCard와 같은 무낙인·no-leak 결을 따른다.
 * 상태 변화를 스크린리더가 즉시 읽도록 라이브 리전을 씌운다.
 */
@Composable
private fun FeedbackCard(feedback: ExtraStudyFeedback) {
    val colors = LocalBcsColors.current
    val announce = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (feedback) {
        // 확인("완벽해요! 정확한 정답입니다.")은 [ConfirmedAnswerField]가 이미 보여주므로, 그 아래
        //    위계(개념 칩 → 해설 → 더 알아보기)만 잇는다. 해설은 정답 시 본문 텍스트로 흘린다(flat 구조).
        is ExtraStudyFeedback.Correct -> Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space3)) {
            if (!feedback.concepts.isNullOrEmpty()) {
                ConceptChips(feedback.concepts)
            }
            feedback.explanation?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.textBody)
            }
            // '더 알아보기'(§5.7) — 정답 처리 즉시 바로 보인다(토글 없음).
            EnrichmentBlock(enrichment = feedback.enrichment)
        }

        // 불일치엔 텍스트 카드를 두지 않는다 — 시각 신호는 문제 영역의 주황 플래시가 맡는다.
        //    비시각 사용자에겐 라이브 리전으로만 짧게 안내한다: '오답/틀림/실패' 어휘 없이(무낙인).
        is ExtraStudyFeedback.Mismatch -> Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space3)) {
            Box(
                Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "정답과 달라요, 다시 시도해 보세요"
                },
            )
            feedback.misconceptionHint?.let { hint -> MisconceptionHintCard(text = hint) }
        }

        ExtraStudyFeedback.NearMiss -> NearMissNudge(modifier = announce)
    }
}

/**
 * [HintStepper]에 넘길 [BcsHint] 목록. ⭐️ no-leak: 미공개 힌트 본문은 클라에 없으므로, 공개된 것만 실제 본문으로
 * 채우고 나머지는 **렌더되지 않는** 자리표시자로 채운다(HintStepper는 revealedCount 미만만 그린다).
 */
private fun ExtraStudyUiState.Active.hintList(): List<BcsHint> {
    val revealed = revealedHints.map { BcsHint(text = it.text, codeSnippet = it.codeSnippet) }
    val hidden = (problem.hintCount - revealed.size).coerceAtLeast(0)
    return revealed + List(hidden) { BcsHint(text = "") }
}

/**
 * 소진 상태 — 무낙인·긍정 톤 빈 상태(§5.10). 모두 풀었고 도래한 복습도 없다.
 * ⛔ '없음/끝/실패' 낙인·상실 프레임 금지 — 복습 주기가 돌아오면 자연히 다시 만난다는 긍정 톤.
 */
@Composable
private fun ExhaustedState(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBcsColors.current
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 빈 상태 아이콘 원형(§5.10) — 긍정 톤(success 컨테이너). 장식이라 시맨틱을 비운다.
        Box(
            modifier = Modifier
                .size(BcsDimens.emptyStateIcon)
                .clip(CircleShape)
                .background(colors.successContainer)
                .clearAndSetSemantics {},
        )
        Text(
            text = "오늘 풀 문제를 다 만났어요",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Text(
            text = "지금은 더 풀 문제가 없어요. 복습 주기가 돌아오면 다시 만나요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        PrimaryButton(text = "홈으로", onClick = onBack)
    }
}

@Composable
private fun ExtraStudySkeleton(modifier: Modifier = Modifier) {
    val colors = LocalBcsColors.current
    Column(modifier = modifier.padding(top = BcsDimens.space6), verticalArrangement = Arrangement.spacedBy(BcsDimens.space3)) {
        Box(Modifier.fillMaxWidth().height(BcsDimens.skeletonLine).clip(RoundedCornerShape(BcsDimens.radiusCard)).background(colors.borderSubtle))
        Box(Modifier.fillMaxWidth(0.7f).height(BcsDimens.skeletonLine).clip(RoundedCornerShape(BcsDimens.radiusCard)).background(colors.borderSubtle))
        Spacer(Modifier.height(BcsDimens.space6))
        Box(Modifier.fillMaxWidth().height(BcsDimens.inputHeight).clip(RoundedCornerShape(BcsDimens.radiusCard)).background(colors.surfaceSubtle))
    }
}

@Composable
private fun ExtraStudyError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
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
