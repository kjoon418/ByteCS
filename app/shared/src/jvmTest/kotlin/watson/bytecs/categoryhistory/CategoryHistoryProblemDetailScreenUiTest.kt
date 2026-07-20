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
 * 카테고리 이력의 한 문제 상세 화면(기능 7, 1차 · 레벨3)의 도메인 가드레일.
 *  - 이미 정답으로 통과한 문제라 문제·모범답안·개념을 공개한다(스크랩 상세와 같은 성격).
 *  - '내가 쓴 답'(오너 결정으로 이력에서 제거)은 이 맥락에서도 노출하지 않는다.
 *  - 로드 실패는 막다른 길 없이 재시도 경로를 준다.
 */
@OptIn(ExperimentalTestApi::class)
class CategoryHistoryProblemDetailScreenUiTest {

    private fun item(
        problemId: Long = 1L,
        question: String = "서로 다른 키가 같은 버킷으로 매핑되는 현상을 부르는 용어는?",
    ) = CategoryHistoryItem(
        problemId = problemId,
        question = question,
        codeSnippet = null,
        difficulty = "MEDIUM",
        result = JudgeResult.CORRECT,
        concepts = listOf("해시 충돌"),
        explanation = "서로 다른 키가 같은 버킷으로 간다.",
        representativeAnswer = "해시 충돌 (collision)",
    )

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        state: CategoryHistoryProblemDetailUiState,
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = setContent {
        BcsTheme(darkTheme = false) {
            CategoryHistoryProblemDetailScreenContent(
                state = state,
                onBack = onBack,
                onRetry = onRetry,
            )
        }
    }

    /** ⭐️ 이미 정답으로 통과한 문제라 문제·모범답안·개념을 공개한다(레벨3에서만 펼친다). */
    @Test
    fun 문제_상세는_문제와_모범답안_개념을_보여준다() = runComposeUiTest {
        setScreen(CategoryHistoryProblemDetailUiState.Ready(item()))

        onNodeWithText("서로 다른 키가 같은 버킷으로 매핑되는 현상을 부르는 용어는?").assertIsDisplayed()
        onNodeWithText("모범답안").assertIsDisplayed()
        onNodeWithText("해시 충돌 (collision)").assertIsDisplayed()
        onNodeWithText("해시 충돌").assertIsDisplayed()
    }

    /** ⭐️ '내가 쓴 답'은 오너 결정으로 이력에서 제거됐다 — 상세에서도 노출하지 않는다. */
    @Test
    fun 문제_상세는_내가_쓴_답을_보여주지_않는다() = runComposeUiTest {
        setScreen(CategoryHistoryProblemDetailUiState.Ready(item()))

        onNodeWithText("내가 쓴 답").assertDoesNotExist()
    }

    /** §5.12: 로드 실패는 막다른 길 없이 재시도 경로를 준다. */
    @Test
    fun 로드에_실패하면_기록_안전을_고지하고_재시도_경로를_준다() = runComposeUiTest {
        var retries = 0
        setScreen(CategoryHistoryProblemDetailUiState.Error, onRetry = { retries++ })

        onNodeWithText("문제를 불러오지 못했어요").assertIsDisplayed()
        onNodeWithText("다시 시도하기").assertIsDisplayed().performClick()

        assertEquals(1, retries)
    }
}
