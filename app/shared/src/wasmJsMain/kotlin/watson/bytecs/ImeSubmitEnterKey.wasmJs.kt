package watson.bytecs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.abs

/** 브라우저 고해상도 시계(ms). 조합 확정과 Enter가 "같은 제스처"인지 시간차로 판정하는 데 쓴다. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun nowMs(): Double = js("performance.now()")

/**
 * compositionend(조합 확정)와 Enter가 [PAIR_WINDOW_MS] 안에 서로 발생하면 한 제스처로 보고, 이 시간창 밖일
 * 때만 제출을 흘려보낸다. 프레임 지터·이벤트 순서 뒤집힘에 견디게 하는 여유값 — 사람이 "단어를 끝내고 잠깐
 * 쉰 뒤 Enter"를 치는 간격보다는 짧게, 브라우저/IME의 스케줄링 지연보다는 넉넉하게 잡는다.
 */
private const val PAIR_WINDOW_MS = 250.0

/**
 * window 레벨에서 "조합 확정과 함께 눌린 Enter"를 감지해 제출([onSubmit])로 잇는다.
 *
 * 배경(사용자 콘솔 실측): 한글 조합 중 Enter 한 번은 브라우저에서 compositionend(조합 확정)와 별개의
 * Enter keydown(isComposing=false)으로 쪼개져 나온다. skiko는 이 Enter를 (조합 확정과 붙어 왔다는 이유로)
 * 삼켜 ImeAction.Done으로 넘기지 않아, 사용자가 Enter를 한 번 더 눌러야 진행된다. 그 빈틈만 메운다.
 *
 * ⭐️ 왜 "1프레임 플래그"가 아니라 "시간창 짝짓기"인가: 두 이벤트가 항상 같은 동기 배치(같은 프레임)로 오는 건
 * 아니다 — 드물게 사이에 프레임 경계가 끼거나 순서가 뒤집힌다(받침 유무와 무관한 간헐 오동작의 원인이었다).
 * 그래서 compositionend·Enter 각각의 발생 시각을 기록하고, 어느 쪽이 오든 상대가 [PAIR_WINDOW_MS] 이내에
 * 있었으면 짝으로 보고 한 번만 제출한다([lastSubmitMs]로 중복·재발화 방지). 프레임 지터·순서 뒤집힘 모두 견딘다.
 *
 * 조합과 무관한(깨끗한) Enter는 최근 compositionend가 없어 짝이 성립하지 않으므로 건드리지 않는다 →
 * skiko의 ImeAction.Done이 처리하고 이중 제출이 없다. 리스너는 컴포저블 수명 동안 한 번만 등록하고 내부에서
 * [enabled]를 검사한다(wasm 리스너 제거 문제 회피, [PhysicalEnterKey]와 같은 규칙).
 */
@Composable
actual fun ImeSubmitEnterKey(enabled: Boolean, onSubmit: () -> Unit) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnSubmit by rememberUpdatedState(onSubmit)
    DisposableEffect(Unit) {
        var lastCompositionEndMs = -1.0
        var lastEnterMs = -1.0
        var lastSubmitMs = -1.0

        fun tryPairAndSubmit() {
            if (!currentEnabled) return
            if (lastCompositionEndMs < 0.0 || lastEnterMs < 0.0) return
            // 조합 확정과 Enter가 서로 시간창 안 → 함께 눌린 Enter(skiko가 삼킨 그 Enter).
            if (abs(lastCompositionEndMs - lastEnterMs) > PAIR_WINDOW_MS) return
            val gestureMs = maxOf(lastCompositionEndMs, lastEnterMs)
            // 같은 짝(제스처)에 대해 한 번만 — 양쪽 핸들러의 중복 호출·직전 제출의 재발화를 막는다.
            if (gestureMs - lastSubmitMs <= PAIR_WINDOW_MS) return
            lastSubmitMs = gestureMs
            // 확정 글자가 입력값에 반영된 뒤(다음 프레임) 제출 — 마지막 글자 누락 방지.
            window.requestAnimationFrame { if (currentEnabled) currentOnSubmit() }
        }

        val onCompositionEnd: (Event) -> Unit = {
            lastCompositionEndMs = nowMs()
            tryPairAndSubmit()
        }
        val onKeyDown: (Event) -> Unit = { event ->
            val keyboardEvent = event as KeyboardEvent
            // repeat(길게 누름)는 무시해 한 번만 진행한다.
            if (keyboardEvent.key == "Enter" && !keyboardEvent.repeat) {
                lastEnterMs = nowMs()
                tryPairAndSubmit()
            }
        }
        window.addEventListener("keydown", onKeyDown)
        window.addEventListener("compositionend", onCompositionEnd)
        onDispose {
            window.removeEventListener("keydown", onKeyDown)
            window.removeEventListener("compositionend", onCompositionEnd)
        }
    }
}
