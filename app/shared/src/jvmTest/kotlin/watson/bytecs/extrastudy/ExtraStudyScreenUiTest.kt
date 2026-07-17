package watson.bytecs.extrastudy

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import watson.bytecs.problem.Enrichment
import watson.bytecs.ui.theme.BcsTheme

/**
 * 추가 학습 화면 계약. 세션 풀이 화면과 시각 동등하되 세션 전용 요소(진행표시·지난 문제·완료 CTA)를 뺀다.
 * 도메인 가드레일(no-leak·무낙인·소진 카피·세션 용어 비노출)을 여기서 못박는다.
 *
 * 뷰모델이 아니라 [ExtraStudyScreenContent]를 직접 그리는 이유: "이 상태일 때 무엇이 있고 무엇이 **없는가**"를
 * 상태→화면 매핑만으로 결정적으로 단언하기 위해서다.
 */
class ExtraStudyScreenUiTest {

    private val problem = ExtraStudyProblem(
        id = 1L,
        question = "서로 다른 키가 같은 버킷으로 가는 현상을 무엇이라 하나요?",
        difficulty = "MEDIUM",
    )

    /** ⭐️ 이 화면의 정답. 어떤 비공개 상태에서도 화면에 새면 안 된다. */
    private val answer = "해시 충돌"
    private val representativeAnswer = "해시 충돌 (collision)"

    private fun active(
        inputText: String = "",
        feedback: ExtraStudyFeedback? = null,
        reveal: ExtraStudyReveal? = null,
    ) = ExtraStudyUiState.Active(
        problem = problem,
        inputText = inputText,
        feedback = feedback,
        reveal = reveal,
    )

    private fun revealOf() = ExtraStudyReveal(
        concepts = listOf(answer),
        explanation = "해시 함수는 서로 다른 입력을 같은 값으로 보낼 수 있어요.",
        representativeAnswer = representativeAnswer,
    )

    @OptIn(ExperimentalTestApi::class)
    private fun runScreen(
        state: ExtraStudyUiState,
        onInputChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onAdvance: () -> Unit = {},
        onReveal: () -> Unit = {},
        onRetry: () -> Unit = {},
        onExit: () -> Unit = {},
        onReport: (Long) -> Unit = {},
        scrappedProblemIds: Set<Long> = emptySet(),
        onToggleScrap: (Long) -> Unit = {},
        body: suspend ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                ExtraStudyScreenContent(
                    state = state,
                    onInputChange = onInputChange,
                    onSubmit = onSubmit,
                    onAdvance = onAdvance,
                    onReveal = onReveal,
                    onRetry = onRetry,
                    onExit = onExit,
                    onReport = onReport,
                    scrappedProblemIds = scrappedProblemIds,
                    onToggleScrap = onToggleScrap,
                )
            }
        }
        body()
    }

    // ── 가드레일: 정답 비노출 ──────────────────────────────────────────────

    /** ⭐️⭐️ 가장 중요한 가드레일: 공개 전에는 정답이 화면 어디에도 없다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 공개_전에는_정답이_화면_어디에도_없다() = runScreen(active()) {
        onNodeWithText(answer, substring = true).assertDoesNotExist()
    }

    /** 오답을 낸 뒤에도 정답은 여전히 없다. 무낙인: 처벌 문구·텍스트 카드도 없다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 오답_피드백_상태에서도_정답이_새지_않고_처벌_문구가_없다() = runScreen(
        active(inputText = "해싱", feedback = ExtraStudyFeedback.Mismatch()),
    ) {
        // 시각 신호는 문제 영역의 주황 플래시(colors.retryFlash) — 텍스트 카드(RetryNudge류)는 두지 않는다.
        onNodeWithContentDescription("정답과 달라요, 다시 시도해 보세요").assertExists()
        onNodeWithText("아직이에요, 다시 해볼까요?").assertDoesNotExist()
        onNodeWithText(answer, substring = true).assertDoesNotExist()
        // 무낙인: 빨강 어휘 금지(보이는 텍스트 + 스크린리더 낭독 양쪽).
        onNodeWithText("오답", substring = true).assertDoesNotExist()
        onNodeWithText("틀렸", substring = true).assertDoesNotExist()
        onNodeWithText("실패", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("오답", substring = true).assertDoesNotExist()
    }

    /** 개념 칩은 풀기 전 숨긴다 — 개념명이 곧 정답인 문제가 있다(정답 '해시 충돌' ↔ 칩 '해시 충돌'). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 개념_칩은_풀기_전에_노출되지_않는다() = runScreen(active()) {
        onNodeWithText(problem.question).assertIsDisplayed()
        onNodeWithText(answer).assertDoesNotExist()
    }

    // ── 정답 보기(시도 전 허용) ────────────────────────────────────────────

    /** [결정 2026-07-17] [정답 보기]는 시도 전에도 열 수 있다(무낙인·사용자 주도). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 정답_보기는_시도_전에도_제안된다() = runScreen(active()) {
        onNodeWithText("정답 보기").assertIsDisplayed()
    }

    // ── 공개 후 흐름: 정답 표시 필드 → 따라 입력 ───────────────────────────────

    /** 공개 후 흐름은 정답 표시 필드 → 따라 입력이다(정답 시 배치와 통일, 불변식 19). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 공개_후에는_정답_표시_필드와_따라_입력_안내가_함께_나온다() = runScreen(active(reveal = revealOf())) {
        onNodeWithText("이 정답을 따라 적어 보세요").assertIsDisplayed()
        onNodeWithText(representativeAnswer).assertIsDisplayed()
        onNodeWithText("정답을 따라 적어 볼까요?").assertIsDisplayed()
    }

    /** 정답 표시 필드는 따라 입력 칸보다 위에 있어야 한다(보고 적을 것이 먼저 와야 한다 — 불변식 19 배치). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 모범답안은_따라_입력_칸보다_위에_있다() = runScreen(active(reveal = revealOf())) {
        val modelAnswerBottom = onNodeWithText("이 정답을 따라 적어 보세요").getBoundsInRoot().bottom
        val typeAlongTop = onNodeWithText("정답을 따라 적어 볼까요?").getBoundsInRoot().top
        assertTrue(
            modelAnswerBottom < typeAlongTop,
            "정답 표시 필드($modelAnswerBottom)가 따라 입력 안내($typeAlongTop)보다 위에 있어야 한다",
        )
    }

    /** 공개 후에는 개념을 밝혀도 학습을 해치지 않는다 — 이때만 칩이 나온다(§5.9 긍정 방향). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 개념_칩은_공개_후에_노출된다() = runScreen(active(reveal = revealOf())) {
        onNodeWithText(answer).assertIsDisplayed()
        onNodeWithText(revealOf().explanation!!).assertIsDisplayed()
    }

    /** 공개 상태에서 [정답 보기]를 또 내밀지 않는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 공개_후에는_정답_보기_진입점이_사라진다() = runScreen(active(reveal = revealOf())) {
        onNodeWithText("정답 보기").assertDoesNotExist()
    }

    // ── 도메인 용어: '세션' 비노출 ──────────────────────────────────────────

    /** ⭐️ '세션'은 내부 용어다 — 보이는 텍스트·스크린리더 어디에도 나오면 안 된다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 내부_용어_세션은_사용자에게_노출되지_않는다() = runScreen(active()) {
        onNodeWithText("세션", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("세션", substring = true).assertDoesNotExist()
        // 나가기 경로 자체는 살아 있어야 한다.
        onNodeWithContentDescription("나가기, 언제든 다시 이어서 할 수 있어요").assertIsDisplayed()
    }

    /** ⭐️ 진행 인디케이터를 두지 않는다 — 추가 학습은 목표 분량이 없다(분량 없는 진행도 금지). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 진행_인디케이터를_두지_않는다() = runScreen(active()) {
        // 세션 진행 표시("총 N문제 중 M번째 문제")의 고유 문구가 추가 학습에는 없어야 한다.
        onNodeWithContentDescription("번째 문제", substring = true).assertDoesNotExist()
        onNodeWithText("번째", substring = true).assertDoesNotExist()
    }

    // ── 하단 CTA ─────────────────────────────────────────────────────────

    /** 미해결 → CTA는 '제출하기'. 빈 입력으로는 제출되지 않는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 빈_입력으로는_제출되지_않는다() {
        var submitted = 0
        runScreen(active(inputText = ""), onSubmit = { submitted++ }) {
            onNodeWithText("제출하기").performClick()
        }
        assertEquals(0, submitted)
    }

    /** 정답을 맞히면 CTA가 '다음 문제'로 바뀐다(세션의 '한입 마치기' 분기는 없다). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 정답_후에는_다음_문제로_넘어간다() {
        var advanced = 0
        runScreen(
            active(inputText = answer, feedback = ExtraStudyFeedback.Correct(listOf(answer), "해설")),
            onAdvance = { advanced++ },
        ) {
            onNodeWithText("완벽해요! 정확한 정답입니다.").assertIsDisplayed()
            onNodeWithText("한입 마치기").assertDoesNotExist()
            onNodeWithText("다음 문제").performClick()
        }
        assertEquals(1, advanced)
    }

    /** 정답을 맞히면 확정 입력란이 제출 텍스트가 아니라 대표 정답을 보여준다([2026-07-16] 오너 결정). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 확정_입력란은_대표_정답을_보여준다() = runScreen(
        active(
            inputText = "trd",
            feedback = ExtraStudyFeedback.Correct(listOf(answer), "해설", representativeAnswer = representativeAnswer),
        ),
    ) {
        onNodeWithText(representativeAnswer).assertIsDisplayed()
        onNodeWithText("trd").assertDoesNotExist()
    }

    // ── 난이도 ───────────────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 난이도는_한글로_표시된다() = runScreen(active()) {
        onNodeWithText("보통").assertIsDisplayed()
        onNodeWithText("MEDIUM").assertDoesNotExist()
    }

    // ── 시스템 오류 vs 오답 ──────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 전송_실패는_오답과_구분해_안내한다() = runScreen(
        active(inputText = "해싱").copy(systemError = true),
    ) {
        onNodeWithText("학습 기록은 안전해요.").assertIsDisplayed()
        onNodeWithText("잠시 연결이 원활하지 않았어요. 다시 시도해 주세요.").assertIsDisplayed()
    }

    // ── 힌트(pull) 진입점 ─────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 힌트가_있으면_힌트_보기_진입점이_보인다() = runScreen(
        active().copy(problem = problem.copy(hintCount = 2)),
    ) {
        onNodeWithText("힌트 보기").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 힌트가_없는_문제는_힌트_진입점을_노출하지_않는다() = runScreen(active()) {
        onNodeWithText("힌트 보기").assertDoesNotExist()
    }

    /** 재진입 복원 + no-leak: 공개된 힌트만 본문으로 보이고 남은 힌트는 '더 보기'로 남는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 공개된_힌트만_보이고_남은_힌트는_더_보기로_남는다() = runScreen(
        active().copy(
            problem = problem.copy(hintCount = 2, revealedHints = listOf(ExtraStudyHint("이미 본 힌트"))),
            revealedHints = listOf(ExtraStudyHint("이미 본 힌트")),
        ),
    ) {
        onNodeWithText("이미 본 힌트").assertIsDisplayed()
        onNodeWithText("더 보기").assertIsDisplayed()
    }

    // ── 오답 교정 힌트(push) ─────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 오답_교정_힌트가_있으면_카드로_함께_뜬다() = runScreen(
        active(
            inputText = "프로세스",
            feedback = ExtraStudyFeedback.Mismatch("실행 흐름의 단위를 다시 떠올려 봐요"),
        ),
    ) {
        onNodeWithContentDescription("정답과 달라요, 다시 시도해 보세요").assertExists()
        onNodeWithText("실행 흐름의 단위를 다시 떠올려 봐요").assertIsDisplayed()
        onNodeWithText("오답", substring = true).assertDoesNotExist()
    }

    // ── 콘텐츠 오류 신고(07) 진입점 ──────────────────────────────────────────

    /** ⭐️ 콘텐츠 오류 신고는 풀이 중(공개 전)에도 열려 있다 — 누르면 그 문제 id로 신고 화면을 연다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 문제_풀이_중에도_오류_신고로_들어갈_수_있다() {
        val reported = mutableListOf<Long>()
        runScreen(active(), onReport = { reported += it }) {
            onNodeWithText("오류 신고").assertIsDisplayed().performClick()
        }
        assertEquals(listOf(1L), reported)
    }

    // ── 스크랩 토글: 풀이 중에도 노출 ──────────────────────────────────────

    /** ⭐️ 스크랩은 개인 북마크일 뿐 모범답안을 노출하지 않으므로 미해결 풀이 중에도 열려 있다(QA-04). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 미해결_풀이_중에도_스크랩_토글이_노출된다() = runScreen(
        active(inputText = "해싱", feedback = ExtraStudyFeedback.Mismatch()),
    ) {
        onNodeWithContentDescription("스크랩").assertIsDisplayed()
        onNodeWithText(answer, substring = true).assertDoesNotExist()
    }

    /** 토글을 누르면 지금 문제의 id로 스크랩 토글 콜백이 불린다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스크랩_토글을_누르면_지금_문제_id로_콜백된다() {
        val toggled = mutableListOf<Long>()
        runScreen(active(), onToggleScrap = { toggled += it }) {
            onNodeWithContentDescription("스크랩").assertIsDisplayed().performClick()
        }
        assertEquals(listOf(1L), toggled)
    }

    // ── '더 알아보기'(심화 정보, §5.7) ────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 정답_피드백에_심화_정보가_있으면_더_알아보기가_바로_보인다() = runScreen(
        active(
            inputText = answer,
            feedback = ExtraStudyFeedback.Correct(
                concepts = listOf(answer),
                explanation = "해설",
                enrichment = Enrichment(title = "생일 문제와의 연결", body = "해시 충돌은 생일 문제와 연결돼요."),
            ),
        ),
    ) {
        onNodeWithText("완벽해요! 정확한 정답입니다.").assertIsDisplayed()
        onNodeWithText("더 알아보기").assertIsDisplayed()
        onNodeWithText("생일 문제와의 연결").assertIsDisplayed()
    }

    // ── 소진 상태 ─────────────────────────────────────────────────────────

    /** ⭐️ 소진은 무낙인·긍정 톤 빈 상태다 — '없음/끝/실패' 낙인·상실 프레임 금지. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 소진_상태는_무낙인_긍정_카피와_홈_복귀_버튼을_보여준다() = runScreen(ExtraStudyUiState.Exhausted) {
        onNodeWithText("오늘 풀 문제를 다 만났어요").assertIsDisplayed()
        onNodeWithText("지금은 더 풀 문제가 없어요. 복습 주기가 돌아오면 다시 만나요.").assertIsDisplayed()
        onNodeWithText("홈으로").assertIsDisplayed()
        // 낙인·상실 프레임 금지.
        onNodeWithText("실패", substring = true).assertDoesNotExist()
        onNodeWithText("끝", substring = true).assertDoesNotExist()
    }

    /** 소진 상태에서는 제출·다음 문제 CTA가 없다(풀 문제가 없다). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 소진_상태에서는_제출_CTA가_없다() = runScreen(ExtraStudyUiState.Exhausted) {
        onNodeWithText("제출하기").assertDoesNotExist()
        onNodeWithText("다음 문제").assertDoesNotExist()
    }

    /** 소진에서 '홈으로'를 누르면 나가기 콜백이 불린다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 소진에서_홈으로_누르면_나가기_콜백이_불린다() {
        var exited = 0
        runScreen(ExtraStudyUiState.Exhausted, onExit = { exited++ }) {
            onNodeWithText("홈으로").performClick()
        }
        assertEquals(1, exited)
    }
}
