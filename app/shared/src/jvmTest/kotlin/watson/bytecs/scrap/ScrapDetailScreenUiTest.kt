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
            concepts = listOf("해시 충돌"),
            explanation = "서로 다른 키가 같은 버킷으로 간다.",
            representativeAnswer = "해시 충돌 (collision)",
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

    /**
     * [2026-07-16] 허용답 나열이 아니라 화면 표시용 대표 정답 하나만 보인다.
     * 병기 표기("해시 충돌 (collision)")가 그대로 하나의 문자열로 뜨는지 확인한다.
     */
    @Test
    fun 재열람은_허용답_나열_대신_대표_정답_하나를_보여준다() = runComposeUiTest {
        setScreen(ready)

        onNodeWithText("해시 충돌 (collision)").assertIsDisplayed()
        // 예전 나열 구분자(가운뎃점)로 이어붙인 문자열은 더 이상 없다.
        onNodeWithText("·", substring = true).assertDoesNotExist()
    }

    /** 문제가 여러 개념에 태깅됐으면(N—M) 재열람 화면에도 칩이 모두 보인다. */
    @Test
    fun 개념이_여러_개면_재열람에도_모두_보인다() = runComposeUiTest {
        setScreen(
            ScrapDetailUiState.Ready(
                detail = ready.detail.copy(concepts = listOf("해시 충돌", "해시 함수")),
                scrapped = true,
            ),
        )

        onNodeWithText("해시 충돌").assertIsDisplayed()
        onNodeWithText("해시 함수").assertIsDisplayed()
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

    // ── '더 알아보기'(심화 정보, §5.7) ────────────────────────────────────────

    /** [결정 2026-07-16] 재열람에 심화 정보가 있으면 '더 알아보기'가 별도 조작 없이 바로 보인다. */
    @Test
    fun 재열람에_심화_정보가_있으면_더_알아보기가_바로_보인다() = runComposeUiTest {
        setScreen(
            ScrapDetailUiState.Ready(
                detail = ready.detail.copy(enrichment = "해시 충돌은 생일 문제와 연결돼요."),
                scrapped = true,
            ),
        )

        onNodeWithText("더 알아보기").assertIsDisplayed()
        onNodeWithText("해시 충돌은 생일 문제와 연결돼요.").assertIsDisplayed()
    }

    /** 심화 정보가 없는 재열람에는 '더 알아보기' 진입점 자체가 없다(빈 껍데기 금지). */
    @Test
    fun 심화_정보가_없는_재열람에는_더_알아보기가_없다() = runComposeUiTest {
        setScreen(ready)

        onNodeWithText("더 알아보기").assertDoesNotExist()
    }
}
