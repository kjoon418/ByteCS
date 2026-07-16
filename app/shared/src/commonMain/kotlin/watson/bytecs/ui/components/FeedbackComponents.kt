package watson.bytecs.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * DESIGN_SYSTEM.md §5.6 답 피드백 · §5.7 정답 공개 이후 흐름.
 *
 * ⭐️ 이 파일 전체를 관통하는 규칙(§6.2): **정답만 긍정(success), 나머지는 전부 중립·정보 톤.**
 * 불일치·근접·정답 공개 어디에도 danger(빨강)·경고 아이콘·벌점 연출이 없다. 틀림은 '아직'일 뿐이다.
 */

/**
 * §5.6 CorrectFeedback — 정답. success 액센트 + 체크 + (있으면) 개념 칩(들)·해설.
 *
 * 햅틱과 진입 모션은 호출 화면이 붙인다(어느 시점에 울릴지는 화면의 상태 흐름이 안다).
 * [concepts]는 정답 이후에만 들어온다 — 풀기 전 개념 노출은 정답 스포일이다(§5.9). 태깅 순서를 보존한
 * 목록이라 여러 개면 칩을 개수만큼 그린다(줄바꿈은 [FlowRow]).
 */
@Composable
fun CorrectFeedback(
    modifier: Modifier = Modifier,
    concepts: List<String>? = null,
    explanation: String? = null,
) {
    val colors = LocalBcsColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.successContainer)
            .padding(BcsDimens.space4),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckMark(color = colors.success)
            Spacer(Modifier.width(BcsDimens.space2))
            Text(
                text = "맞았어요!",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSuccessContainer,
            )
        }
        if (!concepts.isNullOrEmpty()) {
            ConceptChips(concepts)
        }
        explanation?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.onSuccessContainer)
        }
    }
}

/**
 * §5.2 파생 — 정답 입력란의 **확정 전환**(03 정답 상태 시안 55-60행).
 *
 * [AnswerTextField]를 재사용하지 않고 별도 컴포넌트로 둔다: 시안도 이 상태를 `<input>`이 아니라
 * `<div>`로 그린다 — 정답을 맞힌 뒤에는 더 이상 편집 대상이 아니라 **확정된 기록**이기 때문이다.
 * 편집 불가능한 정적 표시로 바뀌므로, 죽은 입력칸에 엔터를 쳐서 낡은 값이 다시 제출되는 경로도
 * 구조적으로 사라진다(칸이 없으니 누를 것도 없다).
 */
@Composable
fun ConfirmedAnswerField(
    representativeAnswer: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BcsDimens.inputHeight)
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.successContainer)
            .border(BcsDimens.borderWidthStrong, colors.success, RoundedCornerShape(BcsDimens.radiusCard))
            .padding(horizontal = BcsDimens.space4, vertical = BcsDimens.space2)
            .semantics { contentDescription = "$representativeAnswer, 정답으로 확인됐어요" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = representativeAnswer,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSuccessContainer,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(BcsDimens.space3))
        SuccessCheckBadge()
    }
}

/**
 * [ConfirmedAnswerField]의 체크 배지 — success 원 배경 위 흰 체크(§6.2). 장식이므로 시맨틱을 비운다
 * (의미는 위 [ConfirmedAnswerField]의 contentDescription이 이미 전달한다).
 */
@Composable
private fun SuccessCheckBadge(modifier: Modifier = Modifier) {
    val colors = LocalBcsColors.current
    Box(
        modifier = modifier
            .size(BcsDimens.iconCheck + BcsDimens.space2)
            .clip(CircleShape)
            .background(colors.success)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        CheckMark(color = colors.onSuccess)
    }
}

/**
 * 개념 칩 목록 — 태깅 순서대로 렌더, 줄바꿈 필요 시 다음 줄로(FlowRow). 단일 개념일 때는 칩 하나만
 * 그려져 기존 레이아웃과 동일하게 보인다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConceptChips(concepts: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BcsDimens.space2),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space2),
    ) {
        concepts.forEach { ConceptChip(it) }
    }
}

/**
 * §5.6 RetryNudge — 불일치. ⭐️ **중립 넛지**(neutralNudge), 경고가 아니다.
 *
 * 빨강·경고 아이콘 금지, 토스트로 휙 사라지는 것도 금지(설명이 화면에 남아야 한다). 입력은 유지되고
 * 재시도는 무제한이다 — 그 책임은 화면에 있다.
 */
@Composable
fun RetryNudge(modifier: Modifier = Modifier) {
    NudgeCard(
        text = "아직이에요, 다시 해볼까요?",
        tone = retryTone(LocalBcsColors.current),
        modifier = modifier,
    )
}

/**
 * §5.6 NearMissNudge — 근접(오탈자 수준). ⭐️ [RetryNudge]와 **구별되는 톤**이다.
 *
 * 구별의 근거: 두 상황에서 사용자가 할 일이 다르다. 불일치는 "다시 생각해 보세요"(중립 회색),
 * 근접은 "생각은 맞았고 오타만 보세요"(info 파랑)다. 같은 회색으로 뭉뚱그리면 이 정보가 사라진다.
 *
 * ⭐️ 정답·개념을 노출하지 않는다 — 무엇이 틀렸는지 짚어 주지 않고 '오타 때문'이라는 사실만 알린다.
 */
@Composable
fun NearMissNudge(modifier: Modifier = Modifier) {
    NudgeCard(
        text = "거의 맞았어요, 오타를 확인해보세요",
        tone = nearMissTone(LocalBcsColors.current),
        modifier = modifier,
    )
}

/**
 * §5.7 RevealAnswerButton — "정답 보기". ⭐️ **사용자가 명시적으로 요청할 때만** 정답이 열린다.
 * secondary 톤(화면의 Primary는 '정답 확인하기'). 노출 조건(최소 한 번 시도 등)은 화면이 정한다.
 */
@Composable
fun RevealAnswerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextLink(
        text = "정답 보기",
        onClick = onClick,
        color = LocalBcsColors.current.textSecondary,
        modifier = modifier,
    )
}

/**
 * §5.7 ModelAnswerBlock — 모범답안 + 짧은 해설.
 *
 * ⭐️ 벌점처럼 보이게 하지 않는다: info/정보 톤이며 "정답을 봤다"는 낙인 문구·색을 쓰지 않는다.
 * ⭐️ [2026-07-16] 허용답을 나열하지 않는다 — 화면 표시용 [representativeAnswer] 하나만 보여준다
 * (오너 결정: 허용답 집합은 판정용이고, 화면엔 대표 표기 하나가 정직하다).
 */
@Composable
fun ModelAnswerBlock(
    representativeAnswer: String,
    modifier: Modifier = Modifier,
    explanation: String? = null,
    codeSnippet: String? = null,
) {
    val colors = LocalBcsColors.current
    InfoCard(modifier = modifier) {
        Text(
            text = "모범답안",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onInfoContainer,
        )
        Text(
            text = representativeAnswer,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onInfoContainer,
        )
        codeSnippet?.let { CodeSnippetBlock(code = it) }
        explanation?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.onInfoContainer)
        }
    }
}

/**
 * §5.7 TypeAlongField — 정답 공개 후 모범답안을 직접 따라 입력하는 칸.
 *
 * 이 서비스가 진행을 요구하는 **유일한** 지점이다. 톤은 '벌'이 아니라 '손으로 써 보며 익히기'.
 *
 * ⭐️ 정답 문자열을 인자로 받지 않는다 — 받을 이유가 없고(정답은 위 [ModelAnswerBlock]에 이미 있다),
 * 받지 않으면 플레이스홀더·힌트에 정답이 새는 실수가 **구조적으로** 불가능해진다. 일치 판정은 화면이 한다.
 */
@Composable
fun TypeAlongField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onImeSubmit: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space2),
    ) {
        Text(
            text = "정답을 따라 적어 볼까요?",
            style = MaterialTheme.typography.labelLarge,
            color = LocalBcsColors.current.textLabel,
        )
        AnswerTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "위 정답을 따라 적어 보세요",
            onImeSubmit = onImeSubmit,
        )
    }
}

/**
 * §5.7 EnrichmentBlock — '더 알아보기'. 정답 처리 후 그 개념의 추가 정보를 **바로** 보여준다.
 *
 * ⭐️ [결정 2026-07-16] 예전엔 토글로 펼쳐야 보였지만, 확인하려 매번 한 번 더 누르는 마찰을 없애려고
 * 정적 노출로 바꿨다. 두 가지는 그대로 지킨다: [content]가 없으면 아무것도 그리지 않고(빈 껍데기 금지),
 * 진행을 막지 않는다(있든 없든 다음 문제로 갈 수 있다 — 하단 CTA는 이 컴포넌트와 무관하다).
 */
@Composable
fun EnrichmentBlock(
    content: String?,
    modifier: Modifier = Modifier,
) {
    if (content == null) return

    val colors = LocalBcsColors.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space2),
    ) {
        Text(
            text = "더 알아보기",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
        )
        InfoCard {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onInfoContainer,
            )
        }
    }
}

/**
 * 체크 표시. 아이콘 폰트 의존을 피해 Canvas로 그린다(§6 주석).
 * 장식이므로 시맨틱을 비운다 — 의미("맞았어요!")는 인접 텍스트가 전달한다.
 */
@Composable
private fun CheckMark(color: Color) {
    Canvas(
        modifier = Modifier
            .size(BcsDimens.iconCheck)
            .clearAndSetSemantics {},
    ) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.12f
        drawLine(
            color = color,
            start = Offset(w * 0.22f, h * 0.55f),
            end = Offset(w * 0.42f, h * 0.74f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.42f, h * 0.74f),
            end = Offset(w * 0.78f, h * 0.30f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
