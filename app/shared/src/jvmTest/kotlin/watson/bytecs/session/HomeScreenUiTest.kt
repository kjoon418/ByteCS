package watson.bytecs.session

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
import watson.bytecs.ui.theme.BcsTheme

/**
 * 02 홈('오늘의 한입') 화면. 확정 시안(`docs/design/02 홈 오늘의 한입 디자인.html`)과 도메인 가드레일을 함께 못박는다.
 * CTA 배치는 최신안(`docs/design/02 홈 오늘의 한입 디자인 최신안.html`)을 따른다(QA #8, 2026-07-17)
 * — '학습 이어서 하기' 버튼이 진행 막대 바로 아래, 오늘의 한입 카드 안에 있어야 한다.
 *
 * 색 매핑 규칙 자체는 `ComponentTonesTest`가 본다. 여기서는 **상태별로 무엇이 나오고 무엇이 안 나오는가**
 * — 특히 다크 패턴 금지선(죄책감·강제 가입·시스템 용어 노출)을 본다.
 */
class HomeScreenUiTest {

    private fun session(
        solved: Int = 2,
        total: Int = 10,
        status: SessionStatus = SessionStatus.IN_PROGRESS,
        streak: Streak? = null,
    ) = DailySession(
        sessionId = 1L,
        sessionDate = "2026-05-14",
        status = status,
        solvedCount = solved,
        totalCount = total,
        position = solved,
        currentProblem = SessionProblem(id = 7L, question = "해시 충돌을 해결하는 방법은?"),
        streak = streak,
    )

    private fun ready(
        solved: Int = 2,
        total: Int = 10,
        status: SessionStatus = SessionStatus.IN_PROGRESS,
        streak: Streak? = null,
        isMember: Boolean = false,
    ) = HomeUiState.Ready(
        session = session(solved = solved, total = total, status = status, streak = streak),
        isMember = isMember,
    )

    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.setHome(
        state: HomeUiState,
        darkTheme: Boolean = false,
        onStartOrContinue: () -> Unit = {},
        onExtraPractice: () -> Unit = {},
        onOpenAccount: () -> Unit = {},
        onUpgrade: () -> Unit = {},
        onOpenScrapList: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = setContent {
        BcsTheme(darkTheme = darkTheme) {
            HomeScreenContent(
                state = state,
                onStartOrContinue = onStartOrContinue,
                onExtraPractice = onExtraPractice,
                onOpenAccount = onOpenAccount,
                onUpgrade = onUpgrade,
                onOpenScrapList = onOpenScrapList,
                onRetry = onRetry,
            )
        }
    }

    // ── 진행 표시(분량 기반) ───────────────────────────────────────────────────

    /**
     * ⭐️ 진행은 **분량**이다(2 / 10, 이 화면 픽스처가 고른 임의 값). 세션 기본 분량은 5(D3 오너 결정,
     * 서버 UserSettings.DEFAULT_DAILY_SESSION_SIZE)이지만 홈은 실제 totalCount를 그대로 보여줄 뿐 기본값을
     * 가정하지 않는다. 카운트다운 타이머는 없다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 진행_중이면_분량_기반_진행과_이어서_하기_CTA를_보여준다() = runComposeUiTest {
        var continued = 0
        setHome(ready(solved = 2, total = 10), onStartOrContinue = { continued++ })

        onNodeWithText("오늘의 한입").assertIsDisplayed()
        onNodeWithText("이어서 풀기").assertIsDisplayed()
        onNodeWithText("2").assertIsDisplayed()
        onNodeWithText(" / 10").assertIsDisplayed()
        onNodeWithText("학습 이어서 하기").assertIsDisplayed().performClick()

        assertEquals(1, continued)
    }

    /**
     * ⭐️ CTA는 하단 고정 바가 아니라 오늘의 한입 카드 **안**, 진행 막대 바로 아래에 있어야 한다
     * (QA #8 — 최신안 `02 홈 오늘의 한입 디자인 최신안.html` 반영, 2026-07-17). 버튼을 누르면
     * 무엇이 시작되는지 맥락이 바로 붙도록 카드 밖으로 새 나가면 안 된다.
     * 존재 단언만으로는 카드 밖(예: 별도 하단 바)에 그려져도 통과하므로 좌표로 못박는다 —
     * 진행 막대 아래·다음 카드(스크랩 진입점) 위 구간에 있어야 카드 영역 안이라 할 수 있다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun CTA_버튼은_오늘의_한입_카드_영역_안에_있다() = runComposeUiTest {
        setHome(ready(solved = 2, total = 10))

        val progressBottom = onNodeWithContentDescription("오늘의 한입 진행 총 10문제 중 2문제 완료")
            .getBoundsInRoot().bottom
        val ctaBounds = onNodeWithText("학습 이어서 하기").getBoundsInRoot()
        val scrapTop = onNodeWithText("스크랩한 문제").getBoundsInRoot().top

        assertTrue(
            progressBottom <= ctaBounds.top,
            "CTA 버튼(${ctaBounds.top})은 진행 막대($progressBottom) 아래에 있어야 한다",
        )
        assertTrue(
            ctaBounds.bottom <= scrapTop,
            "CTA 버튼(${ctaBounds.bottom})은 스크랩 진입점($scrapTop)보다 위, 즉 오늘의 한입 카드 안에 있어야 한다",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 아직_시작_전이면_시작하기_CTA를_보여준다() = runComposeUiTest {
        var started = 0
        setHome(ready(solved = 0), onStartOrContinue = { started++ })

        onNodeWithText("오늘의 한입 시작하기").assertIsDisplayed().performClick()
        onNodeWithText("학습 이어서 하기").assertDoesNotExist()

        assertEquals(1, started)
    }

    /**
     * 완료는 **긍정 빈 상태**다(§5.10). 더 풀기는 권유일 뿐 압박이 아니다 — Primary가 아닌 Ghost.
     * ⭐️ 별도 완료 카드 없이 오늘의 한입 카드 자체가 완료 표식(체크 배지)으로 바뀐다(2026-07-16 오너 결정
     * — 홈 복잡도 감소). "오늘의 한입" 배지는 완료 시 사라지고 "✓ 완료"로 바뀐다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 오늘_몫을_마치면_긍정_빈_상태와_추가_연습_권유를_보여준다() = runComposeUiTest {
        var extra = 0
        setHome(
            ready(solved = 10, total = 10, status = SessionStatus.COMPLETED),
            onExtraPractice = { extra++ },
        )

        onNodeWithText("✓ 완료").assertIsDisplayed()
        onNodeWithText("오늘의 한입").assertDoesNotExist()
        onNodeWithText("오늘 몫은 다 했어요!").assertIsDisplayed()
        onNodeWithText("원한다면 조금 더 풀어볼 수도 있어요.").assertIsDisplayed()
        onNodeWithText("조금 더 풀어보기").assertIsDisplayed().performClick()

        assertEquals(1, extra)
    }

    // ── 게스트 / 가입자 (배타적) ───────────────────────────────────────────────

    /** 게스트: 중립 계정 아이콘 + 가입 **권유** 배너. 막지 않는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 게스트에게는_중립_계정_아이콘과_가입_권유_배너를_보여준다() = runComposeUiTest {
        var upgrades = 0
        setHome(ready(isMember = false), onUpgrade = { upgrades++ })

        onNodeWithContentDescription("계정 만들기·설정 열기").assertIsDisplayed()
        onNodeWithContentDescription("내 계정·설정 열기").assertDoesNotExist()
        onNodeWithText("기록을 안전하게 지키세요").assertIsDisplayed()
        onNodeWithText("가입하기").assertIsDisplayed().performClick()

        assertEquals(1, upgrades)

        // ⭐️ 가입은 권유다 — 게스트도 오늘의 한입을 그대로 시작할 수 있어야 한다(가입 강제 금지).
        onNodeWithText("학습 이어서 하기").assertIsDisplayed()
    }

    /** ⭐️ 배타성: 가입자에게 가입 권유가 남아 있으면 그건 소음이자 강요다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 가입자에게는_프로필_진입점만_두고_가입_배너를_그리지_않는다() = runComposeUiTest {
        setHome(ready(isMember = true))

        onNodeWithContentDescription("내 계정·설정 열기").assertIsDisplayed()
        onNodeWithContentDescription("계정 만들기·설정 열기").assertDoesNotExist()
        onNodeWithText("기록을 안전하게 지키세요").assertDoesNotExist()
        onNodeWithText("가입하기").assertDoesNotExist()
    }

    // ── 스트릭 (긍정 동기 전용) ────────────────────────────────────────────────

    /**
     * ⭐️ 스트릭은 §5.16 승격(2026-07-16 오너 결정)으로 알약 배지가 아니라 독립 카드로 보여준다.
     * 시안(02 html)의 "내일도 오시면 N일 연속이에요" 미래 문구는 손실 프레임 부담 우려로 뺐다 —
     * 오늘 성취를 말하는 한 줄만 남는다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스트릭이_이어지고_있으면_성취로_보여준다() = runComposeUiTest {
        setHome(ready(streak = Streak(count = 3, lastStudyDate = "2026-05-13")))

        onNodeWithText("3일째 꾸준히 한입!").assertIsDisplayed()
        onNodeWithText("🔥").assertIsDisplayed()
        onNodeWithText("내일도 오시면", substring = true).assertDoesNotExist()
    }

    /**
     * ⭐️ 스트릭이 끊겨도 상실 공포·죄책감 연출 금지(UX 4 다크 패턴).
     * 꺼진 불꽃도 그리지 않는다 — 끊김은 실패가 아니라 '다시 시작할 오늘'이다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스트릭이_끊겨도_죄책감_대신_다시_시작하자는_초대를_보여준다() = runComposeUiTest {
        setHome(ready(streak = Streak(count = 0, lastStudyDate = "2026-05-01")))

        onNodeWithText("오늘 한입으로 연속 학습을 시작해요").assertIsDisplayed()
        onNodeWithText("🔥", substring = true).assertDoesNotExist()
        for (guilt in listOf("놓쳤", "끊겼", "사라", "실패", "아쉽")) {
            onNodeWithText(guilt, substring = true).assertDoesNotExist()
        }
    }

    /**
     * 백엔드가 스트릭을 안 실어 주면(null) 배지를 숨긴다 — 0일(=끊김)로 단정하지 않는다.
     * '모름'과 '끊김'은 다른 상태이고, 모름을 끊김으로 그리면 없는 사실을 지어내는 셈이다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스트릭_정보가_없으면_배지를_그리지_않는다() = runComposeUiTest {
        setHome(ready(streak = null))

        onNodeWithText("오늘 한입으로 연속 학습을 시작해요").assertDoesNotExist()
        onNodeWithText("🔥", substring = true).assertDoesNotExist()
    }

    // ── 카피 규율 ─────────────────────────────────────────────────────────────

    /**
     * ⚠️ '세션'은 시스템 용어다. 화면 글자에도, 스크린리더가 읽는 문구에도 나오면 안 된다.
     * 사용자에게 이 단위의 이름은 '오늘의 한입' 하나뿐이다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 사용자_대면_카피에_시스템_용어인_세션을_쓰지_않는다() = runComposeUiTest {
        setHome(ready(streak = Streak(count = 3, lastStudyDate = "2026-05-13")))

        onNodeWithText("세션", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("오늘의 한입 진행 총 10문제 중 2문제 완료").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 인사말은_가벼운_초대_톤이다() = runComposeUiTest {
        setHome(ready())

        onNodeWithText("안녕하세요!\n오늘도 한입 해볼까요?").assertIsDisplayed()
        onNodeWithText("2026.05.14").assertIsDisplayed()
    }

    // ── 로드 실패 ─────────────────────────────────────────────────────────────

    /** §5.12: 시스템 오류는 막다른 길을 만들지 않는다. 자산 안전 고지가 먼저, 재시도 경로는 항상. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 로드에_실패하면_기록_안전을_고지하고_재시도_경로를_준다() = runComposeUiTest {
        var retries = 0
        setHome(HomeUiState.Error, onRetry = { retries++ })

        onNodeWithText("오늘의 한입을 불러오지 못했어요").assertIsDisplayed()
        onNodeWithText("학습 기록은 안전해요. 잠시 후 다시 시도해 주세요.").assertIsDisplayed()
        onNodeWithText("다시 시도하기").assertIsDisplayed().performClick()

        assertEquals(1, retries)
    }

    /** 실패 화면에서는 시작/이어서 CTA를 그리지 않는다(누를 대상이 없는 버튼은 막다른 길이다). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 로드에_실패하면_시작_CTA를_그리지_않는다() = runComposeUiTest {
        setHome(HomeUiState.Error)

        onNodeWithText("오늘의 한입 시작하기").assertDoesNotExist()
        onNodeWithText("학습 이어서 하기").assertDoesNotExist()
    }

    // ── 다크 모드 ─────────────────────────────────────────────────────────────

    /** §9: 라이트·다크 두 스킴 모두 1급이다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 다크_테마에서도_홈이_렌더된다() = runComposeUiTest {
        setHome(ready(streak = Streak(count = 3, lastStudyDate = "2026-05-13")), darkTheme = true)

        onNodeWithText("3일째 꾸준히 한입!").assertIsDisplayed()
        onNodeWithText("학습 이어서 하기").assertIsDisplayed()
    }

    // ── 스크랩 목록 진입점(리뷰 반영) ─────────────────────────────────────────

    /** 스크랩 진입점은 회원 여부와 무관하게 항상 노출되고, 눌리면 콜백을 호출한다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스크랩_목록_진입점을_누르면_콜백을_호출한다() = runComposeUiTest {
        var opened = 0
        setHome(ready(), onOpenScrapList = { opened++ })

        onNodeWithText("스크랩한 문제").assertIsDisplayed().performClick()

        assertEquals(1, opened)
    }

    /** ⭐️ 히어로는 오늘의 한입 CTA다 — 스크랩 진입점은 시작/이어서 CTA와 나란히 있어도 그 위계를 해치지 않는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스크랩_목록_진입점은_오늘의_한입_CTA와_함께_보여준다() = runComposeUiTest {
        setHome(ready(solved = 0))

        onNodeWithText("오늘의 한입 시작하기").assertIsDisplayed()
        onNodeWithText("스크랩한 문제").assertIsDisplayed()
    }
}
