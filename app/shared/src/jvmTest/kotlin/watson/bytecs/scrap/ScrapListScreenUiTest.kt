package watson.bytecs.scrap

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import watson.bytecs.ui.theme.BcsTheme

/**
 * 스크랩 목록 화면의 도메인 가드레일.
 *  - 빈 목록은 긍정 빈 상태로 안내한다(실패 아님).
 *  - 항목을 누르면 재열람으로 진입한다.
 *  - 로드 실패는 막다른 길 없이 재시도 경로를 준다.
 */
@OptIn(ExperimentalTestApi::class)
class ScrapListScreenUiTest {

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        state: ScrapListUiState,
        onOpenScrap: (Long) -> Unit = {},
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = setContent {
        BcsTheme(darkTheme = false) {
            ScrapListScreenContent(
                state = state,
                onOpenScrap = onOpenScrap,
                onBack = onBack,
                onRetry = onRetry,
            )
        }
    }

    /** ⭐️ 빈 목록은 실패가 아니라 긍정 빈 상태(§5.10)로 안내한다. */
    @Test
    fun 스크랩이_없으면_긍정_빈_상태를_보여준다() = runComposeUiTest {
        setScreen(ScrapListUiState.Ready(items = emptyList()))

        onNodeWithText("아직 스크랩한 문제가 없어요").assertIsDisplayed()
        // 빈 상태에도 실패·죄책감 문구는 없다.
        onNodeWithText("불러오지 못했어요", substring = true).assertDoesNotExist()
    }

    @Test
    fun 항목을_누르면_해당_문제_재열람으로_진입한다() = runComposeUiTest {
        var opened = -1L
        setScreen(
            ScrapListUiState.Ready(items = listOf(ScrapListItem(7L, "해시 충돌이란?", "2026-07-15"))),
            onOpenScrap = { opened = it },
        )

        onNodeWithText("해시 충돌이란?").assertIsDisplayed().performClick()

        assertEquals(7L, opened)
    }

    /** §5.12: 로드 실패는 막다른 길 없이 재시도 경로를 준다. */
    @Test
    fun 로드에_실패하면_기록_안전을_고지하고_재시도_경로를_준다() = runComposeUiTest {
        var retries = 0
        setScreen(ScrapListUiState.Error, onRetry = { retries++ })

        onNodeWithText("스크랩을 불러오지 못했어요").assertIsDisplayed()
        onNodeWithText("다시 시도하기").assertIsDisplayed().performClick()

        assertEquals(1, retries)
    }
}
