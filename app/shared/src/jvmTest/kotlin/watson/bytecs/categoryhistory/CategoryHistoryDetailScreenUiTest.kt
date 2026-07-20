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
 * 카테고리별 학습 이력 상세 화면(기능 7, 1차 · 레벨2)의 도메인 가드레일. 스크랩 목록과 같은 '목록(질문만)
 * →상세(전체)' 2단 구조로 통일됐다(오너 결정).
 *  - 이 레벨은 질문만 보여 준다 — 모범답안·개념·해설·심화·'내가 쓴 답'은 눌러 들어가는 레벨3에서만.
 *  - 질문 카드를 누르면 그 문제의 상세로 진입하는 콜백이 불린다.
 *  - 이 카테고리에 푼 문제가 없으면 긍정 빈 상태(UX 가이드 9)로 안내한다(오류 아님).
 */
@OptIn(ExperimentalTestApi::class)
class CategoryHistoryDetailScreenUiTest {

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
        category: String,
        state: CategoryHistoryDetailUiState,
        onOpenProblem: (Long) -> Unit = {},
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = setContent {
        BcsTheme(darkTheme = false) {
            CategoryHistoryDetailScreenContent(
                category = category,
                state = state,
                onOpenProblem = onOpenProblem,
                onBack = onBack,
                onRetry = onRetry,
            )
        }
    }

    /** 레벨2는 질문만 보여 주는 목록이다 — 항목들의 질문이 모두 렌더된다. */
    @Test
    fun 이력_목록은_항목들의_질문을_보여준다() = runComposeUiTest {
        setScreen(
            "DATA_STRUCTURE",
            CategoryHistoryDetailUiState.Ready(
                items = listOf(
                    item(1L, "해시 충돌이란?"),
                    item(2L, "트리란 무엇인가?"),
                ),
            ),
        )

        onNodeWithText("해시 충돌이란?").assertIsDisplayed()
        onNodeWithText("트리란 무엇인가?").assertIsDisplayed()
    }

    /**
     * ⭐️ 정답을 유추할 정보(모범답안)와 '내가 쓴 답'은 이 레벨에서 노출하지 않는다 — 눌러 들어가는
     * 레벨3에서만 펼친다(스크랩 목록과 같은 결).
     */
    @Test
    fun 이력_목록은_모범답안과_내가_쓴_답을_보여주지_않는다() = runComposeUiTest {
        setScreen("DATA_STRUCTURE", CategoryHistoryDetailUiState.Ready(items = listOf(item())))

        onNodeWithText("서로 다른 키가 같은 버킷으로 매핑되는 현상을 부르는 용어는?").assertIsDisplayed()
        onNodeWithText("모범답안").assertDoesNotExist()
        onNodeWithText("내가 쓴 답").assertDoesNotExist()
        onNodeWithText("해시 충돌 (collision)").assertDoesNotExist()
    }

    /** 질문 카드를 누르면 그 문제의 problemId로 상세 진입 콜백이 불린다. */
    @Test
    fun 질문_카드를_누르면_해당_problemId로_상세_진입_콜백이_불린다() = runComposeUiTest {
        var opened = -1L
        setScreen(
            "DATA_STRUCTURE",
            CategoryHistoryDetailUiState.Ready(items = listOf(item(42L, "해시 충돌이란?"))),
            onOpenProblem = { opened = it },
        )

        onNodeWithText("해시 충돌이란?").assertIsDisplayed().performClick()

        assertEquals(42L, opened)
    }

    /**
     * ⭐️ [기능 7 수용 기준] 이 카테고리에 푼 문제가 없으면 긍정 빈 상태(0문제 프레이밍)로 안내한다
     * (UX 가이드 9 — 오류처럼 보이지 않게).
     */
    @Test
    fun 문제가_없으면_긍정_빈_상태를_보여준다() = runComposeUiTest {
        setScreen("SECURITY", CategoryHistoryDetailUiState.Ready(items = emptyList()))

        onNodeWithText("아직 이 카테고리에서 푼 문제가 없어요").assertIsDisplayed()
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
