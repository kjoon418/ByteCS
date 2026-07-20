package watson.bytecs.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.BcsTheme

/**
 * 리스트-디테일 2패널의 배치 계약을 못박는다: 좌측 목록(master, 고정 폭)과 우측 상세(detail, 남은 폭)가
 * 위아래로 쌓이지 않고 **나란히** 놓인다. 선택 전 상세 자리에는 placeholder가 뜬다.
 */
class TwoPaneListDetailTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `master는_고정폭_좌측_detail은_그_오른쪽에_나란히_놓인다`() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                Box(Modifier.size(width = 1000.dp, height = 700.dp)) {
                    TwoPaneListDetail(
                        master = { Box(Modifier.fillMaxSize()) { Text("MASTER") } },
                        detail = { TwoPaneDetailPlaceholder("왼쪽에서 선택하세요") },
                    )
                }
            }
        }

        onNodeWithText("MASTER").assertIsDisplayed()
        onNodeWithText("왼쪽에서 선택하세요").assertIsDisplayed()

        val master = onNodeWithText("MASTER").getBoundsInRoot()
        val detail = onNodeWithText("왼쪽에서 선택하세요").getBoundsInRoot()

        // master 텍스트는 고정 폭(360dp) 목록 패널 안에 있다.
        assertTrue(
            master.left.value < BcsDimens.masterPaneWidth.value,
            "master(left=${master.left.value}dp)는 좌측 고정 폭 패널 안에 있어야 한다",
        )
        // detail(placeholder)은 master 패널 오른쪽(≥360dp)에 있다 — 세로로 쌓이지 않고 나란히.
        assertTrue(
            detail.left.value >= BcsDimens.masterPaneWidth.value,
            "detail(left=${detail.left.value}dp)은 master 고정 폭(${BcsDimens.masterPaneWidth.value}dp) 오른쪽에 있어야 한다",
        )
    }
}
