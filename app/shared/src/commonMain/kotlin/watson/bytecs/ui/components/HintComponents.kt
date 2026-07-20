package watson.bytecs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * DESIGN_SYSTEM.md §5.5 힌트 — ByteCS의 핵심 컴포넌트.
 *
 * 두 종류를 구분한다.
 *  - **pull**: [HintStepper]. 사용자가 요청해야만 열린다. 약→강 순서로 하나씩.
 *  - **push**: [MisconceptionHintCard]. 흔한 오답을 냈을 때 자동으로 뜬다.
 *
 * ⭐️ 공통 불변식: 힌트는 **정답을 노출하지 않는다**. 전부 info 계열이고 경고색을 쓰지 않는다(§6.1).
 */

/**
 * 힌트 하나. ⭐️ 종류(키워드/배경지식 등)를 타입으로 못박지 않는다 — 힌트 구성은 **문제마다 다르다**(§5.5).
 *
 * @param text 힌트 본문. 정답을 담지 않는다(콘텐츠 계약).
 * @param codeSnippet 코드 예시(있을 때만).
 */
@Immutable
data class BcsHint(
    val text: String,
    val codeSnippet: String? = null,
)

/**
 * §5.5 HintStepper — 약→강 순서로 **하나씩** 공개. 이미 연 힌트는 계속 보인다.
 *
 * ⭐️ 지키는 규칙 셋(테스트로 못박음):
 *  1. **고정 사다리 없음.** 힌트 개수·종류는 문제마다 다르다(0~N개). 강도는 §6.1대로 *순서상 위치*로만
 *     표현한다([hintTone]).
 *  2. **요청 없이 열리지 않는다.** [revealedCount]가 늘기 전에는 다음 힌트를 그리지 않는다 — '더 보기'를
 *     누르지 않고 다음 힌트가 보이면 재생 학습이 무너진다.
 *  3. **힌트가 없는 문제면 진입점을 노출하지 않는다.** 눌러 봐야 아무것도 없는 버튼을 두지 않는다.
 *
 * 공개 상태를 [revealedCount]로 끌어올린 이유: 힌트를 몇 개 열었는지는 화면 장식이 아니라 학습 기록이다
 * (숙련도 반영·재진입 시 복원). 소유는 호출자(뷰모델)에 둔다.
 *
 * @param revealedCount 지금까지 공개된 힌트 수(0 = 아직 진입 전).
 * @param onRevealNext '힌트 보기'/'더 보기' — 다음 힌트 하나를 연다.
 *
 * ⭐️ 디딤 문제 진입 버튼은 디딤이 로드맵으로 감에 따라 제거했다(2026-07-16 오너 결정). 디딤 도입 시
 * HintCard에 진입 버튼과 라벨 필드를 되살린다.
 */
@Composable
fun HintStepper(
    hints: List<BcsHint>,
    revealedCount: Int,
    onRevealNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hints.isEmpty()) return

    val colors = LocalBcsColors.current
    val revealed = revealedCount.coerceIn(0, hints.size)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        for (index in 0 until revealed) {
            HintCard(
                hint = hints[index],
                tone = hintTone(index = index, total = hints.size, colors = colors),
            )
        }
        if (revealed < hints.size) {
            // 진입점과 다음 단계는 같은 액션이다. 둘 다 secondary 톤(화면의 Primary는 '정답 확인하기').
            TextLink(
                text = if (revealed == 0) "힌트 보기" else "더 보기",
                onClick = onRevealNext,
                color = colors.textSecondary,
            )
        }
    }
}

/** 힌트 카드 하나. 강도(톤)는 호출자가 [hintTone]으로 계산해 넘긴다. */
@Composable
private fun HintCard(
    hint: BcsHint,
    tone: BcsTone,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(tone.background)
            // 방금 열린 힌트를 스크린리더가 읽어 준다.
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(BcsDimens.space4),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        Text(text = hint.text, style = MaterialTheme.typography.bodyLarge, color = tone.content)
        hint.codeSnippet?.let { CodeSnippetBlock(code = it) }
    }
}

/**
 * §5.5 MisconceptionHintCard — 흔한 오답을 냈을 때 **자동(push)** 으로 뜨는 오답 교정 힌트.
 *
 * ⭐️ 오답 낙인이 아니다. info 톤이고 danger를 쓰지 않으며, **정답을 노출하지 않는다**(왜 그 답이 다른지만
 * 말하고 다시 도전하게 둔다).
 *
 * pull 힌트([HintStepper])와의 시각적 구별: 이쪽만 전구 글리프 + `primaryBorder` 테두리를 가진다.
 * 내가 열지 않았는데 나타난 카드라는 걸 한눈에 알 수 있어야 하기 때문이다.
 */
@Composable
fun MisconceptionHintCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.infoContainer)
            .border(BcsDimens.borderWidth, colors.primaryBorder, RoundedCornerShape(BcsDimens.radiusCard))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(BcsDimens.space4),
        horizontalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        // 장식이다 — 의미는 인접 본문이 전달한다.
        Icon(
            imageVector = Icons.Rounded.Lightbulb,
            contentDescription = null,
            tint = colors.onInfoContainer,
            modifier = Modifier
                .size(BcsDimens.iconMd)
                .clearAndSetSemantics {},
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = colors.onInfoContainer)
    }
}
