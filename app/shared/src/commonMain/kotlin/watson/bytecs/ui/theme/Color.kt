package watson.bytecs.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * DESIGN_SYSTEM.md §2 색상 토큰.
 *
 * Material3 슬롯에 1:1로 매핑되는 색(primary·surface·background·outline 등)은 [BcsLightColorScheme]/
 * [BcsDarkColorScheme]로, Material 슬롯 밖의 브랜드 고유 토큰(success·neutralNudge·info·difficulty·
 * 텍스트 위계)은 [BcsColors]로 나눠 노출한다.
 *
 * ⭐️ 무낙인 원칙(§2.2): danger(빨강)는 Material의 error 슬롯에만 배선하고 **파괴적 행동 전용**이다.
 * 오답·불일치 피드백에는 절대 쓰지 않는다.
 */

// ── Material3 ColorScheme (§2.3) ─────────────────────────────────────────────

val BcsLightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB), // blue-600
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEFF6FF), // blue-50
    onPrimaryContainer = Color(0xFF1D4ED8), // blue-700
    secondary = Color(0xFF64748B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF334155),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFF1F5F9),
    // ⚠️ error 슬롯 = danger. 파괴적 행동(계정 삭제) 전용. 오답 피드백에 배선 금지.
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEF2F2),
    onErrorContainer = Color(0xFFB91C1C),
)

val BcsDarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA), // blue-400
    onPrimary = Color(0xFF172554), // blue-950
    primaryContainer = Color(0xFF1E3A8A), // blue-900
    onPrimaryContainer = Color(0xFFDBEAFE), // blue-100
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFCBD5E1),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFECACA),
)

// ── 브랜드 고유 토큰 (§2.1 텍스트 위계 · §2.2 상태색) ─────────────────────────

/**
 * Material 슬롯으로 표현하기 애매한 CS한입 고유 색 토큰. 라이트/다크 쌍으로 [LocalBcsColors]에 실린다.
 * 화면 코드는 raw hex 대신 이 토큰만 참조한다(§0, §9 검토 게이트).
 */
@Immutable
data class BcsColors(
    // 브랜드 — Primary 눌림(§2.1). Material 스킴에 pressed 슬롯이 없어 별도 노출한다.
    val primaryPressed: Color,
    // 브랜드 — primaryContainer 테두리(§2.1). InfoCard 테두리(§5.3)에 쓴다.
    val primaryBorder: Color,
    // 상태색 — 정답(긍정)
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    // 상태색 — 정보(힌트·근접 안내). 경고 아님. primary 계열과 같은 톤.
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    // 상태색 — 불일치/재시도. ⭐️ 중립 톤(빨강 절대 금지).
    val neutralNudgeForeground: Color,
    val neutralNudgeBackground: Color,
    /**
     * 상태색 — 연속 학습(스트릭) 표시(§2.2).
     *
     * ⚠️ **스트릭 표시(StreakBadge, §5.16)에만 쓴다.** 스트릭이 끊겼을 때 danger·상실 공포 연출
     * (불이 꺼진다 등)·죄책감 연출에 쓰지 않는다 — 끊겨도 "다시 시작해요" 중립·격려 톤(원칙 5).
     */
    val streak: Color,
    val streakContainer: Color,
    // 난이도(은은)
    val difficulty: Color,
    // 텍스트 위계
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textLabel: Color,
    val textBody: Color,
    // 표면·테두리
    val surfaceSubtle: Color,
    val border: Color,
    val borderSubtle: Color,
)

val BcsLightColors = BcsColors(
    primaryPressed = Color(0xFF1D4ED8), // blue-700
    primaryBorder = Color(0xFFDBEAFE), // blue-100
    success = Color(0xFF059669),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFECFDF5),
    onSuccessContainer = Color(0xFF065F46), // 제안: emerald-800 (컨테이너 위 텍스트, 문서 미명시)
    info = Color(0xFF2563EB), // = primary (§2.2)
    infoContainer = Color(0xFFEFF6FF), // = primaryContainer (§2.2)
    onInfoContainer = Color(0xFF1D4ED8),
    neutralNudgeForeground = Color(0xFF64748B),
    neutralNudgeBackground = Color(0xFFF8FAFC),
    streak = Color(0xFFF97316), // orange-500
    streakContainer = Color(0xFFFFF7ED), // orange-50
    difficulty = Color(0xFF94A3B8),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    textTertiary = Color(0xFF94A3B8),
    textLabel = Color(0xFF334155),
    textBody = Color(0xFF475569),
    surfaceSubtle = Color(0xFFF8FAFC),
    border = Color(0xFFE2E8F0),
    borderSubtle = Color(0xFFF1F5F9),
)

val BcsDarkColors = BcsColors(
    primaryPressed = Color(0xFF3B82F6), // blue-500
    primaryBorder = Color(0xFF1D4ED8), // blue-700
    success = Color(0xFF34D399),
    onSuccess = Color(0xFF06251C), // 제안: 밝은 emerald 위 딥 텍스트 (문서 미명시)
    successContainer = Color(0xFF064E3B),
    onSuccessContainer = Color(0xFFA7F3D0), // 제안: emerald-200 (컨테이너 위 텍스트, 문서 미명시)
    info = Color(0xFF60A5FA), // = primary (§2.2)
    infoContainer = Color(0xFF1E3A8A), // = primaryContainer (§2.2)
    onInfoContainer = Color(0xFFDBEAFE),
    neutralNudgeForeground = Color(0xFF94A3B8),
    neutralNudgeBackground = Color(0xFF1E293B),
    streak = Color(0xFFFB923C), // orange-400
    streakContainer = Color(0xFF431407), // orange-950
    difficulty = Color(0xFF64748B),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF64748B),
    textLabel = Color(0xFFCBD5E1),
    textBody = Color(0xFFCBD5E1),
    surfaceSubtle = Color(0xFF1E293B),
    border = Color(0xFF334155),
    borderSubtle = Color(0xFF1E293B),
)

/** 브랜드 색 토큰 접근점. [BcsTheme]가 라이트/다크 값을 공급한다. */
val LocalBcsColors = staticCompositionLocalOf { BcsLightColors }
