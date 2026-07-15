package watson.bytecs.report

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import watson.bytecs.ui.theme.BcsTheme

/**
 * 07 콘텐츠 오류 신고 화면의 도메인 가드레일.
 *  - 빈 입력으로는 제출할 수 없다.
 *  - 팀 계약: 카테고리 선택지 없이 자유 서술 한 필드.
 *  - 전송 실패는 무낙인 시스템 오류로 안내한다.
 */
@OptIn(ExperimentalTestApi::class)
class ReportScreenUiTest {

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        state: ReportUiState,
        onMessageChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onClose: () -> Unit = {},
    ) = setContent {
        BcsTheme(darkTheme = false) {
            ReportScreenContent(
                state = state,
                onMessageChange = onMessageChange,
                onSubmit = onSubmit,
                onClose = onClose,
            )
        }
    }

    /** ⭐️ 빈 입력 제출 불가 — 빈 신고를 서버로 내보내지 않는다. */
    @Test
    fun 입력이_비어_있으면_보내기가_비활성이다() = runComposeUiTest {
        setScreen(ReportUiState(message = ""))

        onNodeWithText("신고 보내기").assertIsNotEnabled()
    }

    /** 내용이 있으면 활성화되고, 누르면 제출 콜백이 호출된다. */
    @Test
    fun 내용이_있으면_보내기가_활성이고_제출을_호출한다() = runComposeUiTest {
        var submits = 0
        setScreen(ReportUiState(message = "정답이 틀렸어요"), onSubmit = { submits++ })

        onNodeWithText("신고 보내기").assertIsEnabled().performClick()

        assertEquals(1, submits)
    }

    /** ⭐️ 팀 계약: 시안의 카테고리 선택지를 두지 않는다(자유 서술 한 필드). */
    @Test
    fun 카테고리_선택지를_두지_않는다() = runComposeUiTest {
        setScreen(ReportUiState(message = ""))

        onNodeWithText("정답이 틀려요").assertDoesNotExist()
        onNodeWithText("문제 설명에 오류가 있어요").assertDoesNotExist()
        onNodeWithText("힌트에 오류가 있어요").assertDoesNotExist()
    }

    /** 접수 완료 후에는 감사 안내를 보이고, 마무리 액션(확인)만 남긴다. */
    @Test
    fun 접수되면_감사_안내와_확인_버튼을_보여준다() = runComposeUiTest {
        var closes = 0
        setScreen(ReportUiState(message = "정답이 틀렸어요", submitted = true), onClose = { closes++ })

        onNodeWithText("알려주셔서 고마워요! 확인 후 빠르게 반영할게요.").assertIsDisplayed()
        onNodeWithText("신고 보내기").assertDoesNotExist()
        onNodeWithText("확인").assertIsDisplayed().performClick()

        assertEquals(1, closes)
    }

    /** 전송 실패는 무낙인 시스템 오류로 안내하고 재시도 경로를 준다(§5.12). */
    @Test
    fun 전송에_실패하면_기록_안전_고지와_재시도_경로를_준다() = runComposeUiTest {
        setScreen(ReportUiState(message = "정답이 틀렸어요", submitFailed = true))

        onNodeWithText("신고를 보내지 못했어요. 잠시 후 다시 시도해 주세요.").assertIsDisplayed()
        onNodeWithText("학습 기록은 안전해요.").assertIsDisplayed()
    }
}
