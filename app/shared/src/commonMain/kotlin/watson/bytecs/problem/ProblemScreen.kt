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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.ui.components.AnswerTextField
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.CodeSnippetBlock
import watson.bytecs.ui.components.CorrectFeedback
import watson.bytecs.ui.components.DifficultyIndicator
import watson.bytecs.ui.components.ErrorBanner
import watson.bytecs.ui.components.NearMissNudge
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.RetryNudge
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.components.difficultyLabel
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.BcsMotion
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * DESIGN_SYSTEM.md §8 / `03 문제 풀이 화면`을 세션 밖 **추가 연습**('조금 더 풀기')으로 재사용한 화면.
 * 세션 내 풀이는 [watson.bytecs.session.SessionScreen]이며, 두 화면은 §5.6 공용 컴포넌트를 공유한다.
 *
 * 문제 하나 → 답 입력 → 정답 확인 → 세 피드백 상태(정답/불일치/근접) 중 하나.
 * ⭐️ 무낙인: 불일치·근접에 빨강·경고·벌점을 절대 쓰지 않는다.
 *
 * ⭐️ 세션 진행 인디케이터(`03` A-1)를 두지 않는다: 그 인디케이터는 정의상 '오늘의 한입 중 몇 번째'인데,
 * 추가 연습은 세션 분량에 속하지 않아 셀 분량 자체가 없다. 분량 없는 진행도를 그리면 없는 목표를
 * 지어내고, 다 채운 것처럼 보이는 순간 '조금 더 풀기'가 끝난 것처럼 읽힌다.
 */
@Composable
fun ProblemScreen(
    viewModel: ProblemViewModel,
    modifier: Modifier = Modifier,
    // 계정·설정(06) 진입점. 미제공(프리뷰·테스트)이면 상단 액션을 숨긴다.
    onOpenAccount: (() -> Unit)? = null,
    // 나가기 진입점. '조금 더 풀기'(세션 밖 추가 연습)로 재사용될 때 홈으로 돌아갈 경로를 준다.
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProblemScreenContent(
        state = state,
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        onNext = viewModel::loadNext,
        onRetry = viewModel::loadProblem,
        onOpenAccount = onOpenAccount,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun ProblemScreenContent(
    state: ProblemUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onOpenAccount: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ready = state as? ProblemUiState.Ready
    val solved = ready?.feedback is Feedback.Correct

    BcsScaffold(
        modifier = modifier,
        topBar = {
            // 맥락 바 — 나가기·계정 진입점만. 진행 인디케이터는 두지 않는다(위 KDoc 참고).
            if (onOpenAccount != null || onBack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        // 부담 없는 나가기(경고 모달 없음) — 추가 연습은 언제 그만둬도 잃을 게 없다.
                        TextLink(
                            text = "나가기",
                            onClick = onBack,
                            color = LocalBcsColors.current.textSecondary,
                            contentDescription = "연습 나가기",
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (onOpenAccount != null) {
                        TextLink(
                            text = "계정",
                            onClick = onOpenAccount,
                            color = LocalBcsColors.current.textSecondary,
                            contentDescription = "계정·설정 열기",
                        )
                    }
                }
            }
        },
        bottomBar = {
            // 엄지 영역 하단 고정 CTA. 화면의 유일한 Primary.
            // 정답을 맞히면 다음 문제로 넘어가는 진행 액션으로 바뀐다(막다른 길 방지).
            if (ready != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                ) {
                    PrimaryButton(
                        text = if (solved) "다음 문제" else "정답 확인하기",
                        onClick = if (solved) onNext else onSubmit,
                        enabled = solved || ready.inputText.isNotBlank(),
                        loading = ready.isSubmitting,
                    )
                }
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

            is ProblemUiState.Error -> ErrorState(
                onRetry = onRetry,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
    val haptics = LocalHapticFeedback.current

    // 새 문제가 오면 입력 필드에 자동 포커스(§C-7).
    LaunchedEffect(state.problem.id) {
        focusRequester.requestFocus()
    }

    // ⭐️ 정답 순간 햅틱(§9 "정답 시 햅틱"). 긍정 피드백을 손끝으로도 느끼게.
    LaunchedEffect(state.feedback) {
        if (state.feedback is Feedback.Correct) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(modifier = modifier) {
        Spacer(Modifier.height(BcsDimens.space2))

        // 난이도(선택·은은). 압박 주지 않게 최소. 모르는 값이면 라벨도 간격도 그리지 않는다.
        difficultyLabel(state.problem.difficulty)?.let { label ->
            DifficultyIndicator(label)
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

        // ⭐️ 시스템 오류는 오답과 구분한다(§5.12). 전송 실패는 학습 기록 안전을 먼저 고지하고 재시도 경로를 준다.
        if (state.submitFailed) {
            Spacer(Modifier.height(BcsDimens.space4))
            ErrorBanner(
                message = "잠시 연결이 원활하지 않았어요. 다시 시도해 주세요.",
                onRetry = onSubmit,
            )
        }

        Spacer(Modifier.height(BcsDimens.space6))
    }
}

/** §5.12 로드 실패(시스템 오류) — 막다른 길 금지. 자산 안전 고지 + 재시도 경로. */
@Composable
private fun ErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    Column(
        // 로드 실패는 스크린리더가 즉시 읽어 줘야 한다 — 화면이 통째로 비어 버리는 상태라
        // 알림이 없으면 무슨 일이 났는지 알 길이 없다.
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "문제를 불러오지 못했어요",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Text(
            text = "학습 기록은 안전해요. 잠시 후 다시 시도해 주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        PrimaryButton(text = "다시 시도하기", onClick = onRetry)
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
            // 개념 칩은 정답 이후에만 노출된다(§5.9, 정답 스포일 방지) — Correct에만 concept이 실린다.
            is Feedback.Correct -> CorrectFeedback(
                concept = feedback.concept,
                explanation = feedback.explanation,
            )
            Feedback.Mismatch -> RetryNudge()
            Feedback.NearMiss -> NearMissNudge()
        }
    }
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

