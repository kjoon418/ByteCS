package watson.bytecs.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.ErrorBanner
import watson.bytecs.ui.components.InfoCard
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 07 콘텐츠 오류 신고 화면(`docs/design/07 콘텐츠 오류 신고 화면 디자인.html`).
 *
 * 시안의 카테고리 선택지는 두지 않는다(팀 계약: 자유 서술 message 한 필드 + 제출).
 * ⭐️ 빈 입력으로는 보낼 수 없다(뷰모델·서버 모두 blank 거부). 전송 실패는 무낙인 시스템 오류로 안내한다(§5.12).
 */
@Composable
fun ReportScreen(
    viewModel: ReportViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportScreenContent(
        state = state,
        onMessageChange = viewModel::onMessageChange,
        onSubmit = viewModel::submit,
        onClose = onClose,
        modifier = modifier,
    )
}

@Composable
internal fun ReportScreenContent(
    state: ReportUiState,
    onMessageChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    BcsScaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextLink(
                    text = "닫기",
                    onClick = onClose,
                    color = colors.textSecondary,
                    contentDescription = "신고 닫기",
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
            ) {
                if (state.submitted) {
                    // 접수 후에는 진행이 아니라 마무리다 — 유일한 액션은 닫기.
                    PrimaryButton(text = "확인", onClick = onClose)
                } else {
                    PrimaryButton(
                        text = "신고 보내기",
                        onClick = onSubmit,
                        // ⭐️ 빈 입력 제출 불가.
                        enabled = state.message.isNotBlank(),
                        loading = state.isSubmitting,
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
            Spacer(Modifier.height(BcsDimens.space2))

            Text(
                text = "콘텐츠 오류 신고",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Text(
                text = "발견하신 오류를 알려주시면 빠르게 수정할게요.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            if (state.submitted) {
                // 접수 완료 — 감사 안내(정보 톤, 낙인·경고 없음).
                InfoCard {
                    Text(
                        text = "알려주셔서 고마워요! 확인 후 빠르게 반영할게요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onInfoContainer,
                    )
                }
            } else {
                Spacer(Modifier.height(BcsDimens.space2))
                Text(
                    text = "무엇이 잘못됐는지 알려주세요",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textLabel,
                )
                ReportMessageField(
                    value = state.message,
                    onValueChange = onMessageChange,
                    placeholder = "예: 정답이 틀렸어요 / 문제 설명에 오류가 있어요",
                )

                if (state.submitFailed) {
                    ErrorBanner(
                        message = "신고를 보내지 못했어요. 잠시 후 다시 시도해 주세요.",
                        onRetry = onSubmit,
                    )
                }
            }

            Spacer(Modifier.height(BcsDimens.space6))
        }
    }
}

/**
 * 자유 서술 신고 입력(여러 줄). [watson.bytecs.ui.components.AnswerTextField]와 시각 골격을 공유하되
 * 여러 줄을 받는다. 단일 화면에서만 쓰이므로 별도 공용 컴포넌트로 빼지 않는다.
 * ⭐️ 무낙인: 검증 실패에도 테두리를 danger로 바꾸지 않는다 — 제출 버튼 비활성으로만 안내한다.
 */
@Composable
private fun ReportMessageField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else colors.border

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = false,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 여러 줄을 위해 입력 높이의 두 배 이상을 확보한다(레이아웃 산식 — 토큰 배수).
                    .heightIn(min = BcsDimens.inputHeight * 2)
                    .clip(RoundedCornerShape(BcsDimens.radiusCard))
                    .background(colors.surfaceSubtle)
                    .border(BcsDimens.borderWidth, borderColor, RoundedCornerShape(BcsDimens.radiusCard))
                    .padding(horizontal = BcsDimens.space4, vertical = BcsDimens.space3),
                contentAlignment = Alignment.TopStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textTertiary,
                    )
                }
                innerTextField()
            }
        },
    )
}
