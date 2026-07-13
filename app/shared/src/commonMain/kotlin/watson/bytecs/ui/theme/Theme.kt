package watson.bytecs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * DESIGN_SYSTEM.md §0 구현 진입점.
 *
 * Material3 [MaterialTheme]를 베이스로 깔고, Material 슬롯 밖의 브랜드 토큰([BcsColors])을
 * [LocalBcsColors]로 함께 노출한다. 시스템 다크 설정(또는 이후 설정 화면 토글, §화면 06)을
 * [darkTheme]로 받아 라이트/다크를 전환한다.
 *
 * 사용:
 * ```
 * BcsTheme {
 *     val colors = LocalBcsColors.current      // 브랜드 고유 토큰
 *     MaterialTheme.colorScheme.primary        // Material 매핑 토큰
 * }
 * ```
 */
@Composable
fun BcsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) BcsDarkColorScheme else BcsLightColorScheme
    val bcsColors = if (darkTheme) BcsDarkColors else BcsLightColors

    CompositionLocalProvider(LocalBcsColors provides bcsColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BcsTypography,
            content = content,
        )
    }
}
