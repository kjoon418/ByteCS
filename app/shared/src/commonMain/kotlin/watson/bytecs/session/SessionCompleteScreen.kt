package watson.bytecs.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import watson.bytecs.account.PreferredDifficulty
import watson.bytecs.account.preferredDifficultyStatement
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.GhostButton
import watson.bytecs.ui.components.InfoCard
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.SecondaryButton
import watson.bytecs.ui.components.StreakBadge
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 거절 안내("설정에서 언제든 바꿀 수 있어요")를 보여준 뒤 카드를 닫기까지의 시간. */
private const val DISMISS_NOTICE_DURATION_MS = 1_800L

/**
 * 04 세션 완료. 오늘의 한입을 다 마쳤을 때의 완결 화면.
 *
 * ⭐️ 성취 연출을 아끼지 않되(축하 모션·햅틱) 긍정 톤 — 다음 방문은 초대이지 강요가 아니다.
 * ⭐️ 스트릭은 긍정 동기(끊김 죄책감·상실 공포 연출 금지).
 *
 * ⚠️ **소요 시간을 표시하지 않는다.** 도메인(기능 1.5)은 세션을 시간이 아니라 **분량**으로 정의하고,
 * §9는 카운트다운·시간 압박을 금지한다. 요약은 '얼마나 걸렸나'가 아니라 '무엇을 해냈나'다.
 *
 * ⭐️ 가입 유도는 이 화면에 두지 않는다(2026-07-16 오너 결정) — 홈의 가입 유도(GuestUpgradeBanner)로
 * 접점을 일원화해, 완료의 순간은 성취에만 집중한다.
 *
 * @param viewModel 선호 난이도 제안 카드(구성 8, DF1) 상태 홀더. 노출 여부는 [CompletionSummary.needsDifficultyPrompt]로
 * 생성 시 정해진다 — 서버가 준 신호를 그대로 따른다(클라 조건 재계산 금지).
 * @param summary 완료 요약(푼 문제 수·스트릭). 세션 완료 이벤트로 전달된다.
 * @param onDone [오늘은 여기까지] → 02 홈.
 * @param onMore [조금 더 풀기] → 새 세션으로 03 세션 풀이에 재진입(D6·D9 일원화 — 추가 학습 폐지).
 */
@Composable
fun SessionCompleteScreen(
    viewModel: SessionCompleteViewModel,
    summary: CompletionSummary,
    onDone: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val promptState by viewModel.uiState.collectAsStateWithLifecycle()
    SessionCompleteScreenContent(
        summary = summary,
        promptState = promptState,
        onDone = onDone,
        onMore = onMore,
        onSelectDifficulty = viewModel::select,
        onDismissPrompt = viewModel::dismiss,
        onDismissNoticeFinished = viewModel::closeAfterDismissNotice,
        modifier = modifier,
    )
}

/**
 * [SessionCompleteScreen]의 순수 렌더 본체 — 뷰모델 없이 상태를 직접 받아 UI 테스트에서 쓴다
 * (계정·설정 화면의 AccountScreen/AccountScreenContent와 같은 분리 관례).
 */
@Composable
internal fun SessionCompleteScreenContent(
    summary: CompletionSummary,
    promptState: DifficultyPromptUiState,
    onDone: () -> Unit,
    onMore: () -> Unit,
    onSelectDifficulty: (PreferredDifficulty) -> Unit,
    onDismissPrompt: () -> Unit,
    onDismissNoticeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    val haptics = LocalHapticFeedback.current

    // 등장 시 한 번 축하 햅틱 + 스케일 인(1~2초, 다음 행동 막지 않음).
    var shown by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.7f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
    )
    // 성취 수치 카운트업(0→푼 문제 수, ~0.7s). 밋밋함 방지(04 §1).
    val solvedShown by animateIntAsState(
        targetValue = if (shown) summary.solvedCount else 0,
        animationSpec = tween(durationMillis = 700),
    )
    LaunchedEffect(Unit) {
        shown = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    BcsScaffold(
        modifier = modifier,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
            ) {
                // 한 화면에 강조되는 Primary Action은 하나. '조금 더 풀기'는 압박이 아닌 선택이므로 Secondary.
                PrimaryButton(text = "오늘은 여기까지", onClick = onDone)
                SecondaryButton(text = "조금 더 풀기", onClick = onMore)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space6),
            verticalArrangement = Arrangement.spacedBy(BcsDimens.space4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CelebrationHeader(scale = scale)

            Text(
                text = "오늘 CS 한입 완료!",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "오늘도 성실하게 지식을 채웠군요.\n작은 습관이 당신을 전문가로 만듭니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            // 오늘의 요약 — 분량 기반 성취(⚠️ 소요 시간 아님).
            // 시안의 '새 개념/복습 개념'은 [CompletionSummary]에 없어 뺐다(명세 §3.2에서 선택 항목).
            // 임의로 세지 않는다 — 개념 분류는 도메인(뷰모델·서버) 몫이다.
            SolvedSummaryCard(solved = solvedShown)

            // 스트릭(백엔드가 실어 줄 때만). 색·카피 규칙은 공용 StreakBadge(§5.16)에 있다.
            summary.streak?.let { StreakBadge(days = it.count) }

            // 정착 연결 안내 — ⚠️ 시점을 단정하지 않는다("3일 뒤" 금지). 복습은 개념별 간격 반복이라
            // 세션 단위로 다음 만남을 약속할 수 없다(지킬 수 없는 약속 금지).
            InfoCard {
                Text(
                    text = "다시 만나요",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onInfoContainer,
                )
                Text(
                    text = "배운 내용은 나중에 복습으로 다시 만나요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
            }

            // 선호 난이도 제안 카드(구성 8, DF1) — 완결 축하·스트릭 아래 얹히는 '가벼운 초대'.
            // 노출 여부는 promptState.visible 하나만 따른다(서버가 준 신호, 클라 재계산 없음).
            if (promptState.visible) {
                DifficultyPromptCard(
                    state = promptState,
                    onSelect = onSelectDifficulty,
                    onDismiss = onDismissPrompt,
                    onDismissNoticeFinished = onDismissNoticeFinished,
                )
            }
        }
    }
}

/**
 * 선호 난이도 제안 카드 본체. 완결 축하 위계를 가리지 않도록 중립 톤(surfaceSubtle)만 쓰고,
 * danger·강조색은 쓰지 않는다. 저장 실패는 카드를 유지한 채 안내만 남겨 재시도를 열어둔다
 * (UX 에러 응답 가이드 — 해결 방법을 알려준다·부정적 감정 최소화).
 */
@Composable
private fun DifficultyPromptCard(
    state: DifficultyPromptUiState,
    onSelect: (PreferredDifficulty) -> Unit,
    onDismiss: () -> Unit,
    onDismissNoticeFinished: () -> Unit,
) {
    val colors = LocalBcsColors.current

    // 거절 안내는 은은히 보여준 뒤 스스로 닫는다(04 §8 "안내 후 닫기").
    LaunchedEffect(state.dismissedNotice) {
        if (state.dismissedNotice) {
            delay(DISMISS_NOTICE_DURATION_MS)
            onDismissNoticeFinished()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.surfaceSubtle)
            .padding(BcsDimens.space5)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        if (state.dismissedNotice) {
            Text(
                text = "설정에서 언제든 바꿀 수 있어요",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        } else {
            // ⭐️ 조절 주체가 사용자임을 드러내는 문구 — "실력에 맞는" 같은 평가 뉘앙스 금지(04 §8 DF4).
            Text(
                text = "새 문제, 어떤 난이도로 만나고 싶은지 골라볼까요?",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space2)) {
                DifficultyPromptOption(
                    label = preferredDifficultyStatement(PreferredDifficulty.EASY),
                    enabled = !state.saving,
                    onClick = { onSelect(PreferredDifficulty.EASY) },
                )
                DifficultyPromptOption(
                    label = preferredDifficultyStatement(PreferredDifficulty.MEDIUM),
                    enabled = !state.saving,
                    onClick = { onSelect(PreferredDifficulty.MEDIUM) },
                )
                DifficultyPromptOption(
                    label = preferredDifficultyStatement(PreferredDifficulty.HARD),
                    enabled = !state.saving,
                    onClick = { onSelect(PreferredDifficulty.HARD) },
                )
            }
            if (state.error != null) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            GhostButton(
                text = "지금은 괜찮아요",
                onClick = onDismiss,
                enabled = !state.saving,
                contentColor = colors.textSecondary,
            )
        }
    }
}

/** 제안 카드의 선택지 하나. 톤은 설정 화면(06)의 같은 문구 선택지와 맞춘다(눈에 익은 시각 언어). */
@Composable
private fun DifficultyPromptOption(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalBcsColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusChip))
            .background(MaterialTheme.colorScheme.surface)
            .border(BcsDimens.borderWidth, colors.border, RoundedCornerShape(BcsDimens.radiusChip))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = BcsDimens.space4, vertical = BcsDimens.space3),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
        )
    }
}

/** 완결 축하 그래픽 — 파티클 버스트 위 success 원형 + 체크 모션. 장식이므로 시맨틱 없음. */
@Composable
private fun CelebrationHeader(scale: Float) {
    val colors = LocalBcsColors.current
    Box(
        modifier = Modifier.size(BcsDimens.space10 * 3),
        contentAlignment = Alignment.Center,
    ) {
        // ⭐️ 짧은 축하 파티클 버스트(≤1.2s·비차단·토큰 색). 성취를 또렷이(밋밋함 방지).
        ConfettiBurst(
            colors = listOf(MaterialTheme.colorScheme.primary, colors.success, colors.info),
        )
        Box(
            modifier = Modifier
                // 세션 완료는 §2.2가 명시한 success 사례 — 브랜드색이 아니라 emerald로 채운다.
                .size(BcsDimens.space6 * 4)
                .scale(scale)
                .clip(CircleShape)
                .background(colors.success),
            contentAlignment = Alignment.Center,
        ) {
            CompletionCheck(color = colors.onSuccess)
        }
    }
}

/**
 * 오늘의 요약 카드 — 푼 본 문제 수. 숫자 압박이 아니라 성취 표현이다(04 §3.2).
 * ⚠️ 시간 지표를 넣지 않는다(도메인은 세션을 분량으로 정의).
 */
@Composable
private fun SolvedSummaryCard(solved: Int) {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.surfaceSubtle)
            .padding(BcsDimens.space5),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "푼 문제",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textTertiary,
        )
        Text(
            text = "${solved}개",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
    }
}

/**
 * 축하 파티클 버스트 — 중심에서 바깥으로 퍼지며 사라지는 점들(≤1.2s, 1회). 장식이므로 시맨틱 없음.
 * 색은 토큰만 사용한다(primary·success·info). 다음 행동(CTA)을 막지 않는 순수 오버레이.
 */
@Composable
private fun ConfettiBurst(colors: List<Color>) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 1200)) }
    // 파티클은 한 번만 무작위 배치(각도·거리·크기·색)한다.
    val particles = remember {
        List(18) { index ->
            Particle(
                angle = Random.nextFloat() * 2f * PI.toFloat(),
                distance = 0.55f + Random.nextFloat() * 0.45f,
                radius = 3f + Random.nextFloat() * 4f,
                color = colors[index % colors.size],
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}) {
        val p = progress.value
        val cx = size.width / 2f
        val cy = size.height / 2f
        val spread = size.minDimension / 2f
        particles.forEach { particle ->
            val r = particle.distance * spread * p
            val x = cx + cos(particle.angle) * r
            val y = cy + sin(particle.angle) * r
            drawCircle(
                color = particle.color.copy(alpha = (1f - p).coerceIn(0f, 1f)),
                radius = particle.radius,
                center = Offset(x, y),
            )
        }
    }
}

private data class Particle(
    val angle: Float,
    val distance: Float,
    val radius: Float,
    val color: Color,
)

/** 완료 체크 표시. 장식이므로 시맨틱을 비운다(의미는 "오늘 CS 한입 완료!" 텍스트가 전달). */
@Composable
private fun CompletionCheck(color: Color) {
    Canvas(
        modifier = Modifier
            .size(BcsDimens.space8)
            .clearAndSetSemantics {},
    ) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.14f
        drawLine(color, Offset(w * 0.22f, h * 0.55f), Offset(w * 0.42f, h * 0.74f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, h * 0.74f), Offset(w * 0.78f, h * 0.30f), stroke, StrokeCap.Round)
    }
}
