package watson.bytecs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * window 레벨 keydown 리스너로 Enter를 잡는다. window는 페이지에 포커스만 있으면(탭을 떠나지 않는 한)
 * 특정 요소 포커스와 무관하게 keydown을 받으므로, 정답 후 입력칸이 사라져 캔버스 포커스가 빠진
 * 상황에서도 클릭 없이 Enter가 동작한다.
 *
 * 리스너는 컴포저블 수명 동안 한 번만 등록하고 내부에서 [enabled]를 검사한다:
 *  - 매 정답마다 add/remove를 반복하지 않아 wasm의 리스너 식별(제거) 문제를 피한다.
 *  - 정답을 제출한 그 Enter의 keydown은 아직 enabled=false 시점에 디스패치되므로(상태 갱신은 그 후),
 *    곧장 다음 문제로 건너뛰는 이중 발화가 없다.
 */
@Composable
actual fun PhysicalEnterKey(enabled: Boolean, onEnter: () -> Unit) {
    // 리스너를 재등록하지 않고도 최신 값을 보게 참조로 유지한다.
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnEnter by rememberUpdatedState(onEnter)
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { event ->
            val keyboardEvent = event as KeyboardEvent
            // NumpadEnter도 key가 "Enter"다. repeat(길게 누름)는 무시해 한 번만 진행한다.
            if (currentEnabled && keyboardEvent.key == "Enter" && !keyboardEvent.repeat) {
                currentOnEnter()
            }
        }
        window.addEventListener("keydown", listener)
        onDispose { window.removeEventListener("keydown", listener) }
    }
}
