package watson.bytecs.interview

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import watson.bytecs.ui.theme.BcsTheme

/**
 * 02 홈 면접 카드 문구 분기(오너 개선안 2)를 못박는다:
 *  - 게스트라도 아직 익힌 개념이 0개면 회원 빈 상태와 **같은** 안내('오늘의 한입에서 맞히면 면접 문제가 생겨요')를 보여준다
 *    (김빠지는 "0개를 연습" 대신). 후보가 있으면 가입 유도로 구체화한다.
 */
class InterviewHomeCardUiTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 게스트라도_후보가_0개면_회원_빈_상태와_같은_안내를_보여준다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                InterviewPracticeCard(state = InterviewCardUiState.Guest(candidateCount = 0), onStart = {}, onUpgrade = {})
            }
        }
        onNodeWithText("오늘의 한입에서 맞히면 면접 문제가 생겨요").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 게스트가_익힌_개념이_있으면_가입_유도_문구를_보여준다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                InterviewPracticeCard(state = InterviewCardUiState.Guest(candidateCount = 3), onStart = {}, onUpgrade = {})
            }
        }
        onNodeWithText("가입하면", substring = true).assertIsDisplayed()
        onNodeWithText("3개", substring = true).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 회원_빈_상태는_직관적인_생성_안내를_보여준다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                InterviewPracticeCard(state = InterviewCardUiState.Empty, onStart = {}, onUpgrade = {})
            }
        }
        onNodeWithText("오늘의 한입에서 맞히면 면접 문제가 생겨요").assertIsDisplayed()
    }
}
