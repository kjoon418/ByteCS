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

    /**
     * §5.13 파괴적 버튼(계정 삭제)도 평범하게 렌더·클릭된다.
     * 색 계약은 [ComponentTonesTest]가 매핑 함수에서 본다 — 여기서는 role이 동작을 바꾸지 않는지만 확인한다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 파괴적_역할_버튼도_텍스트를_보여주고_클릭을_전달한다() = runComposeUiTest {
        var clicks = 0
        setContent {
            BcsTheme(darkTheme = false) {
                PrimaryButton(
                    text = "삭제",
                    onClick = { clicks++ },
                    role = PrimaryButtonRole.Destructive,
                )
            }
        }

        onNodeWithText("삭제").assertIsDisplayed().performClick()

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
