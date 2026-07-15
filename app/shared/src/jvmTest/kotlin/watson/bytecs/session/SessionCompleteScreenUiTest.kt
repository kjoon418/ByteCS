package watson.bytecs.session

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import watson.bytecs.ui.theme.BcsTheme

/**
 * 04 세션 완료 화면 — 확정 시안(`docs/design/04 세션 완료 화면 디자인.html`)·명세 대조.
 *
 * 렌더링·카피뿐 아니라 **도메인 가드레일**을 못박는다: 소요 시간 비표시(세션은 분량 기반),
 * 복습 시점 비단정(개념별 간격 반복), 스트릭 긍정 톤, 가입 비강제.
 */
class SessionCompleteScreenUiTest {

    private val summary = CompletionSummary(
        solvedCount = 10,
        totalCount = 10,
        streak = Streak(count = 3, lastStudyDate = "2026-07-15"),
    )

    /** 카운트업(0→N, ~0.7s)·컨페티가 끝나야 최종 수치가 뜬다. v2는 StandardTestDispatcher라 시간을 직접 민다. */
    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.settleCelebration() {
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(2_000)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.showScreen(
        summary: CompletionSummary = this@SessionCompleteScreenUiTest.summary,
        isGuest: Boolean = false,
        onDone: () -> Unit = {},
        onMore: () -> Unit = {},
        onUpgrade: () -> Unit = {},
    ) {
        setContent {
            BcsTheme(darkTheme = false) {
                SessionCompleteScreen(
                    summary = summary,
                    isGuest = isGuest,
                    onDone = onDone,
                    onMore = onMore,
                    onUpgrade = onUpgrade,
                )
            }
        }
        settleCelebration()
    }

    // ── 완결 축하 헤더 ────────────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 완료_화면은_완결_신호와_격려_문구를_보여준다() = runComposeUiTest {
        showScreen()

        onNodeWithText("오늘 CS 한입 완료!").assertIsDisplayed()
        onNodeWithText("오늘도 성실하게 지식을 채웠군요.\n작은 습관이 당신을 전문가로 만듭니다.").assertIsDisplayed()
    }

    // ── 오늘의 요약 ──────────────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 요약은_푼_문제_수를_카운트업해_보여준다() = runComposeUiTest {
        showScreen()

        onNodeWithText("푼 문제").assertIsDisplayed()
        onNodeWithText("10개").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 요약은_세션_크기_기본값_10이_아니어도_실제_푼_수를_보여준다() = runComposeUiTest {
        showScreen(summary = summary.copy(solvedCount = 7, totalCount = 7))

        onNodeWithText("7개").assertIsDisplayed()
    }

    /**
     * ⚠️ 가드레일: 세션은 시간이 아니라 **분량**으로 정의된다(도메인 기능 1.5, §9 카운트다운 금지).
     * 소요 시간·분·초 지표가 새어 들어오면 실패한다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 요약은_소요_시간을_표시하지_않는다() = runComposeUiTest {
        showScreen()

        listOf("소요", "소요 시간", "걸린 시간", "분", "초", "시간").forEach { forbidden ->
            assertEquals(
                0,
                onAllNodesWithText(forbidden, substring = true).fetchSemanticsNodes().size,
                "완료 화면에 시간 지표('$forbidden')가 있으면 안 된다 — 세션은 분량 기반이다",
            )
        }
    }

    // ── 정착 연결 안내 ────────────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 복습_안내는_시점을_단정하지_않는다() = runComposeUiTest {
        showScreen()

        onNodeWithText("배운 내용은 나중에 복습으로 다시 만나요.").assertIsDisplayed()
        // 복습은 개념별 간격 반복이라 세션 단위로 다음 만남을 약속할 수 없다(지킬 수 없는 약속 금지).
        listOf("3일 뒤", "내일", "일 뒤", "일 후", "며칠 뒤").forEach { forbidden ->
            assertEquals(
                0,
                onAllNodesWithText(forbidden, substring = true).fetchSemanticsNodes().size,
                "복습 시점을 단정하는 카피('$forbidden')가 있으면 안 된다",
            )
        }
    }

    /** ⚠️ 축하 화면에 학습 이론 용어·불안 프레이밍('망각 곡선 방지')은 톤이 어긋난다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 복습_안내에_전문_용어나_불안_프레이밍을_쓰지_않는다() = runComposeUiTest {
        showScreen()

        listOf("망각", "곡선", "방지", "잊", "까먹").forEach { forbidden ->
            assertEquals(
                0,
                onAllNodesWithText(forbidden, substring = true).fetchSemanticsNodes().size,
                "축하 화면에 불안·전문 용어('$forbidden')가 있으면 안 된다",
            )
        }
    }

    // ── 스트릭(긍정 동기) ─────────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스트릭이_있으면_연속_일수를_긍정_톤으로_보여준다() = runComposeUiTest {
        showScreen()

        onNodeWithText("🔥 3일 연속 학습 중").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스트릭이_없으면_스트릭을_숨긴다() = runComposeUiTest {
        showScreen(summary = summary.copy(streak = null))

        assertEquals(0, onAllNodesWithText("연속 학습", substring = true).fetchSemanticsNodes().size)
    }

    /** ⚠️ 가드레일: 끊김(0일)에도 상실 공포·죄책감 연출 금지 — 공용 StreakBadge의 중립·초대 톤 그대로. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스트릭이_끊겨도_상실_공포나_죄책감을_연출하지_않는다() = runComposeUiTest {
        showScreen(summary = summary.copy(streak = Streak(count = 0, lastStudyDate = null)))

        onNodeWithText("오늘 한입으로 연속 학습을 시작해요").assertIsDisplayed()
        listOf("끊", "잃", "사라졌", "놓쳤", "실패").forEach { forbidden ->
            assertEquals(
                0,
                onAllNodesWithText(forbidden, substring = true).fetchSemanticsNodes().size,
                "스트릭 끊김에 상실·죄책감 카피('$forbidden')가 있으면 안 된다",
            )
        }
    }

    // ── CTA ─────────────────────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 오늘은_여기까지를_누르면_홈으로_나간다() = runComposeUiTest {
        var done = 0
        showScreen(onDone = { done++ })

        onNodeWithText("오늘은 여기까지").assertIsDisplayed().performClick()

        assertEquals(1, done)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 조금_더_풀기를_누르면_추가_연습으로_간다() = runComposeUiTest {
        var more = 0
        showScreen(onMore = { more++ })

        onNodeWithText("조금 더 풀기").assertIsDisplayed().performClick()

        assertEquals(1, more)
    }

    /** ⚠️ '조금 더 풀기'는 도메인 용어다 — 명세·시안 모두 '공부하기'가 아니라 '풀기'로 지정. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 세컨더리_CTA는_조금_더_공부하기가_아니다() = runComposeUiTest {
        showScreen()

        assertEquals(0, onAllNodesWithText("조금 더 공부하기", substring = true).fetchSemanticsNodes().size)
    }

    // ── 게스트 승계(권유·강요 X) ──────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 게스트에게는_안심_프레이밍으로_가입을_권유한다() = runComposeUiTest {
        var upgrade = 0
        showScreen(isGuest = true, onUpgrade = { upgrade++ })

        onNodeWithText("가입하면 이 기록이 사라지지 않아요.").assertIsDisplayed().performClick()

        assertEquals(1, upgrade)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 회원에게는_가입_권유를_보여주지_않는다() = runComposeUiTest {
        showScreen(isGuest = false)

        assertEquals(0, onAllNodesWithText("가입", substring = true).fetchSemanticsNodes().size)
    }

    /**
     * ⚠️ 가드레일: 가입은 '권유'다. 게스트도 [오늘은 여기까지]로 그냥 나갈 수 있어야 하고,
     * 카피는 명령형("저장하세요")이 아니어야 한다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 게스트도_가입하지_않고_화면을_나갈_수_있다() = runComposeUiTest {
        var done = 0
        showScreen(isGuest = true, onDone = { done++ })

        onNodeWithText("오늘은 여기까지").performClick()

        assertEquals(1, done, "게스트에게 가입을 강제하면 안 된다")
        listOf("저장하세요", "가입하세요", "지금 가입", "필수").forEach { forbidden ->
            assertTrue(
                onAllNodesWithText(forbidden, substring = true).fetchSemanticsNodes().isEmpty(),
                "가입 권유에 명령·강요 카피('$forbidden')가 있으면 안 된다",
            )
        }
    }
}
