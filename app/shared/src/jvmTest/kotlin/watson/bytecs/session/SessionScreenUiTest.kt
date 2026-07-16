package watson.bytecs.session

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import watson.bytecs.problem.JudgeResult
import watson.bytecs.ui.theme.BcsTheme

/**
 * 03 문제 풀이(세션) 화면 계약. 서비스의 히어로 화면이라 **도메인 가드레일을 여기서 못박는다**.
 *
 * 뷰모델이 아니라 [SessionScreenContent]를 직접 그리는 이유: 여기서 지키려는 것은 "이 상태일 때 화면에
 * 무엇이 있고 무엇이 **없는가**"이고, 그 답은 상태→화면 매핑에만 달려 있다. 상태를 손으로 세워 두면
 * 정답 노출 같은 규칙을 정확히 그 상태에서 단언할 수 있다.
 */
class SessionScreenUiTest {

    private val problem = SessionProblem(
        id = 1L,
        question = "서로 다른 키가 같은 버킷으로 가는 현상을 무엇이라 하나요?",
        difficulty = "MEDIUM",
    )

    /** ⭐️ 이 화면의 정답. 어떤 비공개 상태에서도 화면에 새면 안 된다. */
    private val answer = "해시 충돌"

    private fun active(
        inputText: String = "",
        feedback: SessionFeedback? = null,
        reveal: Reveal? = null,
        past: PastView? = null,
        position: Int = 1,
        total: Int = 10,
    ) = SessionUiState.Active(
        problem = problem,
        position = position,
        total = total,
        solvedCount = position,
        inputText = inputText,
        feedback = feedback,
        reveal = reveal,
        past = past,
    )

    private fun revealOf() = Reveal(
        concepts = listOf(answer),
        explanation = "해시 함수는 서로 다른 입력을 같은 값으로 보낼 수 있어요.",
        acceptableAnswers = listOf(answer, "충돌"),
    )

    @OptIn(ExperimentalTestApi::class)
    private fun runScreen(
        state: SessionUiState,
        onInputChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onAdvance: () -> Unit = {},
        onReveal: () -> Unit = {},
        onOpenPast: (Int) -> Unit = {},
        onClosePast: () -> Unit = {},
        onReport: (Long) -> Unit = {},
        scrappedProblemIds: Set<Long> = emptySet(),
        onToggleScrap: (Long) -> Unit = {},
        body: suspend ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                SessionScreenContent(
                    state = state,
                    onInputChange = onInputChange,
                    onSubmit = onSubmit,
                    onAdvance = onAdvance,
                    onReveal = onReveal,
                    onOpenPast = onOpenPast,
                    onClosePast = onClosePast,
                    onRetry = {},
                    onExit = {},
                    onReport = onReport,
                    scrappedProblemIds = scrappedProblemIds,
                    onToggleScrap = onToggleScrap,
                )
            }
        }
        body()
    }

    // ── 가드레일: 정답 비노출 ──────────────────────────────────────────────

    /**
     * ⭐️⭐️ 가장 중요한 가드레일: **공개 전에는 정답이 화면 어디에도 없다.**
     * (시안 원본이 플레이스홀더에 정답을 노출했던 자리다 — 여기서 막는다.)
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 공개_전에는_정답이_화면_어디에도_없다() = runScreen(active()) {
        onNodeWithText(answer, substring = true).assertDoesNotExist()
    }

    /** 오답을 낸 뒤에도(넛지·정답 보기 진입점이 뜬 상태) 정답은 여전히 없다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 오답_피드백_상태에서도_정답이_새지_않는다() = runScreen(
        active(inputText = "해싱", feedback = SessionFeedback.Mismatch()),
    ) {
        onNodeWithText("아직이에요, 다시 해볼까요?").assertIsDisplayed()
        onNodeWithText(answer, substring = true).assertDoesNotExist()
    }

    /**
     * 근접 신호에도 정답·개념을 짚어 주지 않는다 — '오타 때문'이라는 사실만 알린다.
     * 입력값은 정답을 부분 문자열로 품지 않는 오탈자를 쓴다("해시충돌"). 사용자가 스스로 친 글자가 칸에
     * 그대로 보이는 것은 노출이 아니지만, 그걸 섞으면 이 단언이 무엇을 잡았는지 알 수 없어진다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 근접_신호에도_정답이_새지_않는다() = runScreen(
        active(inputText = "해시충돌", feedback = SessionFeedback.NearMiss),
    ) {
        onNodeWithText("거의 맞았어요, 오타를 확인해보세요").assertIsDisplayed()
        onNodeWithText(answer, substring = true).assertDoesNotExist()
    }

    /**
     * ⭐️ 개념 칩은 풀기 전 숨긴다 — 개념명이 곧 정답인 문제가 있다(정답 '해시 충돌' ↔ 칩 '해시 충돌').
     * 위 no-leak 테스트와 겹쳐 보이지만 노리는 실수가 다르다: 이쪽은 "개념 칩을 문제 옆에 늘 그리자"는 변경을 막는다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 개념_칩은_풀기_전에_노출되지_않는다() = runScreen(active()) {
        onNodeWithText(problem.question).assertIsDisplayed()
        onNodeWithText(answer).assertDoesNotExist()
    }

    // ── 가드레일: 무낙인·비처벌 ────────────────────────────────────────────

    /** ⭐️ 오답에 처벌 문구가 없다. 틀림은 '아직'이지 실패가 아니다(도메인 "가장 중요한 규칙"). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 오답_피드백에_처벌_문구가_없다() = runScreen(
        active(inputText = "해싱", feedback = SessionFeedback.Mismatch()),
    ) {
        onNodeWithText("오답", substring = true).assertDoesNotExist()
        onNodeWithText("틀렸", substring = true).assertDoesNotExist()
        onNodeWithText("실패", substring = true).assertDoesNotExist()
    }

    /**
     * ⭐️ 정답 공개는 포기 고백이 아니라 정당한 학습 경로다 — 자기비하 문구로 유도하지 않는다.
     * 진입점 문구는 중립 "정답 보기"로 고정한다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 정답_보기_진입점은_자기비하_문구를_쓰지_않는다() = runScreen(
        active(inputText = "해싱", feedback = SessionFeedback.Mismatch()),
    ) {
        onNodeWithText("정답 보기").assertIsDisplayed()
        onNodeWithText("모르겠", substring = true).assertDoesNotExist()
        onNodeWithText("포기", substring = true).assertDoesNotExist()
    }

    /** [정답 보기]는 최소 한 번 시도한 뒤에만 — 시도 전에 내밀면 스스로 떠올릴 기회를 뺏는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 정답_보기는_시도_전에는_제안하지_않는다() = runScreen(active(inputText = "해싱")) {
        onNodeWithText("정답 보기").assertDoesNotExist()
    }

    // ── 공개 후 흐름: 모범답안 → 따라 입력 ───────────────────────────────

    /**
     * ⭐️ 공개 후 흐름은 모범답안 → 따라 입력이다. 따라 입력은 이 서비스가 진행을 요구하는 유일한 지점이고,
     * 톤은 '벌'이 아니라 '손으로 써 보며 익히기'다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 공개_후에는_모범답안과_따라_입력_안내가_함께_나온다() = runScreen(active(reveal = revealOf())) {
        onNodeWithText("모범답안").assertIsDisplayed()
        onNodeWithText("해시 충돌  ·  충돌").assertIsDisplayed()
        // 따라 입력(공용 TypeAlongField)의 학습 톤 문구.
        onNodeWithText("정답을 따라 적어 볼까요?").assertIsDisplayed()
        onNodeWithText("위 정답을 따라 적어 보세요").assertIsDisplayed()
    }

    /**
     * ⭐️ 모범답안은 따라 입력 칸보다 **위**에 있어야 한다 — 안내가 "**위** 정답을 따라 적어 보세요"이고,
     * 따라 입력은 답을 보고 하는 행동이라 순서가 곧 의미다(칸이 먼저 오면 보고 적을 것이 없다).
     * 존재 단언만으로는 순서가 뒤집혀도 통과하므로 좌표로 못박는다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 모범답안은_따라_입력_칸보다_위에_있다() = runScreen(active(reveal = revealOf())) {
        val modelAnswerBottom = onNodeWithText("모범답안").getBoundsInRoot().bottom
        val typeAlongTop = onNodeWithText("정답을 따라 적어 볼까요?").getBoundsInRoot().top
        assertTrue(
            modelAnswerBottom < typeAlongTop,
            "모범답안($modelAnswerBottom)이 따라 입력 안내($typeAlongTop)보다 위에 있어야 한다",
        )
    }

    /**
     * 공개 후에는 개념을 밝혀도 학습을 해치지 않는다 — 이때만 칩이 나온다.
     *
     * §5.9는 "풀기 전 숨김 / 공개 이후 노출"의 **양방향** 규칙이라, 부정 방향
     * ([개념_칩은_풀기_전에_노출되지_않는다])만으로는 절반이다 — 칩을 아예 안 그려도 그쪽은 통과한다.
     * 여기서 긍정 방향을 맡는다.
     *
     * 개념 문자열을 exact match로 찾는다: [ConceptChip]은 `Text(concept)` 하나를 그리므로 "해시 충돌"에
     * 정확히 걸리고, 같은 화면 [ModelAnswerBlock]의 "해시 충돌  ·  충돌"과는 겹치지 않는다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 개념_칩은_공개_후에_노출된다() = runScreen(active(reveal = revealOf())) {
        onNodeWithText(answer).assertIsDisplayed()
        onNodeWithText(revealOf().explanation!!).assertIsDisplayed()
    }

    /**
     * 개념이 여러 개(N—M 태깅)면 칩도 개수만큼 모두 보인다 — 하나로 뭉개지거나 뒤 개념이 잘리지 않는다.
     * 대표 개념(첫 번째)과 두 번째 개념을 모두 exact match로 확인한다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 개념이_여러_개면_모두_칩으로_보인다() = runScreen(
        active(
            reveal = Reveal(
                concepts = listOf(answer, "해시 함수"),
                explanation = "해시 함수는 서로 다른 입력을 같은 값으로 보낼 수 있어요.",
                acceptableAnswers = listOf(answer, "충돌"),
            ),
        ),
    ) {
        onNodeWithText(answer).assertIsDisplayed()
        onNodeWithText("해시 함수").assertIsDisplayed()
    }

    /** 공개 상태에서 [정답 보기]를 또 내밀지 않는다 — 이미 열린 것을 여는 버튼은 없다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 공개_후에는_정답_보기_진입점이_사라진다() = runScreen(active(reveal = revealOf())) {
        onNodeWithText("정답 보기").assertDoesNotExist()
    }

    /** 따라 적다 어긋나도 처벌이 아니다 — 같은 비처벌 넛지를 쓴다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 따라_입력이_어긋나도_처벌하지_않는다() = runScreen(
        active(inputText = "해시충", feedback = SessionFeedback.Mismatch(), reveal = revealOf()),
    ) {
        onNodeWithText("아직이에요, 다시 해볼까요?").assertIsDisplayed()
        onNodeWithText("오답", substring = true).assertDoesNotExist()
    }

    // ── 도메인 용어 ──────────────────────────────────────────────────────

    /**
     * ⭐️ '세션'은 내부 용어다 — 사용자 대면 문자열 어디에도 나오면 안 된다. 사용자에게 이건 '오늘의 한입'이다.
     *
     * ⚠️ **보이는 텍스트만 검사하면 이 유형을 놓친다.** 실제로 샜던 자리가 나가기 버튼의
     * `contentDescription`이었고, 그건 눈으로 보는 사용자에게는 안 보이고 **스크린리더 사용자만 들었다**.
     * `onNodeWithText`는 `contentDescription`을 매칭하지 않으므로 두 경로를 모두 건다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 내부_용어_세션은_사용자에게_노출되지_않는다() = runScreen(active()) {
        onNodeWithText("세션", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("세션", substring = true).assertDoesNotExist()
        // 나가기 경로 자체는 살아 있어야 한다(용어만 바꾼 것이지 기능을 뺀 게 아니다).
        onNodeWithContentDescription("오늘의 한입에서 나가기, 언제든 이어서 할 수 있어요").assertIsDisplayed()
    }

    // ── 진행 인디케이터 ───────────────────────────────────────────────────

    /** ⭐️ 진행은 **분량 기반**이다(시간 카운트다운 아님). 세션 기본 크기는 10. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 진행_인디케이터는_분량_기반이다() = runScreen(active(position = 1, total = 10)) {
        onNodeWithContentDescription("총 10문제 중 2번째 문제").assertIsDisplayed()
    }

    /** 진행 인디케이터를 누르면 직전 칸의 지난 문제 다시 보기로 들어간다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 진행_인디케이터를_누르면_지난_문제로_들어간다() {
        val opened = mutableListOf<Int>()
        runScreen(active(position = 3), onOpenPast = { opened += it }) {
            onNodeWithContentDescription("총 10문제 중 4번째 문제").performClick()
        }
        assertEquals(listOf(2), opened)
    }

    /**
     * 첫 칸에는 되돌아볼 것이 없다 — 눌러도 아무 일 없고, 누를 수 있다는 신호(›)도 내걸지 않는다.
     *
     * 호출 여부를 sentinel 정수가 아니라 목록으로 받는 이유: 첫 칸에서 가드가 풀리면 `onOpenPast(0 - 1)`,
     * 즉 -1이 넘어온다. "안 불렸음"을 -1로 표현하면 바로 그 회귀를 못 잡는다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 첫_칸에서는_지난_문제_진입점이_열리지_않는다() {
        val opened = mutableListOf<Int>()
        runScreen(active(position = 0), onOpenPast = { opened += it }) {
            onNodeWithContentDescription("총 10문제 중 1번째 문제")
                .assertIsDisplayed()
                .performClick()
            onNodeWithText("›").assertDoesNotExist()
        }
        assertEquals(emptyList(), opened)
    }

    // ── 하단 CTA ─────────────────────────────────────────────────────────

    /** 화면의 Primary는 '정답 확인하기' 하나. 빈 입력으로는 제출되지 않는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 빈_입력으로는_제출되지_않는다() {
        var submitted = 0
        runScreen(active(inputText = ""), onSubmit = { submitted++ }) {
            onNodeWithText("정답 확인하기").performClick()
        }
        assertEquals(0, submitted)
    }

    /** 정답을 맞히면 CTA가 다음 칸으로 넘기는 액션으로 바뀐다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 정답_후에는_다음_문제로_넘어간다() {
        var advanced = 0
        runScreen(
            active(inputText = answer, feedback = SessionFeedback.Correct(listOf(answer), "해설")),
            onAdvance = { advanced++ },
        ) {
            onNodeWithText("맞았어요!").assertIsDisplayed()
            onNodeWithText("다음 문제").performClick()
        }
        assertEquals(1, advanced)
    }

    /**
     * ⭐️ 엔터는 하단 CTA와 **같은 곳으로** 간다. 정답 상태의 CTA는 '다음 문제'인데 엔터만 제출로 남으면,
     * 세션 제출은 위치 기반이라 낡은 입력이 **사용자가 본 적 없는 다음 문제**로 채점돼 통째로 소비된다.
     *
     * 입력값을 정답·개념과 다른 문자열로 두는 이유: 텍스트 필드를 `hasSetTextAction()`으로 집으므로
     * 값이 무엇이든 상관없지만, 정답 문자열을 쓰면 이 화면의 no-leak 단언들과 얽혀 읽기 어려워진다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 정답_후_엔터는_제출이_아니라_다음_문제로_간다() {
        var submitted = 0
        var advanced = 0
        runScreen(
            active(inputText = "내가 쓴 답", feedback = SessionFeedback.Correct(listOf("개념"), "해설")),
            onSubmit = { submitted++ },
            onAdvance = { advanced++ },
        ) {
            onNode(hasSetTextAction()).performImeAction()
        }
        assertEquals(0, submitted, "정답 후 엔터가 낡은 입력을 다시 제출하면 안 된다")
        assertEquals(1, advanced, "엔터는 CTA와 같이 다음 칸으로 넘긴다")
    }

    /** 아직 못 맞힌 상태에서는 엔터가 제출이다 — 위 라우팅이 제출 경로까지 죽이면 안 된다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 정답_전_엔터는_제출한다() {
        var submitted = 0
        var advanced = 0
        runScreen(
            active(inputText = "해싱"),
            onSubmit = { submitted++ },
            onAdvance = { advanced++ },
        ) {
            onNode(hasSetTextAction()).performImeAction()
        }
        assertEquals(1, submitted)
        assertEquals(0, advanced)
    }

    // ── 난이도 ───────────────────────────────────────────────────────────

    /** 난이도는 한글로 은은하게 — 압박 금지. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 난이도는_한글로_표시된다() = runScreen(active()) {
        onNodeWithText("보통").assertIsDisplayed()
        onNodeWithText("MEDIUM").assertDoesNotExist()
    }

    // ── 시스템 오류 vs 오답 ──────────────────────────────────────────────

    /** ⭐️ 전송 실패는 오답이 아니다(§5.12) — 안심 문구를 먼저 주고 재시도 경로를 연다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 전송_실패는_오답과_구분해_안내한다() = runScreen(
        active(inputText = "해싱").copy(systemError = true),
    ) {
        onNodeWithText("학습 기록은 안전해요.").assertIsDisplayed()
        onNodeWithText("잠시 연결이 원활하지 않았어요. 다시 시도해 주세요.").assertIsDisplayed()
    }

    // ── 지난 문제 다시 보기 ──────────────────────────────────────────────

    /** 지난 칸은 이미 통과했으므로 모범답안을 보여도 학습을 해치지 않는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 지난_문제는_내_답과_모범답안을_함께_보여준다() = runScreen(
        active(
            position = 2,
            past = PastView.Loaded(
                PastItem(
                    position = 0,
                    problemId = 9L,
                    question = "스택과 큐의 차이는?",
                    codeSnippet = null,
                    difficulty = "EASY",
                    submittedAnswer = "LIFO/FIFO",
                    result = JudgeResult.CORRECT,
                    revealed = false,
                    concepts = listOf("자료구조"),
                    explanation = "스택은 나중에 넣은 것이 먼저 나옵니다.",
                    acceptableAnswers = listOf("LIFO와 FIFO"),
                ),
            ),
        ),
    ) {
        onNodeWithText("스택과 큐의 차이는?").assertIsDisplayed()
        onNodeWithText("LIFO/FIFO").assertIsDisplayed()
        onNodeWithText("LIFO와 FIFO").assertIsDisplayed()
        // 이미 통과한 칸이므로 개념도 밝힌다 — 여기서도 칩이 사라지면 안 된다(§5.9 긍정 방향).
        onNodeWithText("자료구조").assertIsDisplayed()
        onNodeWithText("돌아가기").assertIsDisplayed()
    }

    /** 지난 문제를 보는 동안에는 현재 문제의 제출 CTA를 띄우지 않는다(읽기 전용). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 지난_문제를_보는_동안에는_제출_CTA가_없다() = runScreen(
        active(position = 2, past = PastView.Loading),
    ) {
        onNodeWithText("정답 확인하기").assertDoesNotExist()
    }

    // ── 힌트(pull) 진입점 ─────────────────────────────────────────────────

    /** 힌트가 있는 문제는 '힌트 보기' 진입점을 내건다(본문은 아직 없다 — 요청해야 열린다). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 힌트가_있으면_힌트_보기_진입점이_보인다() = runScreen(
        active().copy(problem = problem.copy(hintCount = 2)),
    ) {
        onNodeWithText("힌트 보기").assertIsDisplayed()
    }

    /** ⭐️ 힌트가 0개인 문제는 진입점 자체를 노출하지 않는다(눌러도 아무것도 없는 버튼 금지). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 힌트가_없는_문제는_힌트_진입점을_노출하지_않는다() = runScreen(active()) {
        onNodeWithText("힌트 보기").assertDoesNotExist()
    }

    /**
     * ⭐️ 재진입 복원 + no-leak: 서버가 공개한 힌트만 본문으로 보인다. 아직 안 연 힌트가 남았으면 '더 보기'가 뜨고,
     * 미공개 본문은 화면 어디에도 없다(자리표시자는 렌더되지 않는다).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 공개된_힌트만_보이고_남은_힌트는_더_보기로_남는다() = runScreen(
        active().copy(
            problem = problem.copy(hintCount = 2, revealedHints = listOf(SessionHint("이미 본 힌트"))),
            revealedHints = listOf(SessionHint("이미 본 힌트")),
        ),
    ) {
        onNodeWithText("이미 본 힌트").assertIsDisplayed()
        onNodeWithText("더 보기").assertIsDisplayed()
    }

    // ── 오답 교정 힌트(push) ─────────────────────────────────────────────

    /**
     * ⭐️ 큐레이션된 오답이면 교정 힌트 카드가 불일치 넛지와 함께 뜬다.
     * 무낙인: 교정 메시지가 있어도 처벌·오답 확정 문구는 없다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 오답_교정_힌트가_있으면_카드로_함께_뜬다() = runScreen(
        active(
            inputText = "프로세스",
            feedback = SessionFeedback.Mismatch("실행 흐름의 단위를 다시 떠올려 봐요"),
        ),
    ) {
        onNodeWithText("아직이에요, 다시 해볼까요?").assertIsDisplayed()
        onNodeWithText("실행 흐름의 단위를 다시 떠올려 봐요").assertIsDisplayed()
        onNodeWithText("오답", substring = true).assertDoesNotExist()
    }

    /**
     * 교정 힌트가 없는 오답은 일반 불일치로만 흐른다 — 교정 카드를 억지로 만들지 않는다(막다른 길 없음).
     * 위 [오답_교정_힌트가_있으면_카드로_함께_뜬다]와 짝을 이뤄, 카드가 misconceptionHint 유무에만 달렸음을 못박는다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 교정_힌트가_없으면_교정_카드가_없다() = runScreen(
        active(inputText = "아무 오답", feedback = SessionFeedback.Mismatch(null)),
    ) {
        onNodeWithText("아직이에요, 다시 해볼까요?").assertIsDisplayed()
        onNodeWithText("실행 흐름의 단위를 다시 떠올려 봐요").assertDoesNotExist()
    }

    // ── 콘텐츠 오류 신고(07) 진입점 ──────────────────────────────────────────

    /**
     * ⭐️ 콘텐츠 오류 신고는 풀이 중(공개 전)에도 열려 있어야 한다 — 문제가 이상하면 언제든 알릴 수 있어야 하고,
     * 신고 화면은 유형만 받으므로 정답을 유출하지 않는다. 누르면 그 문제 id로 신고 화면을 연다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 문제_풀이_중에도_오류_신고로_들어갈_수_있다() {
        val reported = mutableListOf<Long>()
        runScreen(active(), onReport = { reported += it }) {
            onNodeWithText("오류 신고").assertIsDisplayed().performClick()
        }
        assertEquals(listOf(1L), reported)
    }

    // ── 스크랩 토글: 정답 접근 맥락에만 노출 ──────────────────────────────────

    /**
     * ⭐️⭐️ 핵심 가드레일: **미해결 문제를 푸는 중에는 스크랩 토글이 없다.** 스크랩은 재열람으로 이어지고
     * 재열람은 모범답안을 공개하므로, 아직 못 푼 문제에 스크랩을 열어 두면 정답 유출 경로가 된다.
     * (게이트 `solved || reveal != null`의 '둘 다 아님' 절반 — 아래 두 노출 테스트가 나머지 절반을 맡는다.)
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 미해결_풀이_중에는_스크랩_토글이_노출되지_않는다() = runScreen(
        active(inputText = "해싱", feedback = SessionFeedback.Mismatch()),
    ) {
        onNodeWithContentDescription("스크랩").assertDoesNotExist()
        onNodeWithContentDescription("스크랩 해제").assertDoesNotExist()
    }

    /** 시도조차 안 한(피드백 없는) 상태에서도 당연히 토글이 없다 — 오답 넛지 유무와 무관하게 게이트가 닫혀 있다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 첫_시도_전에는_스크랩_토글이_노출되지_않는다() = runScreen(active()) {
        onNodeWithContentDescription("스크랩").assertDoesNotExist()
    }

    /** 정답을 맞히면(정답 접근 가능) 스크랩 토글이 나온다 — 게이트의 solved 절반. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 정답을_맞히면_스크랩_토글이_노출된다() = runScreen(
        active(inputText = answer, feedback = SessionFeedback.Correct(listOf(answer), "해설")),
    ) {
        onNodeWithContentDescription("스크랩").assertIsDisplayed()
    }

    /** 정답을 공개한 뒤에도(정답 접근 가능) 스크랩 토글이 나온다 — 게이트의 reveal 절반. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 정답_공개_후에는_스크랩_토글이_노출된다() = runScreen(active(reveal = revealOf())) {
        onNodeWithContentDescription("스크랩").assertIsDisplayed()
    }

    /** 지난 문제(이미 통과)에서도 스크랩 토글이 나온다 — 정답 접근 가능 맥락. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 지난_문제에서도_스크랩_토글이_노출된다() = runScreen(
        active(
            position = 2,
            past = PastView.Loaded(
                PastItem(
                    position = 0,
                    problemId = 9L,
                    question = "스택과 큐의 차이는?",
                    codeSnippet = null,
                    difficulty = "EASY",
                    submittedAnswer = "LIFO/FIFO",
                    result = JudgeResult.CORRECT,
                    revealed = false,
                    concepts = listOf("자료구조"),
                    explanation = null,
                    acceptableAnswers = listOf("LIFO와 FIFO"),
                ),
            ),
        ),
    ) {
        onNodeWithContentDescription("스크랩").assertIsDisplayed()
    }

    /** 토글을 누르면 지금 문제의 id로 스크랩 토글 콜백이 불린다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스크랩_토글을_누르면_지금_문제_id로_콜백된다() {
        val toggled = mutableListOf<Long>()
        runScreen(
            active(inputText = answer, feedback = SessionFeedback.Correct(listOf(answer), "해설")),
            onToggleScrap = { toggled += it },
        ) {
            onNodeWithContentDescription("스크랩").assertIsDisplayed().performClick()
        }
        assertEquals(listOf(1L), toggled)
    }

    /** 이미 스크랩된 문제는 토글이 '스크랩 해제' 상태로 보인다(재진입 시 상태 반영). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 이미_스크랩된_문제는_해제_상태로_보인다() = runScreen(
        active(inputText = answer, feedback = SessionFeedback.Correct(listOf(answer), "해설")),
        scrappedProblemIds = setOf(1L),
    ) {
        onNodeWithContentDescription("스크랩 해제").assertIsDisplayed()
    }
}
