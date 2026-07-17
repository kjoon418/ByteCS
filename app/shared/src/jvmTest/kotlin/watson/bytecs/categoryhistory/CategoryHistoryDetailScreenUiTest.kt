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
 * 카테고리별 학습 이력 상세 화면(기능 7, 1차)의 도메인 가드레일.
 *  - 이미 정답으로 통과한 문제라 모범답안·개념·해설을 공개한다(재열람과 같은 성격).
 *  - 추가 학습에서만 푼 문제는 submittedAnswer가 null이 정상이다 — '—'로 대체 표기한다.
 *  - 이 카테고리에 푼 문제가 없으면 '준비 중' 긍정 빈 상태(UX 가이드 9)로 안내한다.
 */
@OptIn(ExperimentalTestApi::class)
class CategoryHistoryDetailScreenUiTest {

    private fun item(
        problemId: Long = 1L,
        question: String = "서로 다른 키가 같은 버킷으로 매핑되는 현상을 부르는 용어는?",
        submittedAnswer: String? = "해시 충돌",
    ) = CategoryHistoryItem(
        problemId = problemId,
        question = question,
        codeSnippet = null,
        difficulty = "MEDIUM",
        submittedAnswer = submittedAnswer,
        result = JudgeResult.CORRECT,
        concepts = listOf("해시 충돌"),
        explanation = "서로 다른 키가 같은 버킷으로 간다.",
        representativeAnswer = "해시 충돌 (collision)",
    )

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        category: String,
        state: CategoryHistoryDetailUiState,
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = setContent {
        BcsTheme(darkTheme = false) {
            CategoryHistoryDetailScreenContent(
                category = category,
                state = state,
                onBack = onBack,
                onRetry = onRetry,
            )
        }
    }

    /** 이미 정답으로 통과한 문제라 문제·모범답안·개념을 공개한다. */
    @Test
    fun 이력_항목은_문제와_모범답안_개념을_보여준다() = runComposeUiTest {
        setScreen("DATA_STRUCTURE", CategoryHistoryDetailUiState.Ready(items = listOf(item())))

        onNodeWithText("서로 다른 키가 같은 버킷으로 매핑되는 현상을 부르는 용어는?").assertIsDisplayed()
        onNodeWithText("모범답안").assertIsDisplayed()
        onNodeWithText("해시 충돌 (collision)").assertIsDisplayed()
    }

    /**
     * ⭐️ 추가 학습에서만 푼 문제는 submittedAnswer가 null이 정상이다(서버 [결정], 도메인 구조상 제출 답
     * 미보존) — 화면은 이를 오류가 아니라 '—'로 대체 표기해야 한다.
     */
    @Test
    fun 제출_답이_null이면_대시로_표기한다() = runComposeUiTest {
        setScreen(
            "DATA_STRUCTURE",
            CategoryHistoryDetailUiState.Ready(items = listOf(item(submittedAnswer = null))),
        )

        onNodeWithText("내가 쓴 답").assertIsDisplayed()
        onNodeWithText("—").assertIsDisplayed()
    }

    /**
     * 제출 답이 있으면 그대로 보여준다. 개념 칩과 텍스트가 겹치지 않는 값을 써서(세션 화면 테스트와 같은
     * 관례) 어느 노드를 못 박았는지 분명히 한다.
     */
    @Test
    fun 제출_답이_있으면_그대로_보여준다() = runComposeUiTest {
        setScreen(
            "DATA_STRUCTURE",
            CategoryHistoryDetailUiState.Ready(items = listOf(item(submittedAnswer = "해싱"))),
        )

        onNodeWithText("해싱").assertIsDisplayed()
    }

    /**
     * ⭐️ [기능 7 수용 기준] 이 카테고리에 푼 문제가 없으면 '준비 중' 긍정 빈 상태로 안내한다
     * (UX 가이드 9 — 오류처럼 보이지 않게).
     */
    @Test
    fun 문제가_없으면_준비_중_긍정_빈_상태를_보여준다() = runComposeUiTest {
        setScreen("SECURITY", CategoryHistoryDetailUiState.Ready(items = emptyList()))

        onNodeWithText("준비 중이에요", substring = true).assertIsDisplayed()
        onNodeWithText("불러오지 못했어요", substring = true).assertDoesNotExist()
        onNodeWithText("실패", substring = true).assertDoesNotExist()
    }

    /** §5.12: 로드 실패는 막다른 길 없이 재시도 경로를 준다. */
    @Test
    fun 로드에_실패하면_기록_안전을_고지하고_재시도_경로를_준다() = runComposeUiTest {
        var retries = 0
        setScreen("DATA_STRUCTURE", CategoryHistoryDetailUiState.Error, onRetry = { retries++ })

        onNodeWithText("문제를 불러오지 못했어요").assertIsDisplayed()
        onNodeWithText("다시 시도하기").assertIsDisplayed().performClick()

        assertEquals(1, retries)
    }
}
