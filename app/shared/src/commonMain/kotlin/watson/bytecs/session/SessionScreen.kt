package watson.bytecs.session

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.ui.components.AnswerTextField
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.CodeSnippetBlock
import watson.bytecs.ui.components.GhostButton
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.SessionProgress
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.BcsType
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 03 문제 풀이(세션 연동). 오늘의 한입 중 문제 하나를 푼다. 서비스의 히어로 화면.
 *
 * ⭐️ 무낙인: 불일치·근접에 빨강·경고·벌점 금지. 정답은 긍정, 불일치·근접은 중립·격려 톤으로 또렷하게.
 * ⭐️ 정답 공개 후에는 모범답안을 직접 따라 입력해야 진행된다(벌이 아니라 손으로 익히기).
 *
 * @param onCompleted 세션 완료(일회성 이벤트) → 04 완료 화면.
 * @param onExit 부담 없는 나가기 → 홈(언제든 이어서).
 */
@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    onCompleted: (CompletionSummary) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 화면 진입(홈에서 시작·이어서)마다 오늘 상태를 새로 반영한다(뷰모델이 내비게이션 간 재사용돼도 재개가 정확).
    LaunchedEffect(Unit) {
        viewModel.loadSession()
    }

    // 세션 완료는 일회성 이벤트 — 정확히 한 번 04로 넘긴다.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is SessionEvent.Completed) onCompleted(event.summary)
        }
    }

    SessionScreenContent(
        state = state,
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        onAdvance = viewModel::advance,
        onReveal = viewModel::requestReveal,
        onOpenPast = viewModel::openPast,
        onClosePast = viewModel::closePast,
        onRetry = viewModel::loadSession,
        onExit = onExit,
        modifier = modifier,
    )
}

@Composable
private fun SessionScreenContent(
    state: SessionUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAdvance: () -> Unit,
    onReveal: () -> Unit,
    onOpenPast: (Int) -> Unit,
    onClosePast: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
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
                    SessionProgress(current = active.current, total = active.total)
                }
                Spacer(Modifier.weight(1f))
                // 지난 문제 다시 보기(있을 때만) — 가장 최근 완료한 칸을 연다.
                if (active != null && active.hasPast && active.past == null) {
                    TextLink(
                        text = "지난 문제",
                        onClick = { onOpenPast(active.position - 1) },
                        color = LocalBcsColors.current.textSecondary,
                        contentDescription = "지난 문제 다시 보기",
                    )
                }
                // 부담 없는 나가기(경고 모달 없음).
                TextLink(
                    text = "나가기",
                    onClick = onExit,
                    color = LocalBcsColors.current.textSecondary,
                    contentDescription = "세션 나가기, 언제든 이어서 할 수 있어요",
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
                    PrimaryButton(
                        text = if (active.solved) "다음 문제" else "정답 확인하기",
                        onClick = if (active.solved) onAdvance else onSubmit,
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
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = BcsDimens.space5),
                    )
                } else {
                    ActiveContent(
                        state = state,
                        onInputChange = onInputChange,
                        onSubmit = onSubmit,
                        onReveal = onReveal,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = BcsDimens.space5),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveContent(
    state: SessionUiState.Active,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current

    // 새 문제가 오면 입력에 자동 포커스.
    LaunchedEffect(state.problem.id) {
        focusRequester.requestFocus()
    }
    // 정답 순간 햅틱(긍정 피드백을 손끝으로).
    LaunchedEffect(state.feedback) {
        if (state.feedback is SessionFeedback.Correct) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(modifier = modifier) {
        Spacer(Modifier.height(BcsDimens.space2))

        difficultyLabel(state.problem.difficulty)?.let { label ->
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.difficulty)
            Spacer(Modifier.height(BcsDimens.space2))
        }

        Text(text = state.problem.question, style = BcsType.question, color = colors.textPrimary)

        state.problem.codeSnippet?.let { snippet ->
            Spacer(Modifier.height(BcsDimens.space4))
            CodeSnippetBlock(code = snippet)
        }

        Spacer(Modifier.height(BcsDimens.space6))

        AnswerTextField(
            value = state.inputText,
            onValueChange = onInputChange,
            modifier = Modifier.focusRequester(focusRequester),
            placeholder = if (state.reveal != null) "위 정답을 따라 적어 보세요" else "정답을 입력해 보세요",
            onImeSubmit = onSubmit,
        )

        // 피드백(있을 때만). 세 상태 모두 비처벌.
        state.feedback?.let { feedback ->
            Spacer(Modifier.height(BcsDimens.space4))
            FeedbackCard(feedback)
        }

        // 정답 공개 카드(공개했을 때). 모범답안을 따라 입력하도록 안내.
        state.reveal?.let { reveal ->
            Spacer(Modifier.height(BcsDimens.space4))
            RevealCard(reveal)
        }

        // [정답 보기] — 최소 한 번 시도한 뒤에만(중립·secondary). 정답·공개 상태에선 숨김.
        if (state.canReveal) {
            Spacer(Modifier.height(BcsDimens.space4))
            TextLink(text = "정답 보기", onClick = onReveal, color = colors.textSecondary)
        }

        // 시스템 오류(전송 실패) — 오답과 구분(§5.12), 안심 문구 우선.
        if (state.systemError) {
            Spacer(Modifier.height(BcsDimens.space4))
            NudgeCard(
                text = "잠시 연결이 원활하지 않았어요. 학습 기록은 안전하니 다시 시도해 주세요.",
                background = colors.infoContainer,
                stripe = colors.info,
                textColor = colors.onInfoContainer,
            )
        }

        Spacer(Modifier.height(BcsDimens.space6))
    }
}

/** 세 피드백 상태 카드. 정답=긍정, 불일치=중립 넛지, 근접=info(정답·개념 비노출). */
@Composable
private fun FeedbackCard(feedback: SessionFeedback) {
    val colors = LocalBcsColors.current
    when (feedback) {
        is SessionFeedback.Correct -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BcsDimens.radiusCard))
                .background(colors.successContainer)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(BcsDimens.space4),
            verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
        ) {
            Text("맞았어요!", style = MaterialTheme.typography.titleMedium, color = colors.onSuccessContainer)
            feedback.concept?.let { ConceptChip(it) }
            feedback.explanation?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSuccessContainer)
            }
        }

        SessionFeedback.Mismatch -> NudgeCard(
            text = "아직이에요, 다시 해볼까요?",
            background = colors.neutralNudgeBackground,
            stripe = colors.neutralNudgeForeground,
            textColor = colors.neutralNudgeForeground,
        )

        SessionFeedback.NearMiss -> NudgeCard(
            text = "거의 맞았어요, 오타를 확인해보세요",
            background = colors.infoContainer,
            stripe = colors.info,
            textColor = colors.onInfoContainer,
        )
    }
}

/** 정답 공개 카드 — info 톤. 모범답안·개념·해설 + 따라 입력 안내. */
@Composable
private fun RevealCard(reveal: Reveal) {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.infoContainer)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(BcsDimens.space4),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space2),
    ) {
        Text("정답을 따라 적어 볼까요?", style = MaterialTheme.typography.titleSmall, color = colors.onInfoContainer)
        Text(
            text = reveal.acceptableAnswers.joinToString("  ·  "),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onInfoContainer,
        )
        ConceptChip(reveal.concept)
        reveal.explanation?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onInfoContainer)
        }
    }
}

/** 지난 문제 읽기 전용 뷰. 문제·내 답·모범답안·개념·해설. */
@Composable
private fun PastItemView(past: PastView, onClose: () -> Unit, modifier: Modifier = Modifier) {
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
                Text("지난 문제 ${item.position + 1}", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                Text(item.question, style = BcsType.question, color = colors.textPrimary)
                item.codeSnippet?.let { CodeSnippetBlock(code = it) }
                LabeledBlock("내가 쓴 답", item.submittedAnswer ?: "—")
                LabeledBlock("모범답안", item.acceptableAnswers.joinToString("  ·  "))
                ConceptChip(item.concept)
                item.explanation?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textBody)
                }
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

/** 좌측 액센트 스트라이프 넛지 카드(비처벌 공통 골격). */
@Composable
private fun NudgeCard(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    stripe: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(background)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Box(
            Modifier
                .width(BcsDimens.accentStripe)
                .height(BcsDimens.inputHeight)
                .background(stripe),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.padding(BcsDimens.space4))
    }
}

@Composable
private fun ConceptChip(concept: String) {
    val colors = LocalBcsColors.current
    Text(
        text = concept,
        style = MaterialTheme.typography.labelMedium,
        color = colors.onInfoContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(BcsDimens.radiusFull))
            .background(colors.infoContainer)
            .padding(horizontal = BcsDimens.space3, vertical = BcsDimens.space1),
    )
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

private fun difficultyLabel(difficulty: String?): String? = when (difficulty?.uppercase()) {
    "EASY" -> "쉬움"
    "MEDIUM" -> "보통"
    "HARD" -> "어려움"
    else -> null
}
