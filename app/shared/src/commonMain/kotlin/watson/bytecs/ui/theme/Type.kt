package watson.bytecs.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * DESIGN_SYSTEM.md §3 타이포그래피.
 *
 * 폰트: UI 본문은 Pretendard, 코드는 고정폭. 폰트 리소스가 아직 번들되지 않았으므로 시스템 폴백을 쓴다.
 * [BcsFontFamily]/[BcsCodeFontFamily] **한 곳만** 교체하면 전 스타일에 Pretendard가 반영되도록 구성했다.
 */

// TODO: Pretendard 폰트 리소스(composeResources/font/) 추가 후 FontFamily(Font(Res.font.*))로 교체.
val BcsFontFamily: FontFamily = FontFamily.Default

// TODO: JetBrains Mono / D2Coding 번들 후 교체. 미로딩 시 시스템 고정폭 폴백.
val BcsCodeFontFamily: FontFamily = FontFamily.Monospace

/** Material3 타입 스케일(§3.2). Material 슬롯에 매핑되는 스타일. */
val BcsTypography = Typography(
    displaySmall = TextStyle(fontFamily = BcsFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = BcsFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = BcsFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = BcsFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = BcsFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BcsFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = BcsFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = BcsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = BcsFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

/**
 * Material 슬롯 밖의 CS한입 고유 텍스트 스타일(§3.2).
 *  - [question]: 문제 질문 텍스트. 재생 학습의 주인공이라 크고 넉넉한 행간으로 별도 정의.
 *  - [codeInline]/[codeBlock]: 코드 조각·블록(고정폭).
 */
object BcsType {
    val question = TextStyle(fontFamily = BcsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 28.sp)
    val codeInline = TextStyle(fontFamily = BcsCodeFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp)
    val codeBlock = TextStyle(fontFamily = BcsCodeFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp)
}
