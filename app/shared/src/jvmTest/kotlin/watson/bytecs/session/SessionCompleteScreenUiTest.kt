package watson.bytecs.session

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import watson.bytecs.account.PreferredDifficulty
import watson.bytecs.ui.theme.BcsTheme

/**
 * 04 세션 완료 화면 — 확정 시안(`docs/design/04 세션 완료 화면 디자인.html`)·명세 대조.
 *
 * 렌더링·카피뿐 아니라 **도메인 가드레일**을 못박는다: 소요 시간 비표시(세션은 분량 기반),
 * 복습 시점 비단정(개념별 간격 반복), 스트릭 긍정 톤.
 *
 * ⭐️ 가입 유도는 이 화면에 없다(2026-07-16 오너 결정 — 홈 가입 유도로 일원화).
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

    /**
     * 제안 카드 테스트용 — 카드 끝까지 스크롤 없이 전부 보이는 긴 캔버스로 띄운다.
     * ⚠️ 이 화면 테스트에서 performScrollTo를 쓰면 안 된다: [settleCelebration]이 mainClock을 멈춘
     * 상태라 skiko 테스트의 scrollToNode가 스크롤 애니메이션 프레임을 영원히 기다리며 무한 재시도해
     * 실행기가 힙 고갈(OOM)로 죽는다(2026-07-22, 스레드 덤프로 확인 — Actions.kt scrollToNode 루프).
     */
    @OptIn(ExperimentalTestApi::class)
    private fun runTallComposeUiTest(block: suspend SkikoComposeUiTest.() -> Unit) =
        runSkikoComposeUiTest(size = Size(1024f, 2400f), block = block)

    /** 카드 콜백 호출 횟수를 세는 하네스(선택·거절이 정확히 의도한 만큼만 나가는지 증명). */
    private class PromptCallbacks {
        var selected: PreferredDifficulty? = null
        var dismissed = 0
        var dismissNoticeFinished = 0
    }

    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.showScreen(
        summary: CompletionSummary = this@SessionCompleteScreenUiTest.summary,
        promptState: DifficultyPromptUiState = DifficultyPromptUiState(visible = false),
        onDone: () -> Unit = {},
        onMore: () -> Unit = {},
        promptCallbacks: PromptCallbacks = PromptCallbacks(),
    ): PromptCallbacks {
        setContent {
            BcsTheme(darkTheme = false) {
                SessionCompleteScreenContent(
                    summary = summary,
                    promptState = promptState,
                    onDone = onDone,
                    onMore = onMore,
                    onSelectDifficulty = { promptCallbacks.selected = it },
                    onDismissPrompt = { promptCallbacks.dismissed++ },
                    onDismissNoticeFinished = { promptCallbacks.dismissNoticeFinished++ },
                )
            }
        }
        settleCelebration()
        return promptCallbacks
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

    /** 세션 기본 분량(5, D3 오너 결정)과 다른 총량이어도 실제 푼 수를 그대로 보여준다(기본값을 가정하지 않는다). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 요약은_세션_기본_분량이_아니어도_실제_푼_수를_보여준다() = runComposeUiTest {
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

        onNodeWithText("3일 연속 학습 중").assertIsDisplayed()
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

    /**
     * ⚠️ 가드레일(2026-07-16 오너 결정): 가입 유도가 이 화면에 다시 기어들어오면 실패한다.
     * 유일한 가입 접점은 홈의 GuestUpgradeBanner다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 완료_화면은_가입을_권유하지_않는다() = runComposeUiTest {
        showScreen()

        assertEquals(0, onAllNodesWithText("가입", substring = true).fetchSemanticsNodes().size)
    }

    // ── 선호 난이도 제안 카드 (난이도 조절 1차 · 구성 8 · DF1) ────────────────────

    /** 노출 신호(visible=false)가 없으면 카드는 아예 렌더되지 않는다 — 이미 선호를 정했거나 응답한 사용자. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 노출_신호가_없으면_난이도_제안_카드가_보이지_않는다() = runComposeUiTest {
        showScreen(promptState = DifficultyPromptUiState(visible = false))

        onNodeWithText("CS를 이제 막 시작해요").assertDoesNotExist()
        onNodeWithText("지금은 괜찮아요").assertDoesNotExist()
    }

    /** 서버 신호(visible=true)가 있으면 상태 서술형 3택 + 거절이 모두 보인다(무낙인 문구, DF4). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 노출_신호가_있으면_상태_서술형_3택과_거절이_보인다() = runTallComposeUiTest {
        showScreen(promptState = DifficultyPromptUiState(visible = true))

        onNodeWithText("새 문제, 어떤 난이도로 만나고 싶은지 골라볼까요?").assertIsDisplayed()
        onNodeWithText("CS를 이제 막 시작해요").assertIsDisplayed()
        onNodeWithText("기본기를 다지는 중이에요").assertIsDisplayed()
        onNodeWithText("도전적인 문제를 원해요").assertIsDisplayed()
        onNodeWithText("지금은 괜찮아요").assertIsDisplayed()
    }

    /** ⛔ 수준 평가 뉘앙스("실력에 맞는" 등) 금지 — 조절 주체가 사용자임을 드러내는 문구만 쓴다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 제안_카드는_수준_평가_뉘앙스를_쓰지_않는다() = runComposeUiTest {
        showScreen(promptState = DifficultyPromptUiState(visible = true))

        listOf("실력에 맞는", "당신의 수준", "레벨").forEach { forbidden ->
            assertEquals(
                0,
                onAllNodesWithText(forbidden, substring = true).fetchSemanticsNodes().size,
                "수준 평가 뉘앙스('$forbidden')가 있으면 안 된다",
            )
        }
    }

    /** 3택 중 하나를 고르면 그 값 그대로 선택 콜백이 나간다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 난이도_항목을_고르면_선택_콜백이_그_값으로_나간다() = runTallComposeUiTest {
        val callbacks = showScreen(promptState = DifficultyPromptUiState(visible = true))

        onNodeWithText("도전적인 문제를 원해요").performClick()

        assertEquals(PreferredDifficulty.HARD, callbacks.selected)
    }

    /** "지금은 괜찮아요"를 누르면 거절 콜백이 나간다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 지금은_괜찮아요를_누르면_거절_콜백이_나간다() = runTallComposeUiTest {
        val callbacks = showScreen(promptState = DifficultyPromptUiState(visible = true))

        onNodeWithText("지금은 괜찮아요").performClick()

        assertEquals(1, callbacks.dismissed)
    }

    /** 거절 저장 성공 직후엔 선택지 대신 은은한 안내만 보인다("닫기" 전 잠깐 보여주는 상태). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 거절_저장에_성공하면_선택지_대신_안내_문구를_보여준다() = runTallComposeUiTest {
        showScreen(promptState = DifficultyPromptUiState(visible = true, dismissedNotice = true))

        onNodeWithText("설정에서 언제든 바꿀 수 있어요").assertIsDisplayed()
        onNodeWithText("CS를 이제 막 시작해요").assertDoesNotExist()
        onNodeWithText("지금은 괜찮아요").assertDoesNotExist()
    }

    /** 저장 실패는 카드를 유지한 채(사라지지 않고) 비처벌 안내만 보여준다 — 재시도 가능해야 한다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 저장_실패는_카드를_유지한_채_안내를_보여준다() = runTallComposeUiTest {
        showScreen(
            promptState = DifficultyPromptUiState(
                visible = true,
                error = "저장하지 못했어요. 잠시 후 다시 시도해 주세요.",
            ),
        )

        onNodeWithText("저장하지 못했어요. 잠시 후 다시 시도해 주세요.").assertIsDisplayed()
        // 재시도할 수 있도록 선택지가 여전히 남아 있다.
        onNodeWithText("CS를 이제 막 시작해요").assertIsDisplayed()
    }

    /** ⭐️ 가벼운 초대 원칙 — 카드가 떠도 완결 축하 헤더·스트릭의 위계를 가리지 않는다(둘 다 그대로 보인다). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 제안_카드가_떠도_완결_축하_위계를_가리지_않는다() = runComposeUiTest {
        showScreen(promptState = DifficultyPromptUiState(visible = true))

        onNodeWithText("오늘 CS 한입 완료!").assertIsDisplayed()
        onNodeWithText("3일 연속 학습 중").assertIsDisplayed()
        onNodeWithText("오늘은 여기까지").assertIsDisplayed()
    }
}
