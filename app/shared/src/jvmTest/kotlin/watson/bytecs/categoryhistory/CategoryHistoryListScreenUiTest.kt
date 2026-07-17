package watson.bytecs.categoryhistory

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import watson.bytecs.problem.JudgeResult
import watson.bytecs.ui.theme.BcsTheme

/**
 * 카테고리별 학습 이력 목록 화면(기능 7, 1차)의 도메인 가드레일.
 *  - 8개 카테고리를 서버가 준 순서로 한글 라벨과 함께 보여준다.
 *  - 문제 없는 카테고리는 '준비 중'(긍정 빈 상태, UX 가이드 9 — 오류처럼 보이지 않게).
 *  - 로드 실패는 막다른 길 없이 재시도 경로를 준다.
 */
@OptIn(ExperimentalTestApi::class)
class CategoryHistoryListScreenUiTest {

    private fun item(problemId: Long, question: String) = CategoryHistoryItem(
        problemId = problemId,
        question = question,
        codeSnippet = null,
        difficulty = "MEDIUM",
        submittedAnswer = "내가 쓴 답",
        result = JudgeResult.CORRECT,
        concepts = listOf("해시 충돌"),
        explanation = "해설",
        representativeAnswer = "해시 충돌 (collision)",
    )

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        state: CategoryHistoryListUiState,
        onOpenCategory: (String) -> Unit = {},
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = setContent {
        BcsTheme(darkTheme = false) {
            CategoryHistoryListScreenContent(
                state = state,
                onOpenCategory = onOpenCategory,
                onBack = onBack,
                onRetry = onRetry,
            )
        }
    }

    /** 8개 카테고리가 한글 라벨로 렌더된다(순서는 서버가 준 그대로). */
    @Test
    fun 카테고리_8개가_한글_라벨로_보인다() = runComposeUiTest {
        setScreen(
            CategoryHistoryListUiState.Ready(
                groups = listOf(
                    CategoryHistoryGroup("DATA_STRUCTURE", listOf(item(1L, "해시 충돌이란?"))),
                    CategoryHistoryGroup("ALGORITHM", emptyList()),
                    CategoryHistoryGroup("OPERATING_SYSTEM", emptyList()),
                    CategoryHistoryGroup("NETWORK", emptyList()),
                    CategoryHistoryGroup("DATABASE", emptyList()),
                    CategoryHistoryGroup("COMPUTER_ARCHITECTURE", emptyList()),
                    CategoryHistoryGroup("SOFTWARE_ENGINEERING", emptyList()),
                    CategoryHistoryGroup("SECURITY", emptyList()),
                ),
            ),
        )

        onNodeWithText("자료구조").assertIsDisplayed()
        onNodeWithText("알고리즘").assertIsDisplayed()
        onNodeWithText("운영체제").assertIsDisplayed()
        onNodeWithText("네트워크").assertIsDisplayed()
        onNodeWithText("데이터베이스").assertIsDisplayed()
        onNodeWithText("컴퓨터구조").assertIsDisplayed()
        onNodeWithText("소프트웨어공학").assertIsDisplayed()
        onNodeWithText("보안").assertIsDisplayed()
    }

    /** 푼 문제가 있는 카테고리는 문제 수를 보여준다. */
    @Test
    fun 푼_문제가_있으면_문제_수를_보여준다() = runComposeUiTest {
        setScreen(
            CategoryHistoryListUiState.Ready(
                groups = listOf(
                    CategoryHistoryGroup("DATA_STRUCTURE", listOf(item(1L, "해시 충돌이란?"), item(2L, "트리란?"))),
                ),
            ),
        )

        onNodeWithText("2문제").assertIsDisplayed()
    }

    /**
     * ⭐️ [기능 7 수용 기준] 푼 문제가 0개인 카테고리는 '준비 중'으로 표시한다 — 빈 목록이 오류로
     * 보이지 않게(UX 가이드 9 긍정 빈 상태).
     */
    @Test
    fun 푼_문제가_없으면_준비_중으로_보여준다() = runComposeUiTest {
        setScreen(
            CategoryHistoryListUiState.Ready(
                groups = listOf(CategoryHistoryGroup("SECURITY", emptyList())),
            ),
        )

        onNodeWithText("준비 중").assertIsDisplayed()
        onNodeWithText("불러오지 못했어요", substring = true).assertDoesNotExist()
    }

    /** 카테고리를 누르면 그 카테고리 코드로 콜백된다. */
    @Test
    fun 카테고리를_누르면_해당_코드로_상세_진입_콜백이_불린다() = runComposeUiTest {
        var opened = ""
        setScreen(
            CategoryHistoryListUiState.Ready(groups = listOf(CategoryHistoryGroup("DATABASE", emptyList()))),
            onOpenCategory = { opened = it },
        )

        onNodeWithText("데이터베이스").assertIsDisplayed().performClick()

        assertEquals("DATABASE", opened)
    }

    /** §5.12: 로드 실패는 막다른 길 없이 재시도 경로를 준다. */
    @Test
    fun 로드에_실패하면_기록_안전을_고지하고_재시도_경로를_준다() = runComposeUiTest {
        var retries = 0
        setScreen(CategoryHistoryListUiState.Error, onRetry = { retries++ })

        onNodeWithText("학습 이력을 불러오지 못했어요").assertIsDisplayed()
        onNodeWithText("다시 시도하기").assertIsDisplayed().performClick()

        assertEquals(1, retries)
    }
}
