package watson.bytecs.account

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import watson.bytecs.ui.theme.BcsDarkColorScheme
import watson.bytecs.ui.theme.BcsLightColorScheme
import watson.bytecs.ui.theme.BcsLightColors
import watson.bytecs.ui.theme.BcsTheme

/**
 * 05 로그인·가입 화면. 시안(`docs/design/05 로그인 가입 화면 디자인.html`)·페이지 명세 대조.
 *
 * ⭐️ 이 화면에서 뚫렸던 지점은 **실패 표현**이다. 로그인 실패는 처벌이 아니므로 빨강(danger)을 쓰지 않고,
 * 항상 재시도 경로를 함께 낸다. 문구·색 규칙 자체가 계약이라 테스트로 못박는다.
 *
 * 색 규칙은 매핑 함수(ComponentTonesTest)가 아니라 **렌더된 픽셀**에서 확인한다 — 화면이 토큰을 우회해
 * 어딘가에 빨강을 칠하는 실수는 매핑 테스트로는 잡히지 않기 때문이다.
 */
class LoginScreenUiTest {

    private val failedLogin = AuthUiState(
        mode = AuthMode.Login,
        email = "hanip@example.com",
        password = "password",
        status = SubmitStatus.Failed("이메일 또는 비밀번호를 다시 확인해 주세요."),
    )

    // ── 인증 수단: 이메일 단일 ───────────────────────────────────────────────

    /** ⭐️ 명세 §3.2 — 인증 수단은 이메일 단일. 소셜 로그인 버튼이 다시 기어들어오면 실패한다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 소셜_로그인_수단을_노출하지_않는다() = runComposeUiTest {
        setContent { BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Register)) } }

        onNodeWithText("이메일 주소").assertIsDisplayed()
        listOf("카카오", "Apple", "애플", "Google", "구글", "네이버", "간편 로그인").forEach { social ->
            onNodeWithText(social, substring = true).assertDoesNotExist()
        }
    }

    // ── 실패 표현: 무낙인 + 막다른 길 금지 ──────────────────────────────────

    /**
     * ⭐️⭐️ 도메인 가드레일 — 로그인 실패 화면에 처벌색(danger)이 **단 한 픽셀도** 없어야 한다.
     *
     * 특정 노드만 검사하면 배경·테두리·아이콘 어딘가로 빨강이 새어도 통과하므로, 렌더 결과 전체를 훑어
     * danger 계열 색의 부재를 본다. 빨강은 계정 삭제 전용이다(§2.2).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 로그인_실패_화면에_처벌색을_쓰지_않는다() = runComposeUiTest {
        setContent { BcsTheme(darkTheme = false) { Screen(failedLogin) } }

        val punitive = listOf(
            BcsLightColorScheme.error,
            BcsLightColorScheme.errorContainer,
            BcsLightColorScheme.onErrorContainer,
        )
        val rendered = renderedColors()
        punitive.forEach { forbidden ->
            assertTrue(forbidden !in rendered, "실패 안내에 처벌색($forbidden)이 쓰였다 — 빨강은 계정 삭제 전용이다")
        }
    }

    /** 다크 스킴에서도 같은 규칙이다 — 테마가 바뀐다고 실패가 처벌이 되지는 않는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 다크_테마의_로그인_실패_화면에도_처벌색을_쓰지_않는다() = runComposeUiTest {
        setContent { BcsTheme(darkTheme = true) { Screen(failedLogin) } }

        val punitive = listOf(
            BcsDarkColorScheme.error,
            BcsDarkColorScheme.errorContainer,
            BcsDarkColorScheme.onErrorContainer,
        )
        val rendered = renderedColors()
        punitive.forEach { forbidden ->
            assertTrue(forbidden !in rendered, "다크 실패 안내에 처벌색($forbidden)이 쓰였다")
        }
    }

    /** ⭐️ 막다른 길 금지 — 실패 안내에는 항상 재시도 경로가 붙어 있고, 실제로 제출로 이어진다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 실패_안내는_재시도_경로를_함께_낸다() = runComposeUiTest {
        var retried = 0
        setContent { BcsTheme(darkTheme = false) { Screen(failedLogin, onSubmit = { retried++ }) } }

        onNodeWithText("다시 시도하기").assertIsDisplayed().performClick()
        assertEquals(1, retried, "재시도 버튼이 제출로 이어지지 않았다")
    }

    /** 실패는 안심 고지가 먼저다 — 사용자를 비난하는 낙인 문구를 쓰지 않는다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 실패_안내는_안심_문구를_먼저_고지한다() = runComposeUiTest {
        setContent { BcsTheme(darkTheme = false) { Screen(failedLogin) } }

        onNodeWithText("학습 기록은 안전해요.").assertIsDisplayed()
        onNodeWithText("이메일 또는 비밀번호를 다시 확인해 주세요.").assertIsDisplayed()
        listOf("오류", "실패", "잘못", "틀렸").forEach { blame ->
            onNodeWithText(blame, substring = true).assertDoesNotExist()
        }
    }

    // ── 형식 오류(QA #5): 연결 실패 문구로 새지 않는다 ──────────────────────

    /**
     * ⭐️ QA #5 회귀 — 클라 사전 검증을 통과해 서버까지 간 형식 오류(400)는 "연결이 원활하지 않다"가
     * 아니라 서버가 준 형식 오류 문구로 안내해야 한다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 이메일_형식_오류는_서버_문구로_안내하고_연결_실패로_보이지_않는다() = runComposeUiTest {
        val invalidFormat = AuthUiState(
            mode = AuthMode.Register,
            email = "aaa@aaa",
            password = "password",
            status = SubmitStatus.Failed("이메일 형식이 올바르지 않습니다."),
        )
        setContent { BcsTheme(darkTheme = false) { Screen(invalidFormat) } }

        onNodeWithText("이메일 형식이 올바르지 않습니다.").assertIsDisplayed()
        onNodeWithText("잠시 연결이 원활하지 않았어요.", substring = true).assertDoesNotExist()
    }

    // ── 인라인 검증: 통과만, success 톤 ─────────────────────────────────────

    /**
     * ⭐️ §2.2 — 인라인 검증 통과는 success(emerald #059669). Tailwind green이 아니라 토큰이어야 하므로
     * 렌더된 픽셀에 실제 success 색이 있는지 본다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 이메일_검증_통과는_success_톤으로_알린다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Register, email = "hanip@example.com")) }
        }

        onNodeWithText("이메일 형식이 맞아요.").assertIsDisplayed()
        assertTrue(
            BcsLightColors.success in renderedColors(),
            "인라인 검증 통과 문구가 success 토큰(#059669)으로 그려지지 않았다",
        )
    }

    /** 입력 도중의 미완성은 실패가 아니다 — 통과 전에는 침묵하고, 제출도 막는다(부정 문구 금지). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 이메일이_아직_유효하지_않으면_검증_문구도_제출도_없다() = runComposeUiTest {
        var submitted = 0
        setContent {
            BcsTheme(darkTheme = false) {
                Screen(AuthUiState(mode = AuthMode.Register, email = "hanip"), onSubmit = { submitted++ })
            }
        }

        onNodeWithText("이메일 형식이 맞아요.").assertDoesNotExist()
        // 미완성 입력에 낙인을 찍지 않는다 — 비활성 CTA가 "아직 못 낸다"를 이미 말한다.
        onNodeWithText("올바르지", substring = true).assertDoesNotExist()
        onNodeWithText("형식이 아니", substring = true).assertDoesNotExist()

        onNodeWithText("가입하고 기록 저장하기").performClick()
        assertEquals(0, submitted, "이메일이 유효하지 않은데 제출이 나갔다")
    }

    // ── 가입 강제 금지 ──────────────────────────────────────────────────────

    /** ⛔ 가입은 진행의 전제조건이 아니다 — 로그인·가입 어느 모드에서도 빠져나갈 경로가 있어야 한다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 가입_없이_빠져나갈_경로가_항상_있다() {
        listOf(AuthMode.Login, AuthMode.Register).forEach { mode ->
            runComposeUiTest {
                var backed = 0
                setContent { BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = mode), onBack = { backed++ }) } }

                onNodeWithText("나중에 하기").assertIsDisplayed().performClick()
                assertEquals(1, backed, "$mode 모드에서 '나중에 하기'가 동작하지 않았다")
            }
        }
    }

    /** 약관은 최소·명확한 고지다 — 별도 동의 체크박스로 강요하지 않는다(다크 패턴 금지). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 약관은_고지만_하고_동의를_강요하지_않는다() = runComposeUiTest {
        setContent { BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Register)) } }

        onNodeWithText("가입 시 이용약관 및 개인정보처리방침에 동의해요.").assertIsDisplayed()
        onNodeWithText("모두 동의", substring = true).assertDoesNotExist()
        onNodeWithText("(필수)", substring = true).assertDoesNotExist()
    }

    // ── 라이팅·승계 안심 ────────────────────────────────────────────────────

    /**
     * ⭐️⭐️ 도메인 가드레일 — 로그인 모드에 **가입 전제·승계 주장을 두지 않는다.**
     *
     * 명세 4가 둘을 구분한다: 승계는 **가입 시**만이고(§336), 재로그인은 승계가 아니라 영속이다
     * ("재로그인해도 숙련도·복습·세션이 그대로다", §340). 로그인하러 온 사람의 최대 강조가 가입 피치면
     * 가입 강요이기도 하다.
     *
     * (전환 링크는 로그인 모드에서 "아직 계정이 없나요? 가입하기"라 "가입하면" substring과 겹치지 않는다.)
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 로그인_모드는_가입을_전제하거나_승계를_주장하지_않는다() = runComposeUiTest {
        setContent { BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Login)) } }

        onNodeWithText("가입하면", substring = true).assertDoesNotExist()
        onNodeWithText("옮겨져요", substring = true).assertDoesNotExist()
    }

    /** 로그인 모드도 자기 카피를 갖는다 — 가입 카피를 지우고 빈칸으로 두지 않는다(영속 = 사실). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 로그인_모드는_영속을_사실대로_전한다() = runComposeUiTest {
        setContent { BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Login)) } }

        onNodeWithText("다시 오셨네요. 학습 기록이 그대로 기다리고 있어요.").assertIsDisplayed()
        onNodeWithText("로그인하면 어느 기기에서든 이어서 할 수 있어요.").assertIsDisplayed()
    }

    /**
     * ⭐️ 위계 — 헤드라인이 부제보다 무거워야 한다(시안 h2 2xl bold vs p 15px).
     *
     * 색 같은 대리 지표가 아니라 `GetTextLayoutResult`로 **실제 적용된 스타일**을 읽는다. 둘이 같은
     * 무게로 서면 위계가 평평해져 같은 말의 반복처럼 읽힌다 — 그 회귀를 여기서 잠근다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 헤드라인은_부제보다_시각적으로_무겁다() = runComposeUiTest {
        setContent { BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Register)) } }

        val headline = onNodeWithText("가입하면 학습 기록이 어느 기기에서든 안전하게 이어져요.").appliedStyle()
        val subtitle = onNodeWithText("어느 기기에서든 로그인만 하세요. 지금까지의 학습 기록이 그대로 옮겨져요.")
            .appliedStyle()

        assertTrue(
            headline.fontSize.value > subtitle.fontSize.value,
            "헤드라인(${headline.fontSize})이 부제(${subtitle.fontSize})보다 커야 한다 — 위계가 평평해졌다",
        )
    }

    /** 명세가 예시문까지 지정한 라이팅 — 축약 경어체. 승계 안심이 핵심 메시지다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 가입_이유와_승계_안심을_명세_문구로_전한다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Register, isGuestUpgrade = true)) }
        }

        onNodeWithText("가입하면 학습 기록이 어느 기기에서든 안전하게 이어져요.").assertIsDisplayed()
        onNodeWithText("어느 기기에서든 로그인만 하세요. 지금까지의 학습 기록이 그대로 옮겨져요.").assertIsDisplayed()
        onNodeWithText("기록 승계 준비 완료").assertIsDisplayed()
        onNodeWithText("지금까지의 학습 기록이 그대로 옮겨져요.").assertIsDisplayed()
    }

    /**
     * 승계 배너는 게스트에서 왔을 때만 — 이미 회원이면 옮겨질 게 없다.
     *
     * ⭐️ 픽스처가 **가입 모드**인 게 핵심이다. 배너를 애초에 막는 로그인 모드에서 검사하면 `isRegister`가
     * 혼자 통과시켜 `isGuestUpgrade` 조건이 사라져도 모른다 — 배너가 **허용되는** 모드에서 봐야
     * 게스트 여부가 실제로 변별된다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 승계_배너는_게스트에서_왔을_때만_노출한다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Register, isGuestUpgrade = false)) }
        }

        onNodeWithText("기록 승계 준비 완료").assertDoesNotExist()
    }

    /**
     * ⭐️⭐️ 게스트여도 **로그인 모드**면 승계를 약속하지 않는다.
     *
     * `isGuestUpgrade`는 "지금 게스트인가"일 뿐 "가입 중인가"가 아니다. 오늘의 `login()`은 다른 계정
     * 토큰으로 갈아끼울 뿐 게스트 진행분을 옮기지 않으므로(§340 재로그인=영속), 이 조합에 배너를 내면
     * **지킬 수 없는 약속**이 된다. 모드 조건 없이 `isGuestUpgrade`만 보는 게이팅으로 되돌아가면 죽는다.
     *
     * ⚠️ "옮겨져요" substring 부재로는 더 이상 이 규칙을 표현할 수 없다 — 기록 보존 고지가 바로 그 말을
     * **부정하는 데** 쓰기 때문이다("가입할 때만 옮겨져요"). 그래서 **약속 문장 자체의 부재**를 보고,
     * 대신 정정 사실이 **있는지**를 함께 단언해 검사력을 잃지 않는다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 게스트여도_로그인_모드에서는_승계를_약속하지_않는다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Login, isGuestUpgrade = true)) }
        }

        onNodeWithText("기록 승계 준비 완료").assertDoesNotExist()
        onNodeWithText("지금까지의 학습 기록이 그대로 옮겨져요.").assertDoesNotExist()
        // 약속을 안 하는 데 그치지 않고, 사실을 말한다.
        onNodeWithText("이 기기에 쌓인 학습 기록은 가입할 때만 옮겨져요.").assertIsDisplayed()
    }

    /**
     * 짝 단언 — 게스트 + **가입** 모드에는 배너가 실제로 나온다.
     * 위 부재 단언만 있으면 "배너를 아예 지우기"로도 통과되므로, 승계가 사실인 경로를 함께 못박는다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 게스트가_가입할_때는_승계_배너를_낸다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Register, isGuestUpgrade = true)) }
        }

        onNodeWithText("기록 승계 준비 완료").assertIsDisplayed()
        onNodeWithText("지금까지의 학습 기록이 그대로 옮겨져요.").assertIsDisplayed()
        // 승계가 사실인 자리에 "가입할 때만" 고지가 끼어들면 중복이자 모순이다.
        onNodeWithText("이 기기에 쌓인 학습 기록은 가입할 때만 옮겨져요.").assertDoesNotExist()
    }

    /**
     * ⭐️⭐️ 게스트가 로그인 모드로 가면 **기록 보존 조건을 그 자리에서** 고지한다.
     *
     * 게스트용 CTA는 전부 가입 모드로 진입하므로(`App.kt`), 게스트가 로그인 모드에 닿는 경로는 전환 링크뿐이다.
     * "아 나 계정 있지" 하고 누르는 그 순간이 이 기기 기록이 사라지는 분기점이라, 고지가 거기 있어야 한다.
     *
     * 막다른 길로 두지 않는다 — 가입으로 돌아가는 경로가 함께 있고 실제로 동작한다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 게스트가_로그인_모드로_가면_기록_보존_조건을_고지한다() = runComposeUiTest {
        var toggled = 0
        setContent {
            BcsTheme(darkTheme = false) {
                Screen(AuthUiState(mode = AuthMode.Login, isGuestUpgrade = true), onToggleMode = { toggled++ })
            }
        }

        onNodeWithText("이 기기에 쌓인 학습 기록은 가입할 때만 옮겨져요.").assertIsDisplayed()
        onNodeWithText("로그인하면 기존 계정의 기록을 불러와요.").assertIsDisplayed()
        onNodeWithText("가입으로 돌아가기").assertIsDisplayed().performClick()
        assertEquals(1, toggled, "'가입으로 돌아가기'가 가입 모드 전환으로 이어지지 않았다")
    }

    /**
     * 짝 단언 — 고지는 **게스트 + 로그인**에만. 조건이 합성(`isGuestUpgrade && !isRegister`)이라
     * 각 절반을 따로 죽이는 픽스처를 둘 다 둔다:
     *  - 비게스트 + 로그인 → `isGuestUpgrade` 절반을 고정(옮겨질 기록이 없는 사람에게 고지는 소음이다)
     *  - 게스트 + 가입 → `!isRegister` 절반을 고정(승계가 사실인 자리다)
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 기록_보존_고지는_게스트_로그인_모드가_아니면_나오지_않는다() {
        listOf(
            "비게스트 로그인" to AuthUiState(mode = AuthMode.Login, isGuestUpgrade = false),
            "게스트 가입" to AuthUiState(mode = AuthMode.Register, isGuestUpgrade = true),
        ).forEach { (label, state) ->
            runComposeUiTest {
                setContent { BcsTheme(darkTheme = false) { Screen(state) } }

                onNodeWithText("이 기기에 쌓인 학습 기록은 가입할 때만 옮겨져요.")
                    .assertDoesNotExist()
                onNodeWithText("가입으로 돌아가기").assertDoesNotExist()
            }
        }
    }

    /** ⭐️ 고지는 사실 전달이지 처벌이 아니다 — 로그인은 여전히 정당한 선택이다(danger 금지). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 기록_보존_고지에_처벌색을_쓰지_않는다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Login, isGuestUpgrade = true)) }
        }

        val rendered = renderedColors()
        listOf(
            BcsLightColorScheme.error,
            BcsLightColorScheme.errorContainer,
            BcsLightColorScheme.onErrorContainer,
        ).forEach { forbidden ->
            assertTrue(forbidden !in rendered, "기록 보존 고지에 처벌색($forbidden) — 경고가 아니라 사실 고지다")
        }
    }

    /**
     * ⭐️ 구현이 보장하지 않는 보안 속성을 약속하지 않는다 — 명세에 없는 주장이다.
     * (저장 암호화는 이 화면이 아는 사실이 아니다.)
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 암호화_저장을_고지하지_않는다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Register, isGuestUpgrade = true)) }
        }

        listOf("암호화", "안전하게 저장", "encrypt").forEach { claim ->
            onNodeWithText(claim, substring = true).assertDoesNotExist()
        }
    }

    // ── 이메일 검증 체크 아이콘(시안 05 55-57행) ────────────────────────────

    /**
     * ⭐️ 체크는 장식([EmailValidCheck]가 clearAndSetSemantics로 시맨틱을 비운다 — 의미는 인접한
     * "이메일 형식이 맞아요." 텍스트가 전달)이라 텍스트가 아니라 testTag로 노출을 확인한다.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 이메일_검증을_통과하면_체크_아이콘이_뜬다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) {
                Screen(AuthUiState(mode = AuthMode.Register, email = "hanip@example.com"))
            }
        }

        // useUnmergedTree: BcsTextField의 라벨+입력 래퍼가 mergeDescendants=true라, testTag는
        // 병합 트리로 올라오지 않는다(TestTag의 병합 정책은 자식 값을 취하지 않는다).
        onNodeWithTag("email-valid-check", useUnmergedTree = true).assertExists()
    }

    /** 미통과 입력 도중에는 텍스트 헬퍼처럼 체크 아이콘도 침묵한다(낙인 금지). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 이메일이_유효하지_않으면_체크_아이콘도_없다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Register, email = "hanip")) }
        }

        onNodeWithTag("email-valid-check", useUnmergedTree = true).assertDoesNotExist()
    }

    // ── 비밀번호 표시 토글(시안 05 66-68행) ──────────────────────────────────

    /** 토글을 누르면 접근성 라벨이 "표시"→"숨기기"로 뒤집힌다(마스킹 해제). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 비밀번호_토글을_누르면_숨기기_라벨로_전환된다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Login, password = "password1")) }
        }

        onNodeWithContentDescription("비밀번호 표시").assertIsDisplayed().performClick()

        onNodeWithContentDescription("비밀번호 숨기기").assertIsDisplayed()
        onNodeWithContentDescription("비밀번호 표시").assertDoesNotExist()
    }

    /** 다시 누르면 마스킹으로 되돌아간다 — 토글이 상호 가역적이다(재적용). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 비밀번호_토글을_두_번_누르면_다시_마스킹으로_돌아온다() = runComposeUiTest {
        setContent {
            BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Login, password = "password1")) }
        }

        onNodeWithContentDescription("비밀번호 표시").performClick()
        onNodeWithContentDescription("비밀번호 숨기기").performClick()

        onNodeWithContentDescription("비밀번호 표시").assertIsDisplayed()
    }

    /** ⭐️ 토글은 순수 표현 상태다 — 입력값 변경 콜백을 건드리지 않아야 입력 내용·커서가 그대로 유지된다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 비밀번호_토글은_입력값_변경_콜백을_트리거하지_않는다() = runComposeUiTest {
        var changed = 0
        setContent {
            BcsTheme(darkTheme = false) {
                Screen(
                    AuthUiState(mode = AuthMode.Login, password = "password1"),
                    onPasswordChange = { changed++ },
                )
            }
        }

        onNodeWithContentDescription("비밀번호 표시").performClick()

        assertEquals(0, changed, "토글이 비밀번호 입력값 콜백을 호출하면 안 된다 — 표현만 바뀐다")
    }

    // ── CTA: 동사형 ─────────────────────────────────────────────────────────

    /** §3.5 Primary CTA는 동사형이고 모드에 따라 달라진다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun CTA는_모드에_따른_동사형_문구를_쓴다() {
        runComposeUiTest {
            setContent { BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Register)) } }
            onNodeWithText("가입하고 기록 저장하기").assertIsDisplayed()
        }
        runComposeUiTest {
            setContent { BcsTheme(darkTheme = false) { Screen(AuthUiState(mode = AuthMode.Login)) } }
            onNodeWithText("로그인하기").assertIsDisplayed()
        }
    }
}

/**
 * 노드에 **실제 적용된** 텍스트 스타일. `Text`가 자동 노출하는 `GetTextLayoutResult` 시맨틱 액션으로
 * 레이아웃 입력을 그대로 읽는다 — 색 같은 대리 지표가 아니라 진짜 fontSize/fontWeight다.
 */
private fun SemanticsNodeInteraction.appliedStyle(): TextStyle {
    val results = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
    return results.first().layoutInput.style
}

/** 렌더된 화면 전체의 색 집합. 색 규칙을 노드가 아니라 실제 픽셀에서 확인하기 위한 것. */
@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.renderedColors(): Set<Color> {
    val pixels = onRoot().captureToImage().toPixelMap()
    val colors = mutableSetOf<Color>()
    for (y in 0 until pixels.height) {
        for (x in 0 until pixels.width) {
            colors += pixels[x, y]
        }
    }
    return colors
}

/** 테스트용 화면 — 도메인 흐름(뷰모델)이 아니라 표현만 검증한다. */
@androidx.compose.runtime.Composable
private fun Screen(
    state: AuthUiState,
    onSubmit: () -> Unit = {},
    onBack: () -> Unit = {},
    onToggleMode: () -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
) {
    LoginScreenContent(
        state = state,
        onEmailChange = {},
        onPasswordChange = onPasswordChange,
        onToggleMode = onToggleMode,
        onSubmit = onSubmit,
        onBack = onBack,
    )
}
