package watson.bytecs.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.dp

/**
 * DESIGN_SYSTEM.md §4 간격/라운드/그림자 · §7 반응형 치수.
 *
 * 치수는 라이트/다크에 따라 달라지지 않으므로 CompositionLocal 대신 상수 오브젝트로 둔다.
 * 화면 코드는 raw dp 대신 이 토큰만 참조한다(§0, §9 검토 게이트 — 레이아웃 산식은 예외).
 */
object BcsDimens {

    // ── Spacing · 4dp 배수 (§4.1) ────────────────────────────────────────────
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp // 콘텐츠 가로 패딩(표준)
    val space6 = 24.dp
    val space8 = 32.dp
    val space10 = 40.dp

    // ── Radius (§4.2) ────────────────────────────────────────────────────────
    val radiusSm = 8.dp
    val radiusChip = 12.dp
    val radiusCard = 16.dp // 카드·입력·버튼 기본
    val radiusFull = 9999.dp

    // ── Elevation (§4.3) ─────────────────────────────────────────────────────
    val elevCard = 1.dp
    val elevButton = 4.dp
    val elevSheet = 8.dp

    // ── Size (§5 컴포넌트 · §7 반응형) ────────────────────────────────────────
    val inputHeight = 56.dp // AnswerTextField·PrimaryButton 최소 높이
    val buttonHeight = 56.dp
    val minTouchTarget = 48.dp
    val contentMax = 600.dp // 태블릿/웹 콘텐츠 최대 폭(중앙 제한)
    val progressDot = 8.dp
    val accentStripe = 4.dp // 넛지 좌측 액센트 스트라이프 폭
    val iconCheck = 22.dp // 정답 체크 표시
    val loaderSize = 20.dp // 버튼 인라인 로딩 스피너
    val loaderStroke = 2.dp
    val skeletonLine = 24.dp // 스켈레톤 텍스트 줄 높이

    // ── Border (§5 입력·카드 테두리) ──────────────────────────────────────────
    val borderWidth = 1.dp
    val borderWidthStrong = 1.5.dp // 진행 점 현재 위치 테두리
}

/**
 * 모션 토큰(§4.4). 모션은 인지 보조 목적만.
 * 지속시간은 ms(Int)로, 애니메이션 스펙(`tween`)에서 그대로 쓴다.
 */
object BcsMotion {
    const val pressScale = 0.98f
    const val pressScaleStrong = 0.96f // 강조 CTA(정답 제출)
    const val durFast = 120 // 색·스케일 트랜지션
    const val durBase = 200 // 진입·포커스
    const val durDrilldown = 320 // 디딤 문제 깊이 전환
    val easing = FastOutSlowInEasing
}
