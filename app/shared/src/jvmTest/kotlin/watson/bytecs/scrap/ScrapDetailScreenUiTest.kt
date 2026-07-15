package watson.bytecs.scrap

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import watson.bytecs.ui.theme.BcsTheme

/**
 * 스크랩 재열람 화면의 도메인 가드레일.
 *  - 이미 정답 접근이 가능한 맥락이므로 모범답안·개념을 공개한다(재열람).
 *  - 스크랩 토글과 콘텐츠 오류 신고 진입점이 이 맥락에 있다.
 */
@OptIn(ExperimentalTestApi::class)
class ScrapDetailScreenUiTest {

    private val ready = ScrapDetailUiState.Ready(
        detail = ScrapDetail(
            problemId = 7L,
            question = "서로 다른 키가 같은 버킷으로 매핑되는 현상을 부르는 용어는?",
            codeSnippet = null,
            concept = "해시 충돌",
            explanation = "서로 다른 키가 같은 버킷으로 간다.",
            acceptableAnswers = listOf("충돌", "해시 충돌", "collision"),
        ),
        scrapped = true,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        state: ScrapDetailUiState,
        onToggleScrap: () -> Unit = {},
        onReport: (Long) -> Unit = {},
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = setContent {
        BcsTheme(darkTheme = false) {
            ScrapDetailScreenContent(
                state = state,
                onToggleScrap = onToggleScrap,
                onReport = onReport,
                onBack = onBack,
                onRetry = onRetry,
            )
        }
    }

    /** ⭐️ 재열람은 이미 지나온 문제이므로 모범답안·개념을 공개한다(학습을 해치지 않는다). */
    @Test
    fun 재열람은_문제와_모범답안_개념을_보여준다() = runComposeUiTest {
        setScreen(ready)

        onNodeWithText("서로 다른 키가 같은 버킷으로 매핑되는 현상을 부르는 용어는?").assertIsDisplayed()
        onNodeWithText("모범답안").assertIsDisplayed()
        onNodeWithText("해시 충돌").assertIsDisplayed()
    }

    /** 스크랩 토글이 이 맥락에 있고, 누르면 토글 콜백을 호출한다. */
    @Test
    fun 스크랩_토글을_누르면_토글_콜백을_호출한다() = runComposeUiTest {
        var toggles = 0
        setScreen(ready, onToggleScrap = { toggles++ })

        onNodeWithContentDescription("스크랩 해제").assertIsDisplayed().performClick()

        assertEquals(1, toggles)
    }

    /** 콘텐츠 오류 신고 진입점이 있고, 누르면 해당 문제로 신고를 연다. */
    @Test
    fun 콘텐츠_오류_신고_진입점이_문제_id로_신고를_연다() = runComposeUiTest {
        var reported = -1L
        setScreen(ready, onReport = { reported = it })

        onNodeWithText("콘텐츠 오류 신고").assertIsDisplayed().performClick()

        assertEquals(7L, reported)
    }

    /** §5.12: 로드 실패는 막다른 길 없이 재시도 경로를 준다. */
    @Test
    fun 로드에_실패하면_재시도_경로를_준다() = runComposeUiTest {
        var retries = 0
        setScreen(ScrapDetailUiState.Error, onRetry = { retries++ })

        onNodeWithText("문제를 불러오지 못했어요").assertIsDisplayed()
        onNodeWithText("다시 시도하기").assertIsDisplayed().performClick()

        assertEquals(1, retries)
    }
}
