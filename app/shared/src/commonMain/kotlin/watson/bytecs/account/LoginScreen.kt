package watson.bytecs.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.BcsTextField
import watson.bytecs.ui.components.ErrorBanner
import watson.bytecs.ui.components.InfoCard
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 05 로그인·가입 화면. 게스트가 가입(=제자리 승격)하거나 기존 회원이 로그인한다.
 *
 * ⭐️ 저마찰·안심·무낙인:
 *  - "왜 가입하나"를 한 줄로. 승계 배너("기록이 그대로 옮겨져요")는 **가입 모드에서만** 낸다 — 아래 참조.
 *  - 가입을 진행의 전제로 만들지 않는다 — 상단 "나중에 하기"로 언제든 빠져나갈 수 있다.
 *  - 실패는 처벌이 아니다: "학습 기록은 안전해요"를 먼저 고지하고 사실 기반 원인만 안내(빨강 남용 금지).
 *
 * ⭐️ **승계는 가입 경로에만 있다.** 게스트 토큰을 지닌 채 `register`하면 서버가 그 게스트를 회원으로
 * 제자리 승격시키지만(명세 4 §336), `login`은 **다른 계정**의 토큰으로 갈아끼우는 것이라 게스트 진행분이
 * 옮겨지지 않는다. 명세도 재로그인은 승계가 아니라 영속으로 규정한다(§340 "재로그인해도 … 그대로다").
 * 그러므로 게스트가 로그인 모드에 있어도 승계를 약속하지 않는다 — 지킬 수 없는 약속이다.
 *
 * 성공은 뷰모델의 일회성 이벤트로 받아 정확히 한 번 복귀한다(재사용되는 뷰모델의 잔류 성공 상태로 튕기지 않게).
 *
 * @param initialMode 진입 모드(게스트 가입 CTA는 Register로 진입).
 * @param onSuccess 로그인·가입 성공 시(승계 완료) 이전 맥락으로 복귀.
 * @param onBack "나중에 하기" — 가입 없이 계속 학습.
 */
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    initialMode: AuthMode = AuthMode.Login,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    // 진입 시 폼 초기화(재사용 뷰모델의 이전 입력·모드 잔류 제거 + 진입 모드 지정).
    LaunchedEffect(Unit) {
        viewModel.resetForEntry(initialMode)
    }

    // 성공은 일회성 이벤트. 승계(게스트→회원)면 손끝 피드백을 주고, 정확히 한 번 복귀한다.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is AuthEvent.Succeeded) {
                if (viewModel.uiState.value.isGuestUpgrade) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onSuccess()
            }
        }
    }

    LoginScreenContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onToggleMode = viewModel::toggleMode,
        onSubmit = viewModel::submit,
        onBack = onBack,
        modifier = modifier,
    )
}

/** 표현 계층만 분리한 본체 — 뷰모델 없이 상태를 직접 넣어 테스트한다. */
@Composable
internal fun LoginScreenContent(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    val isRegister = state.mode == AuthMode.Register

    BcsScaffold(
        modifier = modifier,
        topBar = {
            // 가입은 전제조건이 아니다 — 언제든 빠져나갈 수 있는 경로를 상단에 둔다(다크 패턴 금지).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space4, vertical = BcsDimens.space2),
            ) {
                TextLink(
                    text = "나중에 하기",
                    onClick = onBack,
                    color = colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        },
        bottomBar = {
            // 엄지 영역 하단 고정 Primary CTA — 동사형.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
            ) {
                PrimaryButton(
                    text = if (isRegister) "가입하고 기록 저장하기" else "로그인하기",
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    loading = state.status is SubmitStatus.Submitting,
                )
                // 약관 고지 — 최소·명확. 별도 동의 체크박스를 두지 않는다(다크 패턴 동의 강요 금지).
                // ⭐️ 암호화 저장 같은 보안 주장은 쓰지 않는다 — 명세에 없고 구현이 보장하는 속성이 아니다.
                if (isRegister) {
                    Text(
                        text = "가입 시 이용약관 및 개인정보처리방침에 동의해요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BcsDimens.space5),
            verticalArrangement = Arrangement.spacedBy(BcsDimens.space4),
        ) {
            // 헤드라인이 이 화면의 제목이다(시안 h2). 부제는 한 단계 낮은 위계 — 같은 무게로 서면
            // 위계가 평평해져 같은 말의 반복처럼 읽힌다.
            //
            // ⭐️ 모드마다 **사실이 다르다**(명세 4):
            //  - 가입 = 승계. 게스트로 쌓은 상태가 계정으로 "옮겨진다".
            //  - 로그인 = 영속. "재로그인해도 숙련도·복습·세션이 그대로다" — 옮겨지는 게 아니다.
            // 로그인하러 온 사람에게 가입 피치를 최대 강조로 들이밀지 않는다(가입 강요 금지).
            Text(
                text = if (isRegister) {
                    "가입하면 학습 기록이 어느 기기에서든 안전하게 이어져요."
                } else {
                    "다시 오셨네요. 학습 기록이 그대로 기다리고 있어요."
                },
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Text(
                text = if (isRegister) {
                    "어느 기기에서든 로그인만 하세요. 지금까지의 학습 기록이 그대로 옮겨져요."
                } else {
                    "로그인하면 어느 기기에서든 이어서 할 수 있어요."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            // 승계 안내 배너 — 게스트가 **가입할 때만**. isGuestUpgrade는 "지금 게스트인가"일 뿐
            // "가입 중인가"가 아니므로, 모드 조건을 함께 걸지 않으면 로그인 모드의 게스트에게도
            // 승계를 약속하게 된다(오늘의 login()은 아무것도 옮기지 않는다 — 이 파일 상단 KDoc 참조).
            if (state.isGuestUpgrade && isRegister) {
                UpgradeBanner()
            }

            // 게스트가 로그인 모드로 전환한 자리 — 여기서만 이 기기 기록이 사라질 수 있다.
            // 게스트용 CTA는 전부 가입 모드로 진입하므로(App.kt), 게스트가 로그인 모드에 오는 경로는
            // "이미 계정이 있나요? 로그인하기"뿐이다. 그 선택의 결과를 그 자리에서 알린다.
            if (state.isGuestUpgrade && !isRegister) {
                GuestLoginNotice(onSwitchToRegister = onToggleMode)
            }

            Spacer(Modifier.height(BcsDimens.space2))

            // 이메일 — 이메일 키패드 + 인라인 검증(긍정 문구만).
            Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space2)) {
                BcsTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = "이메일 주소",
                    placeholder = "example@email.com",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    // 시안(05 55-57행) — 입력칸 안 우측 체크. 아래 텍스트 헬퍼가 의미를 이미 전달하므로
                    // 아이콘은 장식(중복 안내로 스크린리더를 두 번 말하게 하지 않는다).
                    trailing = if (state.isEmailValid) { { EmailValidCheck() } } else null,
                )
                // ⭐️ 인라인 검증은 '통과'만 말한다(§2.2 success 허용 사례). 미통과는 침묵 — 입력 도중의
                // 미완성 상태를 실패로 낙인찍지 않는다. 하단 CTA 비활성이 이미 "아직 못 낸다"를 말해 준다.
                if (state.isEmailValid) {
                    Text(
                        text = "이메일 형식이 맞아요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.success,
                    )
                }
            }

            // 비밀번호 — 기본 마스킹 + 시안(66-68행)의 표시 토글. 토글은 이 화면의 표현 상태일 뿐이라
            // AuthUiState에 두지 않는다(뷰모델 재사용에도 화면을 새로 그릴 때마다 마스킹으로 되돌아간다).
            var passwordVisible by remember { mutableStateOf(false) }
            BcsTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = "비밀번호",
                placeholder = "8자 이상 입력해주세요",
                keyboardType = KeyboardType.Password,
                masked = !passwordVisible,
                imeAction = ImeAction.Done,
                onImeAction = onSubmit,
                trailing = {
                    PasswordVisibilityToggle(
                        visible = passwordVisible,
                        onToggle = { passwordVisible = !passwordVisible },
                    )
                },
            )

            // 실패 안내 — 비처벌. 공용 ErrorBanner가 "학습 기록은 안전해요"를 먼저 고지하고 재시도 경로를
            // 함께 낸다(막다른 길 금지). 실패에 danger를 쓰지 않는다 — 빨강은 계정 삭제 전용(§2.2).
            (state.status as? SubmitStatus.Failed)?.let { failed ->
                ErrorBanner(message = failed.message, onRetry = onSubmit)
            }

            // 로그인 ↔ 가입 전환(같은 화면에서).
            TextLink(
                text = if (isRegister) "이미 계정이 있나요? 로그인하기" else "아직 계정이 없나요? 가입하기",
                onClick = onToggleMode,
            )

            Spacer(Modifier.height(BcsDimens.space4))
        }
    }
}

/**
 * 게스트가 **로그인 모드**로 갔을 때의 사실 고지 — 이 기기 기록은 가입할 때만 옮겨진다.
 *
 * ⭐️ 경고가 아니라 **사실 고지**다. danger 금지(§2.2) — 로그인은 여전히 정당한 선택이고, 이 안내가
 * "가입 안 하면 손해"로 읽히면 그게 가입 강요다. 사실만 말하고 대안([onSwitchToRegister])을 함께 낸다.
 * 막다른 길로 두지 않되, 가입을 정답으로 밀지도 않는다.
 *
 * ⭐️ 공용 [ErrorBanner]를 쓰지 않는 이유: 그 컴포넌트는 "학습 기록은 안전해요."를 머리말로 **하드코딩**한다.
 * 여기서는 그게 정확히 거짓이다(로그인하면 이 기기 기록은 사라진다). 안심시키면 안 되는 자리다.
 *
 * ⚠️ **한계 — "고지했으니 됐다"로 읽지 말 것.** 이 고지는 유실 자체를 막지 않는다. 오늘의 `login()`은
 * 게스트 진행분을 그대로 폐기하며, 이 화면은 사용자가 그 사실을 **알고 선택하게** 할 뿐이다.
 * 병합할지·차단할지는 여전히 열린 결정이고, 정해지면 이 고지도 함께 바뀌어야 한다.
 */
@Composable
private fun GuestLoginNotice(onSwitchToRegister: () -> Unit) {
    val colors = LocalBcsColors.current
    InfoCard {
        Text(
            text = "이 기기에 쌓인 학습 기록은 가입할 때만 옮겨져요.",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onInfoContainer,
        )
        Text(
            text = "로그인하면 기존 계정의 기록을 불러와요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onInfoContainer,
        )
        TextLink(text = "가입으로 돌아가기", onClick = onSwitchToRegister)
    }
}

/**
 * 승계 안내 배너 — info 톤(안심). 게스트로 쌓은 기록이 가입 시 그대로 이어짐을 강조.
 *
 * ⭐️ 호출 조건은 호출자 책임: **가입 모드 + 게스트**일 때만 부른다. 로그인은 승계 경로가 아니다.
 */
@Composable
private fun UpgradeBanner() {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.infoContainer)
            .padding(BcsDimens.space4),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space1),
    ) {
        Text(
            text = "기록 승계 준비 완료",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onInfoContainer,
        )
        Text(
            text = "지금까지의 학습 기록이 그대로 옮겨져요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onInfoContainer,
        )
    }
}

/**
 * 이메일 검증 통과 체크(시안 05 55-57행) — success 색 체크 글리프.
 *
 * ⭐️ 장식이다: "이메일 형식이 맞아요." 텍스트 헬퍼가 이미 같은 의미를 스크린리더에 전달하므로,
 * 아이콘까지 시맨틱을 열면 같은 말을 두 번 읽는다(clearAndSetSemantics로 비운다). 대신 [testTag]로
 * 노출 여부를 테스트에서 확인할 수 있게 한다.
 */
@Composable
private fun EmailValidCheck() {
    val colors = LocalBcsColors.current
    Text(
        text = "✓",
        style = MaterialTheme.typography.titleMedium,
        color = colors.success,
        modifier = Modifier.clearAndSetSemantics { testTag = "email-valid-check" },
    )
}

/**
 * 비밀번호 표시 토글(시안 05 66-68행) — 눈 아이콘. [ScrapToggle]과 같은 아이콘 버튼 관례를 따른다:
 * 프로젝트가 아이콘 폰트에 기대지 않으므로 이모지 글리프 + 최소 터치 타깃(48dp) + contentDescription.
 *
 * ⭐️ [visible]은 호출자([LoginScreenContent])가 들고 있는 화면 로컬 상태다 — 마스킹 여부는 입력값이
 * 아니라 순수 표현이라 [AuthUiState]에 두지 않는다. 토글은 [masked]만 바꿀 뿐 입력 텍스트·커서는
 * [BcsTextField]의 동일 [BasicTextField] 인스턴스가 그대로 들고 있어 건드리지 않는다.
 */
@Composable
private fun PasswordVisibilityToggle(visible: Boolean, onToggle: () -> Unit) {
    val colors = LocalBcsColors.current
    val label = if (visible) "비밀번호 숨기기" else "비밀번호 표시"
    Text(
        text = if (visible) "🙈" else "👁",
        style = MaterialTheme.typography.titleMedium,
        color = colors.textTertiary,
        modifier = Modifier
            .clip(RoundedCornerShape(BcsDimens.radiusFull))
            .clickable(onClick = onToggle)
            .sizeIn(minWidth = BcsDimens.minTouchTarget, minHeight = BcsDimens.minTouchTarget)
            .wrapContentSize(Alignment.Center)
            .semantics { contentDescription = label },
    )
}

