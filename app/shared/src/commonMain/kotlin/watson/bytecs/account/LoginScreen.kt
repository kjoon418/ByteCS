package watson.bytecs.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.BcsTextField
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 05 로그인·가입 화면. 게스트가 가입(=제자리 승격)하거나 기존 회원이 로그인한다.
 *
 * ⭐️ 저마찰·안심·무낙인:
 *  - "왜 가입하나"를 한 줄로, 승계 배너로 "기록이 그대로 옮겨져요"를 강조(게스트면 로그인·가입 두 모드 모두 노출).
 *  - 가입을 진행의 전제로 만들지 않는다 — 상단 "나중에 하기"로 언제든 빠져나갈 수 있다.
 *  - 실패는 처벌이 아니다: "학습 기록은 안전해요"를 먼저 고지하고 사실 기반 원인만 안내(빨강 남용 금지).
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

@Composable
private fun LoginScreenContent(
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
            ) {
                PrimaryButton(
                    text = if (isRegister) "가입하고 기록 저장하기" else "로그인하기",
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    loading = state.status is SubmitStatus.Submitting,
                )
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
            // 헤더 + 가입 이유 한 줄.
            Text(
                text = if (isRegister) "가입하기" else "로그인",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Text(
                text = "가입하면 학습 기록이 어느 기기에서든 안전하게 이어져요.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            // 승계 안내 배너 — 게스트에서 왔으면 로그인·가입 두 모드 모두에서 안심을 강조한다.
            if (state.isGuestUpgrade) {
                UpgradeBanner()
            }

            Spacer(Modifier.height(BcsDimens.space2))

            // 이메일 — 이메일 키패드.
            BcsTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = "이메일",
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )

            // 비밀번호 — 마스킹.
            BcsTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = "비밀번호",
                placeholder = "비밀번호를 입력해 주세요",
                keyboardType = KeyboardType.Password,
                masked = true,
                imeAction = ImeAction.Done,
                onImeAction = onSubmit,
            )

            // 실패 안내 — 비처벌. 안심 문구 먼저, 사실 기반 원인 다음. 빨강 남용 금지.
            (state.status as? SubmitStatus.Failed)?.let { failed ->
                FailureNotice(message = failed.message)
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

/** 승계 안내 배너 — info 톤(안심). 게스트로 쌓은 기록이 그대로 이어짐을 강조. */
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
            text = "지금까지 푼 기록이 이 계정으로 그대로 옮겨져요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onInfoContainer,
        )
    }
}

/** 실패 안내 — info 톤 + 안심 문구 우선. 오답·처벌이 아니므로 danger를 쓰지 않는다. */
@Composable
private fun FailureNotice(message: String) {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.infoContainer)
            // 스크린리더가 실패 안내를 즉시 읽도록 라이브 리전.
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(BcsDimens.space4),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space1),
    ) {
        Text(
            text = "학습 기록은 안전해요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onInfoContainer,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onInfoContainer,
        )
    }
}
