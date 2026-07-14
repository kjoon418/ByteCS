package watson.bytecs.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.BcsMotion
import watson.bytecs.ui.theme.BcsType
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * DESIGN_SYSTEM.md §5 컴포넌트. 03 문제 풀이 슬라이스가 쓰는 것만 구현한다.
 * 모든 색·치수는 토큰만 참조한다(raw hex/dp 0건, 레이아웃 산식 예외).
 */

/**
 * §5.1 PrimaryButton — 화면의 유일한 강조 액션(정답 확인하기).
 * fillMaxWidth, height 56dp, radius 16, 눌림 시 강조 스케일. 로딩 중에는 클릭을 막는다.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val clickable = enabled && !loading
    val scale by animateFloatAsState(
        targetValue = if (pressed && clickable) BcsMotion.pressScaleStrong else 1f,
        animationSpec = tween(BcsMotion.durFast, easing = BcsMotion.easing),
    )
    val colorScheme = MaterialTheme.colorScheme
    val colors = LocalBcsColors.current
    val container = when {
        !clickable -> colorScheme.primary.copy(alpha = 0.4f)
        pressed -> colors.primaryPressed // §2.1 실제 primaryPressed 토큰
        else -> colorScheme.primary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // ⭐️ 글자 확대 시 클리핑 방지: 고정 높이가 아니라 최소 높이(§7 글자 확대 대응).
            .heightIn(min = BcsDimens.buttonHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = clickable,
                onClick = onClick,
            )
            .padding(vertical = BcsDimens.space2),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(BcsDimens.loaderSize),
                color = colorScheme.onPrimary,
                strokeWidth = BcsDimens.loaderStroke,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onPrimary,
            )
        }
    }
}

/**
 * §5.2 AnswerTextField — 주관식 단답 입력.
 * height 56dp, radius 16, bg surfaceSubtle, border 1dp(포커스 시 primary).
 * ⭐️ 불일치 상태에도 border를 danger로 바꾸지 않는다(무낙인). 재시도 유도는 인접 넛지가 담당.
 */
@Composable
fun AnswerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    onImeSubmit: () -> Unit = {},
) {
    val colors = LocalBcsColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else colors.border

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onImeSubmit() }),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 글자 확대 시 세로 클리핑 방지(§7).
                    .heightIn(min = BcsDimens.inputHeight)
                    .clip(RoundedCornerShape(BcsDimens.radiusCard))
                    .background(colors.surfaceSubtle)
                    .border(BcsDimens.borderWidth, borderColor, RoundedCornerShape(BcsDimens.radiusCard))
                    .padding(horizontal = BcsDimens.space4, vertical = BcsDimens.space2),
                contentAlignment = Alignment.CenterStart,
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

/**
 * §5.1 GhostButton — 저강조 보조 액션(로그아웃·취소·삭제 진입 등). 투명 배경 + 1dp 테두리.
 * 색은 기본 중립(textLabel/border)이되, 파괴적 진입은 [contentColor]/[borderColor]로 error를 넘겨 쓴다.
 * ⭐️ 삭제 확인의 "취소"가 시각적으로 쉬운 기본 선택이 되도록, 취소는 **보이는 테두리**의 GhostButton을 쓴다.
 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = LocalBcsColors.current.textLabel,
    borderColor: Color = LocalBcsColors.current.border,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BcsDimens.buttonHeight)
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .border(BcsDimens.borderWidth, borderColor, RoundedCornerShape(BcsDimens.radiusCard))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = BcsDimens.space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
        )
    }
}

/**
 * §5 TextLink — 인라인 텍스트 액션(로그인↔가입 전환·"나중에 하기"·"뒤로"·계정 진입 등).
 * ⭐️ 글자만으로도 최소 터치 타깃(48dp)을 보장한다(작은 글자를 정확히 누를 수 있게).
 */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(BcsDimens.radiusSm))
            .clickable(onClick = onClick)
            // §7 터치 타깃: 텍스트가 짧아도 최소 48dp 안에서 눌리도록.
            .sizeIn(minHeight = BcsDimens.minTouchTarget)
            .wrapContentSize(Alignment.Center)
            .padding(horizontal = BcsDimens.space2)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * §5.2 파생 — 라벨·마스킹·키패드를 지정할 수 있는 범용 단답 입력. 05 로그인·가입 폼이 쓴다.
 * [AnswerTextField]와 시각 골격(높이·라운드·포커스 테두리)을 공유하되, 라벨/이메일 키패드/비밀번호 마스킹을
 * 지원한다. ⭐️ 검증 실패에도 테두리를 danger로 바꾸지 않는다(무낙인) — 안내는 인접 텍스트가 담당한다.
 */
@Composable
fun BcsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    masked: Boolean = false,
    enabled: Boolean = true,
    onImeAction: () -> Unit = {},
) {
    val colors = LocalBcsColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else colors.border

    // ⭐️ a11y: 라벨과 입력을 한 노드로 병합해, 스크린리더가 "라벨 + 입력한 값"을 함께 읽게 한다.
    // (입력 노드에 contentDescription을 덮어쓰면 입력값 대신 라벨만 읽혀 값이 가려지므로 그렇게 하지 않는다.)
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space2),
    ) {
        if (label != null) {
            Text(
                text = label,
                // §3.2 label 토큰(14sp SemiBold) = labelLarge. 필드 라벨은 또렷하게.
                style = MaterialTheme.typography.labelLarge,
                color = colors.textLabel,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction() },
                onGo = { onImeAction() },
                onNext = { onImeAction() },
            ),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = BcsDimens.inputHeight)
                        .clip(RoundedCornerShape(BcsDimens.radiusCard))
                        .background(colors.surfaceSubtle)
                        .border(BcsDimens.borderWidth, borderColor, RoundedCornerShape(BcsDimens.radiusCard))
                        .padding(horizontal = BcsDimens.space4, vertical = BcsDimens.space2),
                    contentAlignment = Alignment.CenterStart,
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
}

/**
 * §5.4 SessionProgress — 분량 기반 진행(`2 / 5` + 점). ⭐️ 카운트다운 타이머 아님.
 * 완료=primary 채운 점, 현재=primary 테두리, 남음=border.
 */
@Composable
fun SessionProgress(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    val primary = MaterialTheme.colorScheme.primary

    Row(
        // 진행도는 한 줄로 읽어 준다. 개별 점은 장식이고, 인접 "n / total" 텍스트가 의미의 원천이다.
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "총 ${total}문제 중 ${current}번째 문제"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        Text(
            text = "$current / $total",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(BcsDimens.space1)) {
            for (position in 1..total) {
                val dotModifier = Modifier
                    .size(BcsDimens.progressDot)
                    .clip(CircleShape)
                when {
                    position < current -> Box(dotModifier.background(primary))
                    position == current -> Box(dotModifier.border(BcsDimens.borderWidthStrong, primary, CircleShape))
                    else -> Box(dotModifier.background(colors.border))
                }
            }
        }
    }
}

/**
 * §7 코드 스니펫 — 고정폭. 짧은 줄 큐레이션을 전제로 하되, 부득이한 긴 줄만 가로 스크롤로 격리해
 * 본문 흐름은 유지한다.
 */
@Composable
fun CodeSnippetBlock(
    code: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.surfaceSubtle)
            .border(BcsDimens.borderWidth, colors.borderSubtle, RoundedCornerShape(BcsDimens.radiusCard))
            .horizontalScroll(rememberScrollState())
            .padding(BcsDimens.space4),
    ) {
        Text(text = code, style = BcsType.codeBlock, color = colors.textBody)
    }
}

/**
 * §7 BcsScaffold — 상단 바 + 중앙 제한 콘텐츠(max 600dp) + 하단 고정 CTA를 한 곳에서 그린다.
 * 배경은 background로 채우고, 콘텐츠는 화면이 넓어도 [BcsDimens.contentMax]로 중앙 제한한다.
 * [content]는 남은 세로 공간을 받아 자체적으로 스크롤한다.
 */
@Composable
fun BcsScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable (() -> Unit)? = null,
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable (androidx.compose.foundation.layout.ColumnScope.() -> Unit),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = BcsDimens.contentMax)
                .fillMaxWidth(),
        ) {
            topBar?.invoke()
            content()
            bottomBar?.invoke()
        }
    }
}
