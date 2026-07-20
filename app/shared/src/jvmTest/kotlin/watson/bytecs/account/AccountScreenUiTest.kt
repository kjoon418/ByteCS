package watson.bytecs.account

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import watson.bytecs.ui.theme.BcsTheme
import watson.bytecs.ui.theme.ThemeMode

/**
 * 06 계정·설정 화면의 **도메인 가드레일**을 고정하는 테스트.
 *
 * 여기서 검증하는 건 픽셀이 아니라 되돌릴 수 없는 결정을 다루는 규칙이다:
 *  - 게스트/가입자 상태는 배타적이며, 게스트에게 계정 액션(로그아웃·삭제)이 새지 않는다.
 *  - 삭제는 명시적 확인 없이는 절대 실행되지 않는다.
 *  - MVP 밖 기능(알림 등)이 미리 들어오지 않는다.
 */
class AccountScreenUiTest {

    /** 게스트 기본 상태. 개별 테스트가 필요한 필드만 copy로 덮어쓴다. */
    private val guestState = AccountUiState(
        isLoading = false,
        isMember = false,
        email = null,
        profileError = false,
        sessionSize = 5,
        sessionSizeError = null,
        isSettingsDirty = false,
        isSettingsSaving = false,
        sessionSizeAppliesNextSession = false,
        themeMode = ThemeMode.SYSTEM,
        deletePhase = DeletePhase.None,
        isLoggingOut = false,
        noticeError = null,
        deleteError = null,
    )

    private val memberState = guestState.copy(isMember = true, email = "study@bytecs.app")

    /**
     * 화면을 띄우고 각 콜백 호출 횟수를 세는 하네스.
     * 삭제·로그아웃처럼 되돌릴 수 없는 액션이 "호출되지 않았음"을 증명하는 게 이 테스트의 핵심이라 카운터로 둔다.
     */
    private class Callbacks {
        var login = 0
        var logout = 0
        var requestDelete = 0
        var cancelDelete = 0
        var confirmDelete = 0
        var openScrapList = 0
    }

    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.showScreen(
        state: AccountUiState,
        callbacks: Callbacks = Callbacks(),
    ): Callbacks {
        setContent {
            BcsTheme(darkTheme = false) {
                AccountScreenContent(
                    state = state,
                    appVersion = "0.1.0",
                    onBack = {},
                    onNavigateToLogin = { callbacks.login++ },
                    onOpenScrapList = { callbacks.openScrapList++ },
                    onSessionSizeChange = {},
                    onSaveSettings = {},
                    onThemeSelect = {},
                    onLogout = { callbacks.logout++ },
                    onRequestDelete = { callbacks.requestDelete++ },
                    onCancelDelete = { callbacks.cancelDelete++ },
                    onConfirmDelete = { callbacks.confirmDelete++ },
                )
            }
        }
        return callbacks
    }

    // ── 게스트/가입자 배타성 (기능 4 · 가입 승계 모델) ──────────────────────────

    /** ⭐️ 게스트에겐 계정이 없다 → 로그아웃할 대상도 없다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 게스트에게는_로그아웃이_보이지_않는다() = runComposeUiTest {
        showScreen(guestState)

        onNodeWithText("로그아웃").assertDoesNotExist()
    }

    // ── 세션 크기 변경 안내(실기기 QA — 다음 세션부터 적용) ──────────────────────

    /** ⭐️ [실기기 QA] 저장 직후에는 '다음 세션부터 적용' 안내가 보인다(진행 중 세션은 그대로). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 세션_크기를_저장하면_다음_세션부터_적용_안내가_보인다() = runComposeUiTest {
        showScreen(memberState.copy(sessionSizeAppliesNextSession = true))

        onNodeWithText("다음 세션부터 적용", substring = true).assertIsDisplayed()
    }

    /** 저장 전(평상시)에는 안내가 뜨지 않는다 — 저장 시에만 안내한다(오너 결정). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 저장_전에는_다음_세션_적용_안내가_뜨지_않는다() = runComposeUiTest {
        showScreen(memberState)

        onNodeWithText("다음 세션부터 적용", substring = true).assertDoesNotExist()
    }

    // ── 스크랩 목록 진입점(시안 외 최소 진입점 · 기획 리뷰 대상) ────────────────

    /**
     * 계정 화면에서 스크랩 목록으로 들어갈 수 있다. 게스트·회원 모두 스크랩을 쓰므로 상태와 무관하게 노출되며,
     * 누르기 전까지는 아무 데도 이동하지 않는다(누른 그 순간에만 콜백).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 스크랩_목록으로_들어갈_수_있다() = runComposeUiTest {
        val callbacks = showScreen(guestState)

        onNodeWithText("스크랩한 문제").assertIsDisplayed()
        assertEquals(0, callbacks.openScrapList, "누르기 전에는 이동하지 않는다")

        onNodeWithText("스크랩한 문제").performClick()
        assertEquals(1, callbacks.openScrapList)
    }

    /** ⭐️ 게스트에겐 삭제할 계정이 없다. danger 진입점 자체가 없어야 한다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 게스트에게는_계정_삭제가_보이지_않는다() = runComposeUiTest {
        showScreen(guestState)

        onNodeWithText("계정 삭제").assertDoesNotExist()
    }

    /** ⭐️ "게스트 계정"이 아니라 "게스트로 이용 중" — 게스트에게 '계정'이 없다는 게 승계 모델의 전제. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 게스트는_계정이_아니라_이용_중_상태로_표기된다() = runComposeUiTest {
        showScreen(guestState)

        onNodeWithText("게스트로 이용 중").assertIsDisplayed()
        onNodeWithText("게스트 계정").assertDoesNotExist()
    }

    /** ⛔ 가입 강제 금지 — 게스트는 CTA를 보되, 누르기 전까지 아무 데도 끌려가지 않는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 게스트는_가입을_강요받지_않고_CTA로만_유도된다() = runComposeUiTest {
        val callbacks = showScreen(guestState)

        assertEquals(0, callbacks.login)
        onNodeWithText("가입하고 기록 지키기").assertIsDisplayed().performClick()
        assertEquals(1, callbacks.login)
    }

    /** 가입자에겐 로그아웃 + 안심 문구가 보인다(막다른 길 아님). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 가입자에게는_로그아웃과_안심_문구가_보인다() = runComposeUiTest {
        showScreen(memberState)

        onNodeWithText("로그아웃").assertIsDisplayed()
        onNodeWithText("다시 로그인하면 기록이 그대로예요.").assertIsDisplayed()
        onNodeWithText("가입하고 기록 지키기").assertDoesNotExist()
    }

    // ── 삭제: 확인 없이는 실행 금지 ─────────────────────────────────────────────

    /** ⭐️⭐️ 핵심 가드레일 — [계정 삭제]는 확인 단계를 열 뿐, 절대 삭제를 실행하지 않는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 계정_삭제를_눌러도_확인_없이는_삭제가_실행되지_않는다() = runComposeUiTest {
        val callbacks = showScreen(memberState)

        onNodeWithText("계정 삭제").assertIsDisplayed().performClick()

        assertEquals(1, callbacks.requestDelete)
        assertEquals(0, callbacks.confirmDelete)
    }

    /** 확인 단계에서만 실제 삭제 버튼이 존재한다. 평상시 화면엔 없다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 확인_단계_전에는_실제_삭제_버튼이_존재하지_않는다() = runComposeUiTest {
        showScreen(memberState)

        onNodeWithText("삭제할게요").assertDoesNotExist()
    }

    /** 확인 다이얼로그는 무엇이 사라지는지·되돌릴 수 없음을 사실대로 고지한다(§5.13 · 라이팅 원문). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 확인_다이얼로그는_사라지는_것과_되돌릴_수_없음을_고지한다() = runComposeUiTest {
        showScreen(memberState.copy(deletePhase = DeletePhase.Confirming))

        onNodeWithText("계정을 삭제할까요?").assertIsDisplayed()
        onNodeWithText("모든 학습 기록·숙련도·복습 일정이 삭제돼요. 되돌릴 수 없어요.").assertIsDisplayed()
    }

    /** 취소는 아무 데이터도 건드리지 않는다 — 삭제 콜백이 호출되면 안 된다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 확인_다이얼로그에서_취소하면_삭제가_실행되지_않는다() = runComposeUiTest {
        val callbacks = showScreen(memberState.copy(deletePhase = DeletePhase.Confirming))

        onNodeWithText("취소").assertIsDisplayed().performClick()

        assertEquals(1, callbacks.cancelDelete)
        assertEquals(0, callbacks.confirmDelete)
    }

    /** 명시적 확인을 거쳤을 때만 삭제가 실행된다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 확인_다이얼로그에서_삭제를_누르면_삭제가_실행된다() = runComposeUiTest {
        val callbacks = showScreen(memberState.copy(deletePhase = DeletePhase.Confirming))

        onNodeWithText("삭제할게요").assertIsDisplayed().performClick()

        assertEquals(1, callbacks.confirmDelete)
    }

    /**
     * 전송 중 중복 삭제 금지 — 되돌릴 수 없는 요청이 두 번 나가면 안 된다.
     * 전송 중에는 PrimaryButton이 라벨 대신 스피너를 그리며 클릭을 막는다(§5.1).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 삭제_전송_중에는_삭제가_다시_실행되지_않는다() = runComposeUiTest {
        val callbacks = showScreen(memberState.copy(deletePhase = DeletePhase.Deleting))

        onNodeWithText("삭제할게요").assertDoesNotExist()

        assertEquals(0, callbacks.confirmDelete)
    }

    /** 삭제 실패는 다이얼로그 안에서 비난 없이 안내하고, 확인 단계를 유지한다(막다른 길 금지). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 삭제_실패_안내는_확인_다이얼로그_안에서_보인다() = runComposeUiTest {
        showScreen(
            memberState.copy(
                deletePhase = DeletePhase.Confirming,
                deleteError = "계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.",
            ),
        )

        onNodeWithText("계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.").assertIsDisplayed()
        onNodeWithText("취소").assertIsDisplayed()
    }

    /** ⭐️ 게스트에게는 삭제 확인 다이얼로그가 절대 뜨지 않는다(상태가 잔류해도). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 게스트에게는_삭제_확인_다이얼로그가_뜨지_않는다() = runComposeUiTest {
        showScreen(guestState.copy(deletePhase = DeletePhase.Confirming))

        onNodeWithText("계정을 삭제할까요?").assertDoesNotExist()
        onNodeWithText("삭제할게요").assertDoesNotExist()
    }

    // ── 공포 연출 완화 · MVP 경계 ───────────────────────────────────────────────

    /** ⛔ 경고 배지·위협 문구 금지. 삭제 고지는 사실 중심이어야 한다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 확인_다이얼로그에_경고_배지나_위협_문구가_없다() = runComposeUiTest {
        showScreen(memberState.copy(deletePhase = DeletePhase.Confirming))

        onNodeWithText("⚠", substring = true).assertDoesNotExist()
        onNodeWithText("경고", substring = true).assertDoesNotExist()
        onNodeWithText("주의", substring = true).assertDoesNotExist()
    }

    /** ⛔ MVP에 없는 기능(알림)을 미리 넣지 말 것 — 도메인은 푸시 알림을 로드맵으로 분류한다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun MVP_밖_기능인_학습_알림_설정이_없다() = runComposeUiTest {
        showScreen(memberState)

        onNodeWithText("알림", substring = true).assertDoesNotExist()
        onNodeWithText("리마인더", substring = true).assertDoesNotExist()
    }

    /** 페이지 맵에 없는 화면(통계)으로 가는 하단 탭이 없다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 페이지_맵에_없는_통계_탭이_없다() = runComposeUiTest {
        showScreen(memberState)

        onNodeWithText("통계").assertDoesNotExist()
    }

    /** 화면 테마는 라이트·다크를 1급으로 지원한다(유지되어야 하는 기능). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 화면_테마_토글은_세_가지_선택지를_제공한다() = runComposeUiTest {
        showScreen(memberState)

        onNodeWithText("라이트").assertIsDisplayed()
        onNodeWithText("다크").assertIsDisplayed()
        onNodeWithText("시스템").assertIsDisplayed()
    }

    // ── 세션 크기 설정 (명세 용어 정렬, 2026-07-16 오너 결정) ──────────────────────

    /** ⭐️ 라벨은 명세 용어 "세션 크기"를 쓴다 — "하루 학습 분량"은 명세에 없는 신조어라 금지어다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 세션_크기_설정은_명세_용어_라벨과_보조_설명을_보여준다() = runComposeUiTest {
        showScreen(memberState)

        onNodeWithText("세션 크기").assertIsDisplayed()
        onNodeWithText("하루 세션에서 풀 문제 수예요").assertIsDisplayed()
        onNodeWithText("${memberState.sessionSize}문제").assertIsDisplayed()
        onNodeWithText("하루 학습 분량").assertDoesNotExist()
    }
}
