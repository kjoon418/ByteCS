package watson.bytecs.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import watson.bytecs.ui.theme.BcsTheme

/**
 * 01 온보딩 시작 화면의 도메인 가드레일. 픽셀이 아니라 **무낙인·저마찰** 규율을 못박는다:
 *  - 가입을 강요하지 않는다("바로 시작하기"가 유일한 Primary, 로그인은 보조).
 *  - 승계 모델 안심 문구가 시작 전에 고지된다.
 */
@OptIn(ExperimentalTestApi::class)
class OnboardingScreenUiTest {

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        onStart: () -> Unit = {},
        onLogin: () -> Unit = {},
    ) = setContent {
        BcsTheme(darkTheme = false) {
            OnboardingScreen(onStart = onStart, onLogin = onLogin)
        }
    }

    @Test
    fun 가치_제안과_시작_CTA를_보여준다() = runComposeUiTest {
        setScreen()

        onNodeWithText("5분이면 CS 한입,\n오늘도 가볍게 채워보세요.").assertIsDisplayed()
        onNodeWithText("바로 시작하기").assertIsDisplayed()
    }

    /** ⭐️ 가입 강요 금지 — "바로 시작하기"는 게스트로 즉시 시작하는 경로다(콜백 1회). */
    @Test
    fun 바로_시작하기는_시작_콜백을_호출한다() = runComposeUiTest {
        var starts = 0
        setScreen(onStart = { starts++ })

        onNodeWithText("바로 시작하기").assertIsDisplayed().performClick()

        assertEquals(1, starts)
    }

    /** 이미 계정이 있는 사용자는 로그인으로 갈 수 있다(보조 경로). */
    @Test
    fun 로그인_링크는_로그인_콜백을_호출한다() = runComposeUiTest {
        var logins = 0
        setScreen(onLogin = { logins++ })

        onNodeWithText("이미 계정이 있나요? 로그인").assertIsDisplayed().performClick()

        assertEquals(1, logins)
    }

    /** 승계 모델 안심 문구가 시작 전에 고지된다(무낙인·저마찰). */
    @Test
    fun 가입하지_않아도_시작할_수_있다는_안심_문구를_보여준다() = runComposeUiTest {
        setScreen()

        onNodeWithText("가입하지 않아도 시작할 수 있어요.\n나중에 가입하면 학습 기록이 그대로 이어져요.")
            .assertIsDisplayed()
    }

    /** ⛔ 온보딩에서 가입을 강요하는 문구·차단이 없다. */
    @Test
    fun 가입을_강요하는_문구가_없다() = runComposeUiTest {
        setScreen()

        onNodeWithText("가입하기").assertDoesNotExist()
        onNodeWithText("가입이 필요", substring = true).assertDoesNotExist()
    }
}
