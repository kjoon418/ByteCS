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
import watson.bytecs.problem.Enrichment
import watson.bytecs.problem.EnrichmentItem
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

    /**
     * [2026-07-16] 허용답을 나열하지 않는다 — 화면 표시용 대표 정답 하나만 보여준다(오너 결정).
     * 병기 표기("인덱스 (index)" 형식) 예시로 대표 정답이 그대로 한 문자열로 뜨는지 확인한다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 모범답안_블록은_대표_정답과_해설을_보여준다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                ModelAnswerBlock(
                    representativeAnswer = "해시 충돌 (collision)",
                    explanation = "같은 버킷에 매핑되는 현상이에요.",
                )
            }
        }

        onNodeWithText("모범답안").assertIsDisplayed()
        onNodeWithText("해시 충돌 (collision)").assertIsDisplayed()
        onNodeWithText("같은 버킷에 매핑되는 현상이에요.").assertIsDisplayed()
    }

    /**
     * 정답 확정 입력란은 대표 정답 바로 아래 확인 라인을 함께 보여준다(시안 52~66행).
     * XP 등 게이미피케이션 문구는 없다(미도입 결정).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 확정_입력란은_확인_라인을_함께_보여준다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                ConfirmedAnswerField(representativeAnswer = "해시 충돌 (collision)")
            }
        }

        onNodeWithText("해시 충돌 (collision)").assertIsDisplayed()
        onNodeWithText("완벽해요! 정확한 정답입니다.").assertIsDisplayed()
        onNodeWithText("XP", substring = true).assertDoesNotExist()
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
                EnrichmentBlock(enrichment = null)
            }
        }

        onNodeWithText("더 알아보기").assertDoesNotExist()
    }

    private val sampleEnrichment = Enrichment(
        title = "왜 충돌이 발생할까요?",
        body = "해시 테이블은 무한한 데이터를 유한한 배열에 매핑하기 때문에 충돌이 반드시 발생합니다.",
        items = listOf(
            EnrichmentItem(title = "해결책 01. 체이닝", description = "같은 인덱스를 연결 리스트로 잇는 방식입니다."),
            EnrichmentItem(title = "해결책 02. 개방 주소법", description = "충돌 시 옆 빈 칸을 찾아 들어가는 방식입니다."),
        ),
        quote = "좋은 해시 함수는 충돌을 최소화합니다.",
    )

    /**
     * [결정 2026-07-16] 심화 정보는 더 이상 토글이 아니다 — 정답 처리 시점에 별도 조작 없이 바로 보인다
     * (확인하려 매번 한 번 더 누르는 마찰 제거). [2026-07-16] 시안 구조(제목·리드·항목 카드·인용)로 렌더한다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 심화_정보는_제목_리드_항목_인용을_구조로_보여준다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                EnrichmentBlock(enrichment = sampleEnrichment)
            }
        }

        onNodeWithText("더 알아보기").assertIsDisplayed()
        onNodeWithText(sampleEnrichment.title).assertIsDisplayed()
        onNodeWithText(sampleEnrichment.body).assertIsDisplayed()
        onNodeWithText("해결책 01. 체이닝").assertIsDisplayed()
        onNodeWithText("같은 인덱스를 연결 리스트로 잇는 방식입니다.").assertIsDisplayed()
        onNodeWithText("해결책 02. 개방 주소법").assertIsDisplayed()
        onNodeWithText("충돌 시 옆 빈 칸을 찾아 들어가는 방식입니다.").assertIsDisplayed()
        // 인용 카드 — 인용 표식으로 감싸 보인다.
        onNodeWithText("“좋은 해시 함수는 충돌을 최소화합니다.”").assertIsDisplayed()
        // 접기 버튼은 없다 — 토글 자체가 사라졌다.
        onNodeWithText("접기").assertDoesNotExist()
    }

    /** 항목이 0개여도 본 카드(제목+리드)는 자연스럽게 그려진다(부분 구조 허용). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 항목이_없어도_본_카드는_제목과_리드를_보여준다() = runComposeUiTest {
        val enrichment = Enrichment(title = "제목만 있는 경우", body = "항목 없이 리드만 있어요.")
        setContent {
            BcsTheme(darkTheme = false) {
                EnrichmentBlock(enrichment = enrichment)
            }
        }

        onNodeWithText("제목만 있는 경우").assertIsDisplayed()
        onNodeWithText("항목 없이 리드만 있어요.").assertIsDisplayed()
    }

    /** 인용이 없으면 인용 카드 자체를 그리지 않는다(빈 껍데기 금지는 인용에도 적용된다). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 인용이_없으면_인용_카드를_그리지_않는다() = runComposeUiTest {
        val enrichment = Enrichment(
            title = "인용 없는 경우",
            body = "본문만 있어요.",
            items = listOf(EnrichmentItem(title = "항목", description = "설명")),
        )
        setContent {
            BcsTheme(darkTheme = false) {
                EnrichmentBlock(enrichment = enrichment)
            }
        }

        onNodeWithText("인용 없는 경우").assertIsDisplayed()
        onNodeWithText("“", substring = true).assertDoesNotExist()
    }
}
