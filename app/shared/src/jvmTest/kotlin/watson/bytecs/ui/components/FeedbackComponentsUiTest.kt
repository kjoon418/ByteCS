package watson.bytecs.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import watson.bytecs.ui.theme.BcsTheme

/**
 * DESIGN_SYSTEM.md §5.6 답 피드백 · §5.7 정답 공개 흐름.
 *
 * 카피 자체가 계약이다 — "오답!" 같은 처벌 문구로 바뀌면 원칙 5(무낙인) 위반이므로 문구를 테스트로 고정한다.
 */
class FeedbackComponentsUiTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 정답_피드백은_개념과_해설을_함께_보여준다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                CorrectFeedback(concepts = listOf("해시 충돌"), explanation = "서로 다른 키가 같은 버킷으로 갑니다.")
            }
        }

        onNodeWithText("맞았어요!").assertIsDisplayed()
        onNodeWithText("해시 충돌").assertIsDisplayed()
        onNodeWithText("서로 다른 키가 같은 버킷으로 갑니다.").assertIsDisplayed()
    }

    /** 개념·해설이 없는 문제도 정답 피드백은 온전해야 한다(빈 칩·빈 줄 금지). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 정답_피드백은_개념과_해설이_없어도_동작한다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                CorrectFeedback()
            }
        }

        onNodeWithText("맞았어요!").assertIsDisplayed()
    }

    /** ⭐️ 불일치는 '아직'이다 — 처벌 문구가 아니라 재시도 초대여야 한다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 불일치_넛지는_중립_격려_문구를_쓴다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { RetryNudge() }
        }

        onNodeWithText("아직이에요, 다시 해볼까요?").assertIsDisplayed()
        // 처벌 신호 금지(§5.6).
        onNodeWithText("오답", substring = true).assertDoesNotExist()
        onNodeWithText("틀렸", substring = true).assertDoesNotExist()
    }

    /**
     * ⭐️ 근접은 불일치와 **다른 톤·다른 문구**다: 오타 때문이라는 사실만 알린다.
     * 두 넛지가 같은 문구로 수렴하면 "생각은 맞았다"는 정보가 사라진다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 근접_넛지는_불일치와_구별되는_문구를_쓴다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { NearMissNudge() }
        }

        onNodeWithText("거의 맞았어요, 오타를 확인해보세요").assertIsDisplayed()
        // 불일치 문구와 섞이지 않는다.
        onNodeWithText("아직이에요, 다시 해볼까요?").assertDoesNotExist()
    }

    /** §5.7 정답 보기는 사용자가 누를 때만 열린다 — 버튼은 콜백만 쏘고 스스로 정답을 그리지 않는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 정답_보기_버튼은_클릭을_콜백으로_전달한다() = runComposeUiTest {
        var revealed = 0
        setContent {
            BcsTheme(darkTheme = false) { RevealAnswerButton(onClick = { revealed++ }) }
        }

        onNodeWithText("정답 보기").assertIsDisplayed().performClick()

        assertEquals(1, revealed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 모범답안_블록은_여러_정답과_해설을_보여준다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                ModelAnswerBlock(
                    answers = listOf("해시 충돌", "충돌"),
                    explanation = "같은 버킷에 매핑되는 현상이에요.",
                )
            }
        }

        onNodeWithText("모범답안").assertIsDisplayed()
        onNodeWithText("해시 충돌  ·  충돌").assertIsDisplayed()
        onNodeWithText("같은 버킷에 매핑되는 현상이에요.").assertIsDisplayed()
    }

    /**
     * ⭐️ TypeAlongField는 정답 문자열을 받지 않는다 — 플레이스홀더에 정답이 새는 게 구조적으로 불가능하다.
     * 여기서는 안내 문구가 '벌'이 아닌 '따라 써 보기' 톤인지, 플레이스홀더가 일반 문구인지만 확인한다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 따라_입력_칸은_정답을_노출하지_않는_일반_안내만_보여준다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                TypeAlongField(value = "", onValueChange = {})
            }
        }

        onNodeWithText("정답을 따라 적어 볼까요?").assertIsDisplayed()
        onNodeWithText("위 정답을 따라 적어 보세요").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 따라_입력_칸은_입력을_콜백으로_전달한다() = runComposeUiTest {
        var typed by mutableStateOf("")
        setContent {
            BcsTheme(darkTheme = false) {
                TypeAlongField(value = typed, onValueChange = { typed = it })
            }
        }

        onNodeWithText("위 정답을 따라 적어 보세요").performTextInput("해시 충돌")

        assertEquals("해시 충돌", typed)
    }

    /** ⭐️ §5.7: 심화 정보가 없으면 '더 알아보기' 자체를 그리지 않는다(빈 껍데기 금지). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 심화_정보가_없으면_더_알아보기를_그리지_않는다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                EnrichmentBlock(content = null)
            }
        }

        onNodeWithText("더 알아보기").assertDoesNotExist()
    }

    /**
     * [결정 2026-07-16] 심화 정보는 더 이상 토글이 아니다 — 정답 처리 시점에 별도 조작 없이 바로 보인다
     * (확인하려 매번 한 번 더 누르는 마찰 제거).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 심화_정보는_바로_보인다() = runComposeUiTest {
        val body = "해시 충돌은 생일 문제와 연결돼요."
        setContent {
            BcsTheme(darkTheme = false) {
                EnrichmentBlock(content = body)
            }
        }

        onNodeWithText("더 알아보기").assertIsDisplayed()
        onNodeWithText(body).assertIsDisplayed()
        // 접기 버튼은 없다 — 토글 자체가 사라졌다.
        onNodeWithText("접기").assertDoesNotExist()
    }
}
