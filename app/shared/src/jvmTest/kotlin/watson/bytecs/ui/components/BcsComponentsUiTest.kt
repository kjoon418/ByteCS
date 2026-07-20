package watson.bytecs.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import watson.bytecs.ui.theme.BcsTheme

/**
 * DESIGN_SYSTEM.md §5 공용 컴포넌트 — 신설·추출분.
 * 색 규칙은 [ComponentTonesTest]가, 여기서는 렌더링·상호작용·카피를 본다.
 */
class BcsComponentsUiTest {

    // ── §5.1 SecondaryButton ─────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 세컨더리_버튼은_텍스트를_보여주고_클릭을_전달한다() = runComposeUiTest {
        var clicks = 0
        setContent {
            BcsTheme(darkTheme = false) {
                SecondaryButton(text = "조금 더 풀기", onClick = { clicks++ })
            }
        }

        onNodeWithText("조금 더 풀기").assertIsDisplayed().performClick()

        assertEquals(1, clicks)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 세컨더리_버튼은_비활성일_때_클릭을_전달하지_않는다() = runComposeUiTest {
        var clicks = 0
        setContent {
            BcsTheme(darkTheme = false) {
                SecondaryButton(text = "조금 더 풀기", onClick = { clicks++ }, enabled = false)
            }
        }

        onNodeWithText("조금 더 풀기").performClick()

        assertEquals(0, clicks)
    }

    // ── §5.16 StreakBadge ────────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스트릭_배지는_연속_일수를_보여준다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { StreakBadge(days = 5) }
        }

        onNodeWithText("5일 연속 학습 중").assertIsDisplayed()
        onNodeWithTag("streak-fire", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * ⭐️ 끊김 카피 계약: "다시 시작해요" 초대다.
     * 상실·죄책감 어휘("놓쳤", "끊겼", "사라") 금지 — 다크 패턴이다(UX 4).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스트릭이_끊기면_상실이_아니라_다시_시작하자는_초대를_보여준다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { StreakBadge(days = 0) }
        }

        onNodeWithText("오늘 한입으로 연속 학습을 시작해요").assertIsDisplayed()
        for (guilt in listOf("놓쳤", "끊겼", "사라", "실패")) {
            onNodeWithText(guilt, substring = true).assertDoesNotExist()
        }
    }

    /** ⭐️ 꺼진 불꽃 연출 금지 — 끊김에는 불꽃 자체를 그리지 않는다(§5.16). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스트릭이_끊기면_불꽃을_그리지_않는다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { StreakBadge(days = 0) }
        }

        onNodeWithTag("streak-fire", useUnmergedTree = true).assertDoesNotExist()
    }

    // ── §5.16 ScrapToggle ────────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스크랩_토글은_상태를_뒤집어_전달한다() = runComposeUiTest {
        var scrapped: Boolean? = null
        setContent {
            BcsTheme(darkTheme = false) {
                ScrapToggle(scrapped = false, onToggle = { scrapped = it })
            }
        }

        onNodeWithContentDescription("스크랩").assertIsDisplayed().performClick()

        assertEquals(true, scrapped)
    }

    /** 켜진 상태에서는 해제 액션으로 읽힌다(스크린리더가 지금 상태를 알 수 있어야 한다). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스크랩된_문제는_해제_액션으로_읽힌다() = runComposeUiTest {
        var scrapped: Boolean? = null
        setContent {
            BcsTheme(darkTheme = false) {
                ScrapToggle(scrapped = true, onToggle = { scrapped = it })
            }
        }

        onNodeWithContentDescription("스크랩 해제").assertIsDisplayed().performClick()

        assertEquals(false, scrapped)
    }

    // ── §5.9 DifficultyIndicator ─────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 난이도_표시는_주어진_라벨을_보여준다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { DifficultyIndicator(label = "쉬움") }
        }

        onNodeWithText("쉬움").assertIsDisplayed()
    }

    // ── §5.12 Snackbar / ErrorBanner ─────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스낵바는_메시지와_액션을_보여준다() = runComposeUiTest {
        var actions = 0
        setContent {
            BcsTheme(darkTheme = false) {
                BcsSnackbar(message = "저장했어요", actionLabel = "실행 취소", onAction = { actions++ })
            }
        }

        onNodeWithText("저장했어요").assertIsDisplayed()
        onNodeWithText("실행 취소").assertIsDisplayed().performClick()

        assertEquals(1, actions)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스낵바는_액션이_없으면_액션을_그리지_않는다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { BcsSnackbar(message = "저장했어요") }
        }

        onNodeWithText("저장했어요").assertIsDisplayed()
        onNodeWithText("실행 취소").assertDoesNotExist()
    }

    /**
     * ⭐️ §5.12: 시스템 오류는 막다른 길을 만들지 않는다.
     * 학습 기록 안전을 **먼저** 고지하고 재시도 경로를 항상 함께 준다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 에러_배너는_안심_문구와_재시도_경로를_함께_준다() = runComposeUiTest {
        var retries = 0
        setContent {
            BcsTheme(darkTheme = false) {
                ErrorBanner(message = "잠시 연결이 원활하지 않았어요.", onRetry = { retries++ })
            }
        }

        onNodeWithText("학습 기록은 안전해요.").assertIsDisplayed()
        onNodeWithText("잠시 연결이 원활하지 않았어요.").assertIsDisplayed()
        onNodeWithText("다시 시도하기").assertIsDisplayed().performClick()

        assertEquals(1, retries)
    }

    // ── §5.5 MisconceptionHintCard ───────────────────────────────────────────

    /** 흔한 오답 교정 힌트는 자동으로 뜨되, 낙인이 아니라 안내다(§5.5). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 오답_교정_힌트는_본문을_보여준다() = runComposeUiTest {
        val body = "'프로세스'는 실행 중인 프로그램이라 이 문제의 답과는 달라요. 다시 도전!"
        setContent {
            BcsTheme(darkTheme = false) { MisconceptionHintCard(text = body) }
        }

        onNodeWithText(body).assertIsDisplayed()
    }

    // ── §5.9 ConceptChip ─────────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 개념_칩은_개념을_보여준다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { ConceptChip(concept = "해시 충돌") }
        }

        onNodeWithText("해시 충돌").assertIsDisplayed()
    }

    // ── 다크 모드 ─────────────────────────────────────────────────────────────

    /** §9: 라이트·다크 두 스킴 모두 1급이다. 다크에서 컴포지션이 깨지지 않는지 본다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 다크_테마에서도_컴포넌트가_렌더된다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = true) {
                StreakBadge(days = 3)
            }
        }

        onNodeWithText("3일 연속 학습 중").assertIsDisplayed()
    }
}
