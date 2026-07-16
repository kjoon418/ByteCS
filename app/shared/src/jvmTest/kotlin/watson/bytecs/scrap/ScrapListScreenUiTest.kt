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
            ScrapListUiState.Ready(items = listOf(ScrapListItem(7L, "해시 충돌이란?", "2026-07-15T09:00:00Z"))),
            onOpenScrap = { opened = it },
        )

        onNodeWithText("해시 충돌이란?").assertIsDisplayed().performClick()

        assertEquals(7L, opened)
    }

    /**
     * ⭐️ 회수·삭제된 문제(question=null)는 '더 이상 볼 수 없어요'로 담담히 표시하고, 재열람 진입을 막는다
     * (서버 상세가 404라 눌러도 막다른 길이다 — 서버 [결정], 도메인 명세 406행).
     * 정상 항목과 함께 두어, 회수 항목만 진입이 막히고 정상 항목은 그대로 열리는지 양쪽을 못박는다.
     */
    @Test
    fun 회수된_문제는_더_이상_볼_수_없음으로_표시하고_진입을_막는다() = runComposeUiTest {
        val opened = mutableListOf<Long>()
        setScreen(
            ScrapListUiState.Ready(
                items = listOf(
                    ScrapListItem(7L, question = null, scrappedAt = "2026-07-15T09:00:00Z"),
                    ScrapListItem(8L, question = "정상 문제", scrappedAt = "2026-07-15T09:00:00Z"),
                ),
            ),
            onOpenScrap = { opened += it },
        )

        // 회수 항목: 안내 문구가 뜨고, 눌러도 재열람으로 들어가지 않는다.
        onNodeWithText("더 이상 볼 수 없어요").assertIsDisplayed().performClick()
        assertEquals(emptyList(), opened, "회수된 문제는 재열람 진입을 열지 않는다")

        // 정상 항목은 그대로 열린다(회수 처리가 목록 전체를 막다른 길로 만들지 않는다).
        onNodeWithText("정상 문제").assertIsDisplayed().performClick()
        assertEquals(listOf(8L), opened)
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
