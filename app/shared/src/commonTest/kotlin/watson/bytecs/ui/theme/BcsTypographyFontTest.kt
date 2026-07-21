package watson.bytecs.ui.theme

import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ⭐️ 웹(skiko) 한글 두부(□) 회귀 가드: [bcsTypography]의 **모든 15개 Material 슬롯**이 지정한 폰트를
 * 쓰는지 못박는다. 빠뜨린 슬롯은 `FontFamily.Default`로 떨어져, 시스템 한글 폰트가 없는 웹에서 한글이
 * 깨진다(모바일은 시스템 폰트라 안 깨져 놓치기 쉽다). 실제로 headlineMedium 누락으로 온보딩 'CS한입'
 * 제목이 웹에서만 깨졌던 자리다 — 이 테스트가 그 클래스의 재발을 막는다.
 *
 * 센티넬 폰트([FontFamily.Monospace], Default가 아닌 아무 패밀리)를 주입해 전 슬롯이 그대로 전파되는지 본다.
 */
class BcsTypographyFontTest {

    @Test
    fun `모든 Material 타이포그래피 슬롯이 지정한 폰트를 쓴다`() {
        val sentinel = FontFamily.Monospace
        val typography = bcsTypography(sentinel)

        val slots = mapOf(
            "displayLarge" to typography.displayLarge,
            "displayMedium" to typography.displayMedium,
            "displaySmall" to typography.displaySmall,
            "headlineLarge" to typography.headlineLarge,
            "headlineMedium" to typography.headlineMedium,
            "headlineSmall" to typography.headlineSmall,
            "titleLarge" to typography.titleLarge,
            "titleMedium" to typography.titleMedium,
            "titleSmall" to typography.titleSmall,
            "bodyLarge" to typography.bodyLarge,
            "bodyMedium" to typography.bodyMedium,
            "bodySmall" to typography.bodySmall,
            "labelLarge" to typography.labelLarge,
            "labelMedium" to typography.labelMedium,
            "labelSmall" to typography.labelSmall,
        )

        for ((name, style) in slots) {
            assertEquals(
                sentinel,
                style.fontFamily,
                "$name 슬롯이 지정 폰트를 안 써 FontFamily.Default로 떨어지면 웹에서 한글이 깨진다",
            )
        }
    }
}
