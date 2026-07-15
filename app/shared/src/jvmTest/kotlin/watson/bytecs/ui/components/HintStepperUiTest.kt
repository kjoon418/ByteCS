package watson.bytecs.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import watson.bytecs.ui.theme.BcsTheme

/**
 * DESIGN_SYSTEM.md §5.5 HintStepper 계약.
 *
 * 여기서 지키는 것은 시각이 아니라 **학습 설계**다. 힌트가 요청 없이 열리거나, 개수를 고정으로 가정하면
 * 재생(직접 떠올림) 학습이 무너진다.
 */
class HintStepperUiTest {

    private val hints = listOf(
        BcsHint(text = "버킷이 겹치는 상황을 떠올려 보세요"),
        BcsHint(text = "해시 함수는 서로 다른 입력을 같은 값으로 보낼 수 있어요"),
        BcsHint(text = "이 현상을 다루는 방법에 체이닝·개방 주소법이 있어요", drilldownLabel = "더 쉬운 문제로 풀어보기"),
    )

    /** 진입 전에는 힌트 본문이 하나도 없고 진입점만 있다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 공개_전에는_힌트_보기_진입점만_보인다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                HintStepper(hints = hints, revealedCount = 0, onRevealNext = {})
            }
        }

        onNodeWithText("힌트 보기").assertIsDisplayed()
        for (hint in hints) {
            onNodeWithText(hint.text).assertDoesNotExist()
        }
    }

    /**
     * ⭐️ 핵심 가드레일: '더 보기'를 누르지 않으면 다음 힌트는 화면에 없다.
     * (열린 힌트는 계속 보이되, 안 연 힌트가 미리 보이면 안 된다.)
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 더_보기_없이는_다음_힌트가_열리지_않는다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                HintStepper(hints = hints, revealedCount = 1, onRevealNext = {})
            }
        }

        // 연 힌트는 남아 있고
        onNodeWithText(hints[0].text).assertIsDisplayed()
        // 아직 요청하지 않은 힌트는 존재하지 않는다.
        onNodeWithText(hints[1].text).assertDoesNotExist()
        onNodeWithText(hints[2].text).assertDoesNotExist()
        onNodeWithText("더 보기").assertIsDisplayed()
    }

    /** '힌트 보기' → '더 보기'로 한 번에 하나씩만 늘어난다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 한_번에_하나씩_공개된다() = runComposeUiTest {
        var revealed by mutableStateOf(0)
        setContent {
            BcsTheme(darkTheme = false) {
                HintStepper(hints = hints, revealedCount = revealed, onRevealNext = { revealed++ })
            }
        }

        onNodeWithText("힌트 보기").performClick()
        assertEquals(1, revealed)
        onNodeWithText(hints[0].text).assertIsDisplayed()
        onNodeWithText(hints[1].text).assertDoesNotExist()

        onNodeWithText("더 보기").performClick()
        assertEquals(2, revealed)
        onNodeWithText(hints[0].text).assertIsDisplayed() // 이미 연 힌트는 계속 보인다
        onNodeWithText(hints[1].text).assertIsDisplayed()
        onNodeWithText(hints[2].text).assertDoesNotExist()
    }

    /** 다 열면 진입점이 사라진다 — 눌러도 아무 일 없는 버튼을 남기지 않는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 모두_공개하면_더_보기가_사라진다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                HintStepper(hints = hints, revealedCount = hints.size, onRevealNext = {})
            }
        }

        onNodeWithText("더 보기").assertDoesNotExist()
        onNodeWithText("힌트 보기").assertDoesNotExist()
        for (hint in hints) {
            onNodeWithText(hint.text).assertIsDisplayed()
        }
    }

    /** ⭐️ §5.5: 힌트가 없는 문제면 진입점 자체를 노출하지 않는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 힌트가_없는_문제는_진입점을_노출하지_않는다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                HintStepper(hints = emptyList(), revealedCount = 0, onRevealNext = {})
            }
        }

        onNodeWithText("힌트 보기").assertDoesNotExist()
    }

    /**
     * ⭐️ 고정 L1/L2 사다리 금지 — 힌트가 하나뿐인 문제도 정상 동작한다.
     * (2단 사다리를 가정하면 여기서 깨진다.)
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 힌트가_하나뿐인_문제도_동작한다() = runComposeUiTest {
        val single = listOf(BcsHint(text = "키가 겹치면 어떻게 될까요?"))
        var revealed by mutableStateOf(0)
        setContent {
            BcsTheme(darkTheme = false) {
                HintStepper(hints = single, revealedCount = revealed, onRevealNext = { revealed++ })
            }
        }

        onNodeWithText("힌트 보기").performClick()
        onNodeWithText(single[0].text).assertIsDisplayed()
        // 하나뿐이므로 더 열 것이 없다.
        onNodeWithText("더 보기").assertDoesNotExist()
    }

    /**
     * 디딤 문제 진입점은 `drilldownLabel`이 있는 힌트에**만** 붙는다.
     * 힌트 3개를 전부 연 상태에서 세 번째에만 버튼이 있으므로, 정확히 1개여야 한다
     * (모든 힌트에 버튼을 달면 여기서 3이 나와 깨진다).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 디딤_문제_진입점은_선행_개념_힌트에만_붙는다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                HintStepper(hints = hints, revealedCount = hints.size, onRevealNext = {})
            }
        }

        onAllNodesWithText("더 쉬운 문제로 풀어보기").assertCountEquals(1)
    }

    /** 디딤 버튼을 누르면 해당 힌트의 인덱스가 올라온다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 디딤_문제_버튼은_힌트_인덱스를_전달한다() = runComposeUiTest {
        var drilledIndex = -1
        setContent {
            BcsTheme(darkTheme = false) {
                HintStepper(
                    hints = hints,
                    revealedCount = 3,
                    onRevealNext = {},
                    onDrilldown = { drilledIndex = it },
                )
            }
        }

        onNodeWithText("더 쉬운 문제로 풀어보기").performClick()
        assertEquals(2, drilledIndex)
    }
}
