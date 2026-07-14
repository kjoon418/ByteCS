package watson.bytecs.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.GhostButton
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 04 세션 완료. 오늘의 한입을 다 마쳤을 때의 완결 화면.
 *
 * ⭐️ 성취 연출을 아끼지 않되(축하 모션·햅틱) 긍정 톤 — 다음 방문은 초대이지 강요가 아니다.
 * ⭐️ 스트릭은 긍정 동기(끊김 죄책감·상실 공포 연출 금지). 게스트 승계는 축하 맥락의 자연스러운 권유.
 *
 * @param summary 완료 요약(푼 문제 수·스트릭). 세션 완료 이벤트로 전달된다.
 * @param isGuest 게스트면 승계 유도를 은은하게 노출.
 * @param onDone [오늘은 여기까지] → 02 홈.
 * @param onMore [조금 더 풀기] → 추가 연습.
 * @param onUpgrade 게스트 승계 → 05.
 */
@Composable
fun SessionCompleteScreen(
    summary: CompletionSummary,
    isGuest: Boolean,
    onDone: () -> Unit,
    onMore: () -> Unit,
    onUpgrade: () -> Unit,
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
                PrimaryButton(text = "오늘은 여기까지", onClick = onDone)
                GhostButton(text = "조금 더 풀기", onClick = onMore)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = BcsDimens.space5),
            verticalArrangement = Arrangement.spacedBy(BcsDimens.space4, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 완결 축하 그래픽 — 파티클 버스트 위 원형 배경 + 체크 모션.
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
                        .size(BcsDimens.space10)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(colors.successContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    CompletionCheck(color = colors.success)
                }
            }

            Text(
                text = "오늘 CS 한입 완료!",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "오늘 ${solvedShown}문제를 해냈어요.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            // 스트릭(있을 때만·긍정).
            summary.streak?.takeIf { it.count > 0 }?.let { streak ->
                Text(
                    text = "🔥 ${streak.count}일 연속 학습 중",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onInfoContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(BcsDimens.radiusFull))
                        .background(colors.infoContainer)
                        .padding(horizontal = BcsDimens.space3, vertical = BcsDimens.space1)
                        .semantics { contentDescription = "${streak.count}일 연속 학습 중" },
                )
            }

            // 정착 연결 안내(은은).
            Text(
                text = "오늘 배운 건 나중에 복습으로 다시 만나요.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
            )

            // 게스트 승계 유도(축하 맥락의 자연스러운 권유·강요 X).
            if (isGuest) {
                Spacer(Modifier.height(BcsDimens.space2))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(BcsDimens.radiusCard))
                        .background(colors.surfaceSubtle)
                        .padding(BcsDimens.space4),
                    verticalArrangement = Arrangement.spacedBy(BcsDimens.space2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "가입하면 이 기록이 사라지지 않아요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    GhostButton(text = "가입하고 기록 지키기", onClick = onUpgrade)
                }
            }
        }
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
private fun CompletionCheck(color: androidx.compose.ui.graphics.Color) {
    Canvas(
        modifier = Modifier
            .size(BcsDimens.space6)
            .clearAndSetSemantics {},
    ) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.14f
        drawLine(color, Offset(w * 0.22f, h * 0.55f), Offset(w * 0.42f, h * 0.74f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, h * 0.74f), Offset(w * 0.78f, h * 0.30f), stroke, StrokeCap.Round)
    }
}
