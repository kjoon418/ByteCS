package watson.bytecs.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import watson.bytecs.ui.theme.BcsTheme

/**
 * Compose UI 테스트 기반이 실제로 동작하는지 증명하는 스모크 테스트.
 * 렌더 → 표시 확인 → 클릭 → 콜백 확인까지 한 바퀴를 돈다.
 */
class PrimaryButtonUiTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 텍스트를_보여주고_클릭을_콜백으로_전달한다() = runComposeUiTest {
        var clicks = 0
        setContent {
            BcsTheme(darkTheme = false) {
                PrimaryButton(text = "정답 확인하기", onClick = { clicks++ })
            }
        }

        onNodeWithText("정답 확인하기").assertIsDisplayed().performClick()

        assertEquals(1, clicks)
    }

    /** loading 중에는 클릭이 막힌다(PrimaryButton의 clickable = enabled && !loading). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 로딩_중에는_클릭이_콜백으로_전달되지_않는다() = runComposeUiTest {
        var clicks = 0
        setContent {
            BcsTheme(darkTheme = false) {
                PrimaryButton(text = "정답 확인하기", onClick = { clicks++ }, loading = true)
            }
        }

        onNodeWithText("정답 확인하기").assertDoesNotExist()

        assertEquals(0, clicks)
    }
}
