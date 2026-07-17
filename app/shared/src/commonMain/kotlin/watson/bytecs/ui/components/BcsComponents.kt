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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
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
 *
 * [role]로 §5.13의 파괴적 버튼(계정 삭제)을 만든다. 색을 직접 받지 않는 이유는 [PrimaryButtonRole] 참고.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    role: PrimaryButtonRole = PrimaryButtonRole.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val clickable = enabled && !loading
    val scale by animateFloatAsState(
        targetValue = if (pressed && clickable) BcsMotion.pressScaleStrong else 1f,
        animationSpec = tween(BcsMotion.durFast, easing = BcsMotion.easing),
    )
    val tone = primaryButtonTone(role, LocalBcsColors.current, MaterialTheme.colorScheme)
    val container = when {
        !clickable -> tone.container.copy(alpha = 0.4f)
        pressed -> tone.containerPressed
        else -> tone.container
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
                color = tone.content,
                strokeWidth = BcsDimens.loaderStroke,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = tone.content,
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
 *
 * @param trailing 입력칸 안 우측 슬롯(검증 체크 아이콘·비밀번호 표시 토글 등). 없으면 슬롯 자체를 그리지
 * 않아 기존 사용처(trailing 없이 부르는 곳)는 레이아웃 변화가 없다.
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
    trailing: (@Composable () -> Unit)? = null,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = BcsDimens.inputHeight)
                        .clip(RoundedCornerShape(BcsDimens.radiusCard))
                        .background(colors.surfaceSubtle)
                        .border(BcsDimens.borderWidth, borderColor, RoundedCornerShape(BcsDimens.radiusCard))
                        .padding(horizontal = BcsDimens.space4, vertical = BcsDimens.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textTertiary,
                            )
                        }
                        innerTextField()
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(BcsDimens.space2))
                        trailing()
                    }
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
 * §5.1 SecondaryButton — Primary보다 한 단계 낮은 액션("조금 더 풀기" 등).
 * bg secondaryContainer, 텍스트 onSecondaryContainer, height 56dp.
 *
 * §5.1의 인라인 40dp 변형은 아직 만들지 않았다 — 40dp를 담을 치수 토큰(`buttonHeightInline`)이 없고,
 * 토큰 추가는 이번 작업 범위 밖이다. raw dp를 쓰느니 변형을 빼 두는 편이 낫다(§9 검토 게이트).
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) BcsMotion.pressScale else 1f,
        animationSpec = tween(BcsMotion.durFast, easing = BcsMotion.easing),
    )
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 글자 확대 시 클리핑 방지(§7) — PrimaryButton과 같은 규칙.
            .heightIn(min = BcsDimens.buttonHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(
                if (enabled) colorScheme.secondaryContainer else colorScheme.secondaryContainer.copy(alpha = 0.4f),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = BcsDimens.space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) {
                colorScheme.onSecondaryContainer
            } else {
                colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
            },
        )
    }
}

/**
 * §5.6 넛지 공통 골격 — 좌측 액센트 스트라이프 + 옅은 배경 카드.
 *
 * 회색-on-회색이라 밋밋해지기 쉬운 중립 넛지에 살리언스를 주되, 처벌 신호(빨강·경고 아이콘)는 쓰지 않는다.
 *
 * ⭐️ 색을 낱개 [Color]로 받지 않고 [BcsTone]으로만 받는다. 상태→색 매핑(§6.2)은 무낙인 원칙 그 자체라
 * [retryTone]·[nearMissTone]처럼 **테스트된 매핑 함수 한 곳**을 반드시 지나야 하고, 임의 색을 주입할
 * 구멍을 열어 두면 그 규율이 무의미해지기 때문이다. 같은 이유로 `internal`이다 — 화면은 상태에 맞는
 * 넛지 컴포넌트([RetryNudge]·[NearMissNudge])를 부르지, 골격에 색을 실어 부르지 않는다.
 */
@Composable
internal fun NudgeCard(
    text: String,
    tone: BcsTone,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 스트라이프가 글자 높이를 따라가도록(글자 확대·여러 줄에도 안 깨진다).
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(tone.background),
    ) {
        Box(
            modifier = Modifier
                .width(BcsDimens.accentStripe)
                .fillMaxHeight()
                .background(tone.accent),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = tone.content,
            modifier = Modifier.padding(BcsDimens.space4),
        )
    }
}

/**
 * §5.9 ConceptChip — 개념 태그(알약형 radiusFull).
 * ⭐️ 호출자 책임: **풀기 전에는 부르지 않는다**(정답 스포일 방지). 정답 확인·공개 이후에만 노출한다.
 */
@Composable
fun ConceptChip(
    concept: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    Text(
        text = concept,
        style = MaterialTheme.typography.labelMedium,
        color = colors.onInfoContainer,
        modifier = modifier
            .clip(RoundedCornerShape(BcsDimens.radiusFull))
            .background(colors.infoContainer)
            .padding(horizontal = BcsDimens.space3, vertical = BcsDimens.space1),
    )
}

/**
 * §5.3 BcsCard — bg surface, radius 16, 1dp borderSubtle 테두리.
 * [onClick]을 주면 눌림 스케일이 붙는다. 다크에서는 그림자 대신 테두리로 표면 위계를 준다(§4.3).
 */
@Composable
fun BcsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalBcsColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) BcsMotion.pressScale else 1f,
        animationSpec = tween(BcsMotion.durFast, easing = BcsMotion.easing),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(MaterialTheme.colorScheme.surface)
            .border(BcsDimens.borderWidth, colors.borderSubtle, RoundedCornerShape(BcsDimens.radiusCard))
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(BcsDimens.space5),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
        content = content,
    )
}

/**
 * §5.3 InfoCard — BcsCard의 info 변형(힌트·안내·모범답안). bg primaryContainer, border primaryBorder.
 * ⭐️ 경고가 아니다. 안내·정보 톤이며 danger를 쓰지 않는다(§2.2).
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalBcsColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.infoContainer)
            .border(BcsDimens.borderWidth, colors.primaryBorder, RoundedCornerShape(BcsDimens.radiusCard))
            .padding(BcsDimens.space4),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
        content = content,
    )
}

/**
 * §5.12 Snackbar — 하단 부유 안내. bg textPrimary .95, 텍스트 surface, radius 12, 우측 액션 primary.
 *
 * Material3의 `Snackbar`와 이름이 겹치지 않도록 [BcsSnackbar]로 둔다(같은 파일의 BcsCard·BcsScaffold와 같은 규율).
 * ⭐️ 사용자의 오답은 여기로 보내지 않는다 — 오답은 인라인 넛지(§5.6)가 맡고, 설명이 화면에 남아야 한다.
 */
@Composable
fun BcsSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalBcsColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusChip))
            .background(colors.textPrimary.copy(alpha = 0.95f))
            .padding(horizontal = BcsDimens.space4, vertical = BcsDimens.space3)
            // 떠 있는 안내는 스크린리더가 바로 읽어 준다.
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextLink(text = actionLabel, onClick = onAction)
        }
    }
}

/**
 * §5.12 ErrorBanner — **시스템 오류**(네트워크 등) 안내. "학습 기록은 안전해요"를 먼저 고지하고 재시도 경로를 준다.
 *
 * ⭐️ 두 가지를 지킨다.
 *  - 막다른 길 금지: 항상 재시도 액션이 함께 나간다.
 *  - danger 금지: 시스템 오류도 파괴적 행동이 아니다. 중립 톤으로 안내한다(§2.2 — danger는 계정 삭제 전용).
 *  - 사용자의 **오답은 여기로 오지 않는다**(§5.12) — 오답은 §5.6 RetryNudge 소관.
 */
@Composable
fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "다시 시도하기",
) {
    val colors = LocalBcsColors.current
    val tone = systemErrorTone(colors)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(tone.background)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(BcsDimens.space4),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        Text(
            text = "학습 기록은 안전해요.",
            style = MaterialTheme.typography.labelLarge,
            color = tone.content,
        )
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        GhostButton(text = retryLabel, onClick = onRetry)
    }
}

/**
 * §5.9 DifficultyIndicator — 난이도 라벨(difficulty 색, 은은). ⭐️ 압박 금지: 강조하지 않는다.
 *
 * 이미 매핑된 [label]을 받는다 — "모르는 난이도면 안 그린다"는 판단은 [difficultyLabel]이 null로 표현하고,
 * 호출자는 `difficultyLabel(x)?.let { ... }` 한 번으로 라벨과 주변 간격을 함께 결정한다.
 * (이 컴포넌트가 null을 또 처리하면 같은 계약이 호출부마다 복제된다.)
 */
@Composable
fun DifficultyIndicator(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = LocalBcsColors.current.difficulty,
        modifier = modifier,
    )
}

/** 난이도 코드 → 한글 라벨. 모르는 값이면 null(=표시 안 함). */
internal fun difficultyLabel(difficulty: String?): String? = when (difficulty?.uppercase()) {
    "EASY" -> "쉬움"
    "MEDIUM" -> "보통"
    "HARD" -> "어려움"
    else -> null
}

/**
 * §7 대표 분류 CategoryBadge — 문제의 대표 분류(도메인 명세 §7, 8개 고정 대분류) 배지.
 *
 * ⭐️ [ConceptChip]과 달리 **풀기 전부터** 부를 수 있다 — 카테고리는 대분류라 스포일 위험이 낮다는
 * 오너 판단([결정 2026-07-17], 힌트 리스크 수용 기록). 개념 칩의 info 톤과 다른 중립 톤을 써서, 사용자가
 * 이 배지를 개념(정답 힌트) 유출로 오인하지 않게 시각적으로 구별한다.
 *
 * 호출자 책임: [categoryLabel]이 "모르는/미분류 값이면 null(=표시 안 함)"을 이미 판단하므로,
 * `categoryLabel(category)?.let { CategoryBadge(it) }` 한 번으로 처리한다([DifficultyIndicator]와 같은 계약).
 */
@Composable
fun CategoryBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = colors.textSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(BcsDimens.radiusFull))
            .background(colors.surfaceSubtle)
            .border(BcsDimens.borderWidth, colors.borderSubtle, RoundedCornerShape(BcsDimens.radiusFull))
            .padding(horizontal = BcsDimens.space3, vertical = BcsDimens.space1),
    )
}

/**
 * 카테고리 enum name(서버 [watson.bytecs.problem.domain.ProblemCategory] 대응) → 한글 라벨.
 * 모르는 값(미분류 null 포함)이면 null(=표시 안 함) — [difficultyLabel]과 같은 계약.
 */
internal fun categoryLabel(category: String?): String? = when (category) {
    "DATA_STRUCTURE" -> "자료구조"
    "ALGORITHM" -> "알고리즘"
    "OPERATING_SYSTEM" -> "운영체제"
    "NETWORK" -> "네트워크"
    "DATABASE" -> "데이터베이스"
    "COMPUTER_ARCHITECTURE" -> "컴퓨터구조"
    "SOFTWARE_ENGINEERING" -> "소프트웨어공학"
    "SECURITY" -> "보안"
    else -> null
}

/**
 * §5.16 ScrapToggle — 문제를 개인 북마크에 저장/해제. 켜짐=primary, 꺼짐=textTertiary.
 * 아이콘 폰트 의존을 피해 별 글리프(★/☆)로 그린다. 최소 터치 타깃 48dp를 보장한다(§7).
 */
@Composable
fun ScrapToggle(
    scrapped: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    val label = if (scrapped) "스크랩 해제" else "스크랩"
    Text(
        text = if (scrapped) "★" else "☆",
        style = MaterialTheme.typography.titleMedium,
        color = if (scrapped) MaterialTheme.colorScheme.primary else colors.textTertiary,
        modifier = modifier
            .clip(RoundedCornerShape(BcsDimens.radiusFull))
            .clickable { onToggle(!scrapped) }
            .sizeIn(minWidth = BcsDimens.minTouchTarget, minHeight = BcsDimens.minTouchTarget)
            .wrapContentSize(Alignment.Center)
            // 글리프 자체는 의미를 전달하지 못하므로 상태를 말로 실어 준다.
            .semantics { contentDescription = label },
    )
}

/**
 * §5.16 StreakBadge — 연속 학습 표시(긍정 동기).
 *
 * ⚠️ 끊김([days] == 0)에도 danger·불꽃·죄책감 연출을 쓰지 않는다. 중립 톤 + "다시 시작해요" 초대다.
 * 색 매핑은 [streakTone]에 있고 그 규칙은 테스트로 못박혀 있다.
 */
@Composable
fun StreakBadge(
    days: Int,
    modifier: Modifier = Modifier,
) {
    val tone = streakTone(days, LocalBcsColors.current)
    // 불꽃은 스트릭이 살아 있을 때만. 꺼진 불꽃을 보여 주는 연출은 금지다.
    val label = if (days > 0) "🔥 ${days}일 연속 학습 중" else "오늘 한입으로 연속 학습을 시작해요"
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = tone.content,
        modifier = modifier
            .clip(RoundedCornerShape(BcsDimens.radiusFull))
            .background(tone.background)
            .padding(horizontal = BcsDimens.space3, vertical = BcsDimens.space1)
            .semantics { contentDescription = label },
    )
}

/**
 * §7 BcsScaffold — 상단 바 + 중앙 제한 콘텐츠(max 600dp) + 하단 고정 CTA를 한 곳에서 그린다.
 * 배경은 background로 채우고, 콘텐츠는 화면이 넓어도 [BcsDimens.contentMax]로 중앙 제한한다.
 * [content]는 남은 세로 공간을 받아 자체적으로 스크롤한다.
 *
 * 시스템 바·디스플레이 컷아웃·키보드(IME)는 [WindowInsets.Companion.safeDrawing]로 여기 한 곳에서만 소비한다
 * (개별 화면은 인셋을 신경 쓰지 않는다). 좌우는 바깥 Column에서 전 슬롯 공통으로 소비하고, 상하는
 * topBar/bottomBar가 있으면 그 슬롯이, 없으면 콘텐츠 영역이 대신 소비한다 — 이중 소비 금지.
 * bottomBar의 하단 인셋에는 IME가 포함돼 키보드가 뜨면 CTA가 자동으로 그 위로 올라간다.
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
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            Box(
                modifier = if (topBar != null) {
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                } else {
                    Modifier
                },
            ) {
                topBar?.invoke()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (topBar == null) {
                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (bottomBar == null) {
                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        } else {
                            Modifier
                        },
                    ),
                content = content,
            )
            Box(
                modifier = if (bottomBar != null) {
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                } else {
                    Modifier
                },
            ) {
                bottomBar?.invoke()
            }
        }
    }
}
