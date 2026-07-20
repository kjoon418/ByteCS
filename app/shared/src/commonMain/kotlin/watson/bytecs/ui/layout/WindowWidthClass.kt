package watson.bytecs.ui.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 창(뷰포트) 가로 폭의 3단 분류. 적응형 레이아웃(웹/태블릿)의 유일한 분기 축이다.
 *
 * Material3 window-size-class 아티팩트를 도입하지 않고 자체 경량 구현을 택했다(계획 §4-1):
 * 이 프로젝트는 수동 DI·자체 내비게이션 등 라이브러리 최소주의를 일관되게 유지하고,
 * 필요한 것은 너비 3단 분류뿐이다. pane scaffolding 등 더 복잡한 요구가 생기면 아티팩트 전환을 검토한다.
 */
enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

/**
 * 가로 폭(dp)을 [WindowWidthClass]로 분류한다. Material3 기준 브레이크포인트를 따른다:
 * `<600dp` → COMPACT(모바일), `<840dp` → MEDIUM(대형폰·세로 태블릿), `>=840dp` → EXPANDED(가로 태블릿·웹/데스크톱).
 *
 * 순수 함수로 분리해 경계값을 테스트로 못박는다(599/600/839/840).
 */
fun windowWidthClassFor(widthDp: Float): WindowWidthClass = when {
    widthDp < 600f -> WindowWidthClass.COMPACT
    widthDp < 840f -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.EXPANDED
}

/**
 * 현재 창 너비 클래스. 기본값 [WindowWidthClass.COMPACT]는 "측정 전 = 모바일 렌더"를 뜻해,
 * provider가 없거나 폭 측정 전에도 기존 모바일 화면과 100% 동일하게 그려진다(회귀 없음).
 * App 최상위에서 실제 폭을 측정해 이 값을 덮어쓴다.
 */
val LocalWindowWidthClass = compositionLocalOf { WindowWidthClass.COMPACT }

/**
 * 가용 폭을 측정해 [LocalWindowWidthClass]를 제공한다. 적응형 레이아웃(웹/태블릿)의 단일 분기 지점이다.
 * [BoxWithConstraints]가 부여하는 `maxWidth`(Dp)를 폭으로 삼아 순수 함수 [windowWidthClassFor]로 분류한다.
 * 모바일 폭(<600dp)에서는 COMPACT가 나와 기존 렌더와 100% 동일하다(회귀 없음).
 *
 * App 최상위에서 한 번 감싸 전 화면에 전파한다. 화면 코드는 [LocalWindowWidthClass]`.current`만 읽는다.
 */
@Composable
fun ProvideWindowWidthClass(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthClass = windowWidthClassFor(maxWidth.value)
        CompositionLocalProvider(LocalWindowWidthClass provides widthClass) {
            content()
        }
    }
}
