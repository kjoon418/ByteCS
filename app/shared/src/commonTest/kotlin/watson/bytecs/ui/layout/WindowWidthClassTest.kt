package watson.bytecs.ui.layout

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 적응형 레이아웃의 유일한 분기 축인 [windowWidthClassFor]의 경계값을 못박는다.
 * Material3 브레이크포인트(600/840dp)를 반개구간으로 나눈 것이므로, 경계 바로 아래/위가 갈리는 값을 본다.
 */
class WindowWidthClassTest {

    @Test
    fun `600dp 미만은 COMPACT다`() {
        assertEquals(WindowWidthClass.COMPACT, windowWidthClassFor(0f))
        assertEquals(WindowWidthClass.COMPACT, windowWidthClassFor(360f)) // 표준 폰 폭
        assertEquals(WindowWidthClass.COMPACT, windowWidthClassFor(599f))
    }

    @Test
    fun `600dp 이상 840dp 미만은 MEDIUM이다`() {
        assertEquals(WindowWidthClass.MEDIUM, windowWidthClassFor(600f)) // 경계 포함
        assertEquals(WindowWidthClass.MEDIUM, windowWidthClassFor(700f))
        assertEquals(WindowWidthClass.MEDIUM, windowWidthClassFor(839f))
    }

    @Test
    fun `840dp 이상은 EXPANDED다`() {
        assertEquals(WindowWidthClass.EXPANDED, windowWidthClassFor(840f)) // 경계 포함
        assertEquals(WindowWidthClass.EXPANDED, windowWidthClassFor(1280f)) // 데스크톱 폭
    }
}
