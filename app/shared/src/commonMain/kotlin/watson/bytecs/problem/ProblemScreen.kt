package watson.bytecs.problem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.ui.components.AnswerTextField
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.CodeSnippetBlock
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.SessionProgress
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.BcsMotion
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * DESIGN_SYSTEM.md §8 / `03 문제 풀이 화면`. 서비스의 히어로 화면.
 *
 * 문제 하나 → 답 입력 → 정답 확인 → 세 피드백 상태(정답/불일치/근접) 중 하나.
 * ⭐️ 무낙인: 불일치·근접에 빨강·경고·벌점을 절대 쓰지 않는다.
 */
@Composable
fun ProblemScreen(
    viewModel: ProblemViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProblemScreenContent(
        state = state,
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
private fun ProblemScreenContent(
    state: ProblemUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = state as? ProblemUiState.Ready

    BcsScaffold(
        modifier = modifier,
        topBar = {
            // 상단 세션·맥락 바. 분량 기반 진행만(카운트다운 타이머 아님).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ready != null) {
                    SessionProgress(current = ready.current, total = ready.total)
                }
            }
        },
        bottomBar = {
            // 엄지 영역 하단 고정 CTA. 화면의 유일한 Primary.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
            ) {
                PrimaryButton(
                    text = "정답 확인하기",
                    onClick = onSubmit,
                    enabled = ready != null && ready.inputText.isNotBlank(),
                )
            }
        },
    ) {
        when (state) {
            is ProblemUiState.Loading -> ProblemSkeleton(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5),
            )

            is ProblemUiState.Ready -> ReadyContent(
                state = state,
                onInputChange = onInputChange,
                onSubmit = onSubmit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = BcsDimens.space5),
            )
        }
    }
}

@Composable
private fun ReadyContent(
    state: ProblemUiState.Ready,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    val focusRequester = remember { FocusRequester() }

    // 새 문제가 오면 입력 필드에 자동 포커스(§C-7).
    LaunchedEffect(state.problem.id) {
        focusRequester.requestFocus()
    }

    Column(modifier = modifier) {
        Spacer(Modifier.height(BcsDimens.space2))

        // 난이도(선택·은은). 압박 주지 않게 최소.
        val difficultyLabel = difficultyLabel(state.problem.difficulty)
        if (difficultyLabel != null) {
            Text(
                text = difficultyLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.difficulty,
            )
            Spacer(Modifier.height(BcsDimens.space2))
        }

        // 문제 질문 — 화면의 주인공.
        Text(
            text = state.problem.question,
            style = watson.bytecs.ui.theme.BcsType.question,
            color = colors.textPrimary,
        )

        // 코드 스니펫(있을 때만).
        state.problem.codeSnippet?.let { snippet ->
            Spacer(Modifier.height(BcsDimens.space4))
            CodeSnippetBlock(code = snippet)
        }

        Spacer(Modifier.height(BcsDimens.space6))

        // 답 입력.
        AnswerTextField(
            value = state.inputText,
            onValueChange = onInputChange,
            modifier = Modifier.focusRequester(focusRequester),
            placeholder = "정답을 입력해 보세요",
            onImeSubmit = onSubmit,
        )

        // 피드백(있을 때만). 세 상태 모두 비처벌.
        state.feedback?.let { feedback ->
            Spacer(Modifier.height(BcsDimens.space4))
            FeedbackSection(feedback)
        }

        Spacer(Modifier.height(BcsDimens.space6))
    }
}

/** 세 피드백 상태. 나타날 때 또렷한 진입 모션을 준다(밋밋함 금지). */
@Composable
private fun FeedbackSection(feedback: Feedback) {
    // feedback 종류가 바뀔 때마다 다시 애니메이션되도록 종류로 키를 준다.
    val visibleState = remember(feedback::class) {
        MutableTransitionState(false).apply { targetState = true }
    }
    AnimatedVisibility(
        visibleState = visibleState,
        // ⭐️ a11y: 상태 변화("맞았어요!"/"아직이에요…")를 스크린리더가 즉시 읽어 주도록 라이브 리전.
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        enter = fadeIn(tween(BcsMotion.durBase)) +
            scaleIn(tween(BcsMotion.durBase), initialScale = 0.96f) +
            expandVertically(tween(BcsMotion.durBase)),
    ) {
        when (feedback) {
            is Feedback.Correct -> CorrectFeedback(feedback)
            Feedback.Mismatch -> RetryNudge()
            Feedback.NearMiss -> NearMissNudge()
        }
    }
}

/** 정답 — 긍정(success) + 체크 + 개념 칩·해설. */
@Composable
private fun CorrectFeedback(feedback: Feedback.Correct) {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.successContainer)
            .padding(BcsDimens.space4),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckMark(color = colors.success)
            Spacer(Modifier.width(BcsDimens.space2))
            Text(
                text = "맞았어요!",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSuccessContainer,
            )
        }
        // 개념 칩은 정답 이후에만 노출(§5.9, 정답 스포일 방지).
        feedback.concept?.let { concept -> ConceptChip(concept) }
        feedback.explanation?.let { explanation ->
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSuccessContainer,
            )
        }
    }
}

/** 불일치 — ⭐️ 중립·격려 넛지(neutralNudge). 빨강·경고 금지, 토스트로 사라지지 않음. */
@Composable
private fun RetryNudge() {
    val colors = LocalBcsColors.current
    // 회색-on-회색이라 밋밋하지 않도록 좌측 액센트 스트라이프로 살리언스를 준다(비처벌 유지).
    NudgeCard(
        text = "아직이에요, 다시 해볼까요?",
        backgroundColor = colors.neutralNudgeBackground,
        stripeColor = colors.neutralNudgeForeground,
        textColor = colors.neutralNudgeForeground,
    )
}

/** 근접(오탈자) — info/중립 톤. ⭐️ 정답·개념 비노출. 불일치와 구별되는 톤. */
@Composable
private fun NearMissNudge() {
    val colors = LocalBcsColors.current
    NudgeCard(
        text = "거의 맞았어요, 오타를 확인해보세요",
        backgroundColor = colors.infoContainer,
        stripeColor = colors.info,
        textColor = colors.onInfoContainer,
    )
}

/** 좌측 액센트 스트라이프 + 배경 카드. 분명하지만 비처벌인 인라인 넛지의 공통 골격. */
@Composable
private fun NudgeCard(
    text: String,
    backgroundColor: Color,
    stripeColor: Color,
    textColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(backgroundColor),
    ) {
        Box(
            modifier = Modifier
                .width(BcsDimens.accentStripe)
                .fillMaxHeight()
                .background(stripeColor),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.padding(BcsDimens.space4),
        )
    }
}

/** §5.9 ConceptChip — 알약형(radiusFull), 정답 이후에만 노출. */
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

/** §5.11 로딩 — 스켈레톤(스피너 지양). 은은한 알파 펄스. */
@Composable
private fun ProblemSkeleton(modifier: Modifier = Modifier) {
    val colors = LocalBcsColors.current
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            tween(BcsMotion.durDrilldown, easing = BcsMotion.easing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    Column(
        modifier = modifier.padding(top = BcsDimens.space6),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(BcsDimens.skeletonLine).alpha(pulse), colors.borderSubtle)
        SkeletonBox(Modifier.fillMaxWidth(0.7f).height(BcsDimens.skeletonLine).alpha(pulse), colors.borderSubtle)
        Spacer(Modifier.height(BcsDimens.space6))
        SkeletonBox(Modifier.fillMaxWidth().height(BcsDimens.inputHeight).alpha(pulse), colors.surfaceSubtle)
    }
}

@Composable
private fun SkeletonBox(modifier: Modifier, color: Color) {
    Box(modifier.clip(RoundedCornerShape(BcsDimens.radiusCard)).background(color))
}

/**
 * 간단한 체크 표시. 아이콘 폰트 의존을 피해 Canvas로 그린다.
 * 장식 요소이므로 시맨틱을 비운다 — 의미("맞았어요!")는 인접 텍스트가 전달한다(불변식).
 */
@Composable
private fun CheckMark(color: Color) {
    Canvas(
        modifier = Modifier
            .size(BcsDimens.iconCheck)
            .clearAndSetSemantics {},
    ) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.12f
        drawLine(
            color = color,
            start = Offset(w * 0.22f, h * 0.55f),
            end = Offset(w * 0.42f, h * 0.74f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.42f, h * 0.74f),
            end = Offset(w * 0.78f, h * 0.30f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun difficultyLabel(difficulty: String?): String? = when (difficulty?.uppercase()) {
    "EASY" -> "쉬움"
    "MEDIUM" -> "보통"
    "HARD" -> "어려움"
    else -> null
}
