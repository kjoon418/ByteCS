package watson.bytecs

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // 안드로이드 Activity·iOS UIViewController에 대응하는 웹 엔트리포인트.
    // 캔버스는 document.body 전체에 붙고, 실제 화면은 commonMain의 App()이 그린다.
    ComposeViewport(document.body!!) {
        App()
    }
}
