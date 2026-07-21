package watson.bytecs.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import bytecs.app.shared.generated.resources.Res
import bytecs.app.shared.generated.resources.pretendard_variable
import org.jetbrains.compose.resources.Font

/**
 * DESIGN_SYSTEM.md §3 타이포그래피.
 *
 * 폰트: UI 본문은 Pretendard(가변 폰트, composeResources/font에 번들), 코드는 시스템 고정폭.
 * Pretendard는 한글 글리프를 포함하므로, 시스템 한글 폰트가 없는 웹(skiko 캔버스)에서도 한글이
 * 두부(□)로 깨지지 않고 정상 렌더된다. 모바일도 같은 폰트로 통일된다.
 *
 * compose-resources의 [Font]가 @Composable이라 폰트 패밀리를 최상위 val로 만들 수 없다.
 * [rememberBcsFontFamily]로 컴포지션 안에서 만들어 [BcsTheme]가 타이포그래피와 [LocalBcsType]에
 * 주입한다. 폰트 패밀리 교체는 이 파일 한 곳만 고치면 전 스타일에 반영된다.
 */

/** Pretendard 가변 폰트 패밀리. 실제 쓰는 웨이트만 선언한다(가변 폰트라 파일 자체는 하나). */
@Composable
fun rememberBcsFontFamily(): FontFamily = FontFamily(
    Font(Res.font.pretendard_variable, weight = FontWeight.Normal),
    Font(Res.font.pretendard_variable, weight = FontWeight.Medium),
    Font(Res.font.pretendard_variable, weight = FontWeight.SemiBold),
    Font(Res.font.pretendard_variable, weight = FontWeight.Bold),
)

/**
 * 코드용 고정폭. 코드 조각은 대개 Latin이라 시스템 고정폭 폴백을 쓴다.
 * (웹에서 코드 블록 내부의 한글은 시스템 폴백이 없으면 깨질 수 있음 — 후속 과제로 남긴다.)
 */
val BcsCodeFontFamily: FontFamily = FontFamily.Monospace

/**
 * Material3 타입 스케일(§3.2). [fontFamily]를 Material 슬롯 **전체**에 적용한다.
 *
 * ⚠️ 명시 정의하는 슬롯뿐 아니라 **모든 15개 슬롯**에 폰트를 입혀야 한다. 빠뜨린 슬롯은
 * `FontFamily.Default`로 떨어지는데, 시스템 한글 폰트가 없는 웹(skiko 캔버스)에서는 한글이 두부(□)로
 * 깨진다(모바일은 시스템 폰트라 안 깨져서 놓치기 쉽다 — 온보딩 'CS한입'이 headlineMedium을 써서 웹에서만
 * 깨졌던 회귀가 이 자리다). 커스텀 사이즈를 주지 않는 슬롯(headline*·display L/M·labelSmall)은
 * Material 기본 스케일을 유지하되 폰트만 덮어쓴다. 회귀 방지: `BcsTypographyFontTest`.
 */
fun bcsTypography(fontFamily: FontFamily): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp),
        titleSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
    )
}

/**
 * Material 슬롯 밖의 CS한입 고유 텍스트 스타일(§3.2).
 *  - [question]: 문제 질문 텍스트. 재생 학습의 주인공이라 크고 넉넉한 행간으로 별도 정의.
 *  - [codeInline]/[codeBlock]: 코드 조각·블록(고정폭).
 *
 * 폰트 패밀리가 런타임(컴포지션)에 정해지므로 [LocalBcsType]으로 내려준다.
 */
@Immutable
data class BcsTypeStyles(
    val question: TextStyle,
    val codeInline: TextStyle,
    val codeBlock: TextStyle,
)

/** 본문 폰트([fontFamily])와 코드 폰트([BcsCodeFontFamily])로 커스텀 스타일 묶음을 만든다. */
fun bcsTypeStyles(fontFamily: FontFamily): BcsTypeStyles = BcsTypeStyles(
    question = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 28.sp),
    codeInline = TextStyle(fontFamily = BcsCodeFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    codeBlock = TextStyle(fontFamily = BcsCodeFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
)

/**
 * 커스텀 텍스트 스타일 접근점. 기본값은 시스템 폴백 폰트라 [BcsTheme] 밖(프리뷰·격리 테스트)에서도
 * 안전하다. [BcsTheme]가 Pretendard 기반 스타일로 덮어쓴다.
 */
val LocalBcsType = staticCompositionLocalOf { bcsTypeStyles(FontFamily.Default) }
