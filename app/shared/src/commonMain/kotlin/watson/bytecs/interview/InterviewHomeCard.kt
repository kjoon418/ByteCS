package watson.bytecs.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import watson.bytecs.ui.components.BcsCard
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 아직 면접 후보가 없을 때의 안내(빈 상태) 문구 — '어떻게 하면 면접 문제가 생기는지'를 직관적으로 알린다.
 * 회원의 빈 상태(Empty)와, 아직 아무 개념도 익히지 않은 게스트(후보 0)가 **같은 문구**를 본다
 * (게스트에게 "0개를 연습" 같은 김빠지는 안내 대신, 회원이 보는 것과 동일한 미래 지향 안내를 보여준다 — 오너 개선안 2).
 */
private const val INTERVIEW_EMPTY_BODY = "오늘의 한입에서 맞히면 면접 문제가 생겨요"

/**
 * 02 홈 면접 연습 진입 카드(디자인 02 3-b). 오늘의 한입 카드보다 **아래 위계**의 secondary 카드(BcsCard) —
 * 홈의 유일한 Primary는 오늘의 한입 CTA이므로 이 카드의 액션은 Primary가 아니다(계획 §4.3).
 *
 * 서버 `GET /api/interview/status` 응답 하나로 4개 상태를 그대로 반영한다(클라이언트 재판단 금지):
 *  - 게스트(익힌 개념 有) → 가입 유도(누르면 05) / 게스트(0개) → 회원 빈 상태와 같은 담백 안내, 후보 0 → 긍정 빈 상태,
 *    잔여 있음 → 진입 CTA(누르면 08), 소진 → 담백 안내.
 * [InterviewCardUiState.Hidden]이면 아무것도 그리지 않는다(호출자가 렌더 여부를 판단).
 *
 * ⭐️ 무낙인·권유 톤: 게스트에게 강요하지 않고, 소진에도 재촉·아쉬움을 강조하지 않는다.
 *
 * @param onStart 잔여 있음 카드 클릭 → 08 면접 세션.
 * @param onUpgrade 게스트 카드 클릭 → 05 로그인·가입.
 */
@Composable
internal fun InterviewPracticeCard(
    state: InterviewCardUiState,
    onStart: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        InterviewCardUiState.Hidden -> Unit

        is InterviewCardUiState.Guest -> if (state.candidateCount == 0) {
            // 아직 익힌 개념이 없는 게스트 → "0개를 연습"은 김빠지므로, 회원 빈 상태와 똑같이 '어떻게 생기는지'만 담백하게 안내한다.
            InterviewCardShell(
                body = INTERVIEW_EMPTY_BODY,
                onClick = null,
                modifier = modifier,
            )
        } else {
            InterviewCardShell(
                body = "가입하면 지금까지 익힌 개념 ${state.candidateCount}개를 면접처럼 연습할 수 있어요",
                onClick = onUpgrade,
                modifier = modifier,
            )
        }

        InterviewCardUiState.Empty -> InterviewCardShell(
            body = INTERVIEW_EMPTY_BODY,
            onClick = null,
            modifier = modifier,
        )

        is InterviewCardUiState.Ready -> InterviewCardShell(
            body = "익힌 개념 ${state.candidateCount}개, 면접처럼 설명해보기",
            onClick = onStart,
            modifier = modifier,
        )

        InterviewCardUiState.Exhausted -> InterviewCardShell(
            body = "내일 다시 열려요.",
            onClick = null,
            modifier = modifier,
        )
    }
}

/**
 * 카드 공통 골격 — "면접 연습" 라벨 + 안내 본문. 진입 가능한 상태([onClick] != null)면 카드 전체가 눌리고
 * 진입 신호(`›`)를 붙인다(스크랩 진입 행과 같은 관례). 아닌 상태(빈·소진)는 담백한 안내만 둔다.
 */
@Composable
private fun InterviewCardShell(
    body: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    BcsCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BcsDimens.space3),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Chat,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(BcsDimens.iconMd),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BcsDimens.space1),
            ) {
                Text(
                    text = "면접 연습",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
            }
            if (onClick != null) {
                Text(text = "›", style = MaterialTheme.typography.titleMedium, color = colors.textTertiary)
            }
        }
    }
}
