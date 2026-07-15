package watson.bytecs.problem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import watson.bytecs.ui.theme.BcsTheme

/**
 * 03 시안을 재사용한 **추가 연습**('조금 더 풀기') 화면의 도메인 가드레일 테스트.
 *
 * 여기서 검증하는 건 컴포넌트 하나의 모양이 아니라 **화면이 상태를 어떻게 엮는가**다 —
 * 정답이 언제 노출되는지, 무엇이 풀기 전에 숨겨지는지는 화면의 배선에서 뚫린다.
 * 그래서 진짜 [ProblemViewModel] + [FakeProblemRepository]로 실제 판정을 태워 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ProblemScreenUiTest {

    // viewModelScope는 Main 디스패처를 쓴다. Unconfined로 두면 최초 로드가 즉시 끝나 Ready부터 관찰된다.
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    /** 시드 1번: 정답 "스레드", 개념 "프로세스와 스레드". 질문 문구에 정답이 들어 있지 않아 노출 검증에 적합하다. */
    private val question =
        "한 프로세스 안에서 스택 등 일부를 제외한 자원을 공유하며 실행되는 흐름의 단위는?"

    private fun setUpScreen(test: androidx.compose.ui.test.ComposeUiTest) = with(test) {
        setContent {
            BcsTheme(darkTheme = false) {
                ProblemScreen(
                    viewModel = ProblemViewModel(FakeProblemRepository()),
                    onOpenAccount = {},
                    onBack = {},
                )
            }
        }
    }

    /**
     * ⭐️ 정답 비노출 — 이 화면 최대의 가드레일.
     * 풀기 전에는 정답 문자열("스레드")이 화면 어디에도(플레이스홀더 포함) 없어야 한다.
     * 시안 원본이 플레이스홀더에 `(예: 선점형)`으로 정답을 흘렸던 자리라 문자열 단위로 못박는다.
     */
    @Test
    fun 풀기_전에는_정답_문자열이_화면_어디에도_없다() = runComposeUiTest {
        setUpScreen(this)

        onNodeWithText(question).assertIsDisplayed()
        onNodeWithText("정답을 입력해 보세요").assertIsDisplayed()
        // 허용답 전부. 플레이스홀더·난이도·CTA 어디에도 새면 안 된다.
        onNodeWithText("스레드", substring = true).assertDoesNotExist()
        onNodeWithText("쓰레드", substring = true).assertDoesNotExist()
        onNodeWithText("thread", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    /**
     * ⭐️ §5.9 개념 칩은 풀기 전 숨김 — 개념명이 정답을 스포일한다
     * (여기서는 개념 "프로세스와 스레드"가 정답 "스레드"를 통째로 품고 있다).
     */
    @Test
    fun 개념은_정답을_맞히기_전까지_보이지_않는다() = runComposeUiTest {
        setUpScreen(this)

        onNodeWithText("프로세스와 스레드").assertDoesNotExist()

        onNodeWithText("정답을 입력해 보세요").performTextInput("스레드")
        onNodeWithText("정답 확인하기").performClick()

        // 정답 처리 이후에야 개념·해설이 열린다.
        onNodeWithText("맞았어요!").assertIsDisplayed()
        onNodeWithText("프로세스와 스레드").assertIsDisplayed()
    }

    /** ⭐️ 무낙인 — 불일치는 '아직'이다. 처벌 문구가 화면에 실리면 안 된다. */
    @Test
    fun 불일치는_처벌_문구_없이_중립_넛지로_안내한다() = runComposeUiTest {
        setUpScreen(this)

        onNodeWithText("정답을 입력해 보세요").performTextInput("프로세스")
        onNodeWithText("정답 확인하기").performClick()

        onNodeWithText("아직이에요, 다시 해볼까요?").assertIsDisplayed()
        onNodeWithText("오답", substring = true).assertDoesNotExist()
        onNodeWithText("틀렸", substring = true).assertDoesNotExist()
        // 불일치는 정답을 떠먹이지 않는다.
        onNodeWithText("스레드", substring = true).assertDoesNotExist()
    }

    /**
     * ⭐️ 근접(오탈자)은 불일치와 **다른 톤**으로 갈린다.
     * "스래드"는 정답 "스레드"와 편집거리 1이라 NEAR_MISS로 판정된다 — 두 넛지가 뭉개지면
     * "생각은 맞았고 오타만 보면 된다"는 정보가 사라진다.
     */
    @Test
    fun 근접은_불일치와_구별되는_넛지로_안내한다() = runComposeUiTest {
        setUpScreen(this)

        onNodeWithText("정답을 입력해 보세요").performTextInput("스래드")
        onNodeWithText("정답 확인하기").performClick()

        onNodeWithText("거의 맞았어요, 오타를 확인해보세요").assertIsDisplayed()
        onNodeWithText("아직이에요, 다시 해볼까요?").assertDoesNotExist()
        // 근접이어도 정답·개념은 여전히 비노출이다.
        onNodeWithText("프로세스와 스레드").assertDoesNotExist()
    }

    /**
     * ⭐️ 추가 연습에는 세션 진행 인디케이터를 두지 않는다(`03` A-1은 '오늘의 한입 중 몇 번째'로 정의).
     * 세션 분량에 속하지 않는 연습에 `1 / 5`를 그리면 없는 목표를 지어낸다.
     *
     * ⚠️ 반드시 **contentDescription**으로 찾는다. SessionProgress는 `clearAndSetSemantics`로 자식
     * 시맨틱을 지우고 요약 설명만 남기므로, `onNodeWithText("1 / 5")`로 검사하면 인디케이터가 도로
     * 살아나도 통과하는 **빈 테스트**가 된다(실제로 변이로 확인함).
     */
    @Test
    fun 추가_연습에는_세션_진행_인디케이터를_그리지_않는다() = runComposeUiTest {
        setUpScreen(this)

        onNodeWithContentDescription("번째 문제", substring = true).assertDoesNotExist()
        // 나가기 진입점은 남는다(막다른 길 금지).
        onNodeWithText("나가기").assertIsDisplayed()
    }

    /** 빈 답으로는 제출할 수 없다 — 빈 제출을 오답으로 기록하지 않는다. */
    @Test
    fun 답이_비어_있으면_제출_버튼이_비활성이다() = runComposeUiTest {
        setUpScreen(this)

        onNodeWithText("정답 확인하기").assertIsNotEnabled()
    }
}
