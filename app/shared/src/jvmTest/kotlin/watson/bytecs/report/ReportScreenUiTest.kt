package watson.bytecs.report

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import watson.bytecs.ui.theme.BcsTheme

/**
 * 07 콘텐츠 오류 신고 화면의 도메인 가드레일(team-plan.md §B [계약 v2]).
 *  - 신고 유형(단일 선택, 필수) 4개를 보여준다.
 *  - 유형을 고르지 않으면 제출할 수 없고, 상세 내용은 비어도 제출할 수 있다.
 *  - 전송 실패는 무낙인 시스템 오류로 안내한다.
 */
@OptIn(ExperimentalTestApi::class)
class ReportScreenUiTest {

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        state: ReportUiState,
        onCategorySelect: (ReportCategory) -> Unit = {},
        onMessageChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onClose: () -> Unit = {},
    ) = setContent {
        BcsTheme(darkTheme = false) {
            ReportScreenContent(
                state = state,
                onCategorySelect = onCategorySelect,
                onMessageChange = onMessageChange,
                onSubmit = onSubmit,
                onClose = onClose,
            )
        }
    }

    /** ⭐️ 시안대로 신고 유형 4개를 모두 보여준다. */
    @Test
    fun 신고_유형_네_가지를_모두_보여준다() = runComposeUiTest {
        setScreen(ReportUiState())

        onNodeWithText("정답이 틀려요").assertIsDisplayed()
        onNodeWithText("문제 설명에 오류가 있어요").assertIsDisplayed()
        onNodeWithText("힌트에 오류가 있어요").assertIsDisplayed()
        onNodeWithText("기타").assertIsDisplayed()
    }

    /** ⭐️ 유형 미선택으로는 제출할 수 없다. */
    @Test
    fun 유형을_선택하지_않으면_보내기가_비활성이다() = runComposeUiTest {
        setScreen(ReportUiState(category = null))

        onNodeWithText("신고 보내기").assertIsNotEnabled()
    }

    /** ⭐️ 상세 내용은 선택이다 — 유형만 골라도 제출 가능하다. */
    @Test
    fun 유형만_선택하면_상세_내용_없이도_보내기가_활성이다() = runComposeUiTest {
        setScreen(ReportUiState(category = ReportCategory.WRONG_ANSWER, message = ""))

        onNodeWithText("신고 보내기").assertIsEnabled()
    }

    /** 유형을 누르면 선택 콜백이 그 유형으로 호출된다. */
    @Test
    fun 유형을_누르면_해당_유형으로_선택_콜백을_호출한다() = runComposeUiTest {
        var selected: ReportCategory? = null
        setScreen(ReportUiState(), onCategorySelect = { selected = it })

        onNodeWithText("힌트에 오류가 있어요").assertIsDisplayed().performClick()

        assertEquals(ReportCategory.HINT_ERROR, selected)
    }

    /** 유형이 선택된 상태에서 보내기를 누르면 제출 콜백이 호출된다. */
    @Test
    fun 유형_선택_후_보내기를_누르면_제출을_호출한다() = runComposeUiTest {
        var submits = 0
        setScreen(ReportUiState(category = ReportCategory.OTHER), onSubmit = { submits++ })

        onNodeWithText("신고 보내기").assertIsEnabled().performClick()

        assertEquals(1, submits)
    }

    /** ⭐️ 상세 내용 라벨이 "(선택)"임을 명시해 필수가 아님을 알린다. */
    @Test
    fun 상세_내용_라벨이_선택임을_밝힌다() = runComposeUiTest {
        setScreen(ReportUiState())

        onNodeWithText("상세 내용 (선택)").assertIsDisplayed()
    }

    /** 접수 완료 후에는 감사 안내를 보이고, 마무리 액션(확인)만 남긴다. */
    @Test
    fun 접수되면_감사_안내와_확인_버튼을_보여준다() = runComposeUiTest {
        var closes = 0
        setScreen(
            ReportUiState(category = ReportCategory.WRONG_ANSWER, submitted = true),
            onClose = { closes++ },
        )

        onNodeWithText("알려주셔서 고마워요! 확인 후 빠르게 반영할게요.").assertIsDisplayed()
        onNodeWithText("신고 보내기").assertDoesNotExist()
        onNodeWithText("확인").assertIsDisplayed().performClick()

        assertEquals(1, closes)
    }

    /** 전송 실패는 무낙인 시스템 오류로 안내하고 재시도 경로를 준다(§5.12). */
    @Test
    fun 전송에_실패하면_기록_안전_고지와_재시도_경로를_준다() = runComposeUiTest {
        setScreen(ReportUiState(category = ReportCategory.WRONG_ANSWER, submitFailed = true))

        // 유형 4개가 위에 있어 화면 밖으로 밀릴 수 있다 — 스크롤해 노출을 확인한다.
        onNodeWithText("신고를 보내지 못했어요. 잠시 후 다시 시도해 주세요.").performScrollTo().assertIsDisplayed()
        onNodeWithText("학습 기록은 안전해요.").performScrollTo().assertIsDisplayed()
    }
}
