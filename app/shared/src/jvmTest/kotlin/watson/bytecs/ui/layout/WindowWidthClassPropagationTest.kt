package watson.bytecs.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import watson.bytecs.ui.theme.BcsTheme

/**
 * [ProvideWindowWidthClass]가 측정한 폭을 [LocalWindowWidthClass]로 전파하는지, 실제 컴포지션에서 창 크기를
 * 강제해 검증한다. 순수 함수 경계값은 [WindowWidthClassTest]가 보고, 여기서는 "측정→CompositionLocal 전파"
 * 배선이 실제로 붙는지를 못박는다(폭 고정 Box 안에서 provider가 그 폭을 읽는다).
 */
class WindowWidthClassPropagationTest {

    @OptIn(ExperimentalTestApi::class)
    private fun assertWidthClassAt(widthDp: Int, expected: WindowWidthClass) = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                // 폭을 명시적으로 고정해 provider의 BoxWithConstraints가 그 폭을 측정하게 한다.
                Box(Modifier.size(width = widthDp.dp, height = 800.dp)) {
                    ProvideWindowWidthClass {
                        Text(LocalWindowWidthClass.current.name)
                    }
                }
            }
        }
        onNodeWithText(expected.name).assertIsDisplayed()
    }

    @Test
    fun `좁은 폭은 COMPACT로 전파된다`() = assertWidthClassAt(360, WindowWidthClass.COMPACT)

    @Test
    fun `중간 폭은 MEDIUM으로 전파된다`() = assertWidthClassAt(700, WindowWidthClass.MEDIUM)

    @Test
    fun `넓은 폭은 EXPANDED로 전파된다`() = assertWidthClassAt(1000, WindowWidthClass.EXPANDED)
}
