package watson.bytecs.categoryhistory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.ui.components.BcsCard
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.components.categoryLabel
import watson.bytecs.ui.components.difficultyLabel
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 한 카테고리의 학습 이력 상세(도메인 명세 §7, 1차) — 그 카테고리에서 정답으로 통과한 문제 목록을
 * 읽기 전용으로 보여준다(스크랩 재열람과 같은 성격 — 자동 재출제 아님). 스크랩 목록
 * ([watson.bytecs.scrap.ScrapListScreen])과 같은 '목록(질문만)→상세(전체)'의 2단 구조로 통일한다(오너
 * 결정) — 이 화면은 질문만 보여 주는 탭 가능한 목록이고, 문제·모범답안·개념·해설·심화는 한 문제를 눌러
 * 들어가는 상세([CategoryHistoryProblemDetailScreen])에서만 펼친다.
 *
 * ⭐️ 이 카테고리에 푼 문제가 없으면(items 빈 목록) 긍정 빈 상태로 안내한다 — 오류가 아니다
 * (UX 가이드 9 '엣지 케이스에서의 공감').
 */
@Composable
fun CategoryHistoryDetailScreen(
    viewModel: CategoryHistoryDetailViewModel,
    category: String,
    onOpenProblem: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoryHistoryDetailScreenContent(
        category = category,
        state = state,
        onOpenProblem = onOpenProblem,
        onBack = onBack,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
internal fun CategoryHistoryDetailScreenContent(
    category: String,
    state: CategoryHistoryDetailUiState,
    onOpenProblem: (Long) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    val label = categoryLabel(category) ?: category

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
                    text = "뒤로",
                    onClick = onBack,
                    color = colors.textSecondary,
                    contentDescription = "뒤로 가기",
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
            verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
        ) {
            Spacer(Modifier.height(BcsDimens.space2))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(BcsDimens.space2))

            when (state) {
                is CategoryHistoryDetailUiState.Loading -> Text(
                    text = "불러오는 중…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                is CategoryHistoryDetailUiState.Error -> CategoryHistoryDetailError(onRetry)

                is CategoryHistoryDetailUiState.Ready ->
                    if (state.items.isEmpty()) {
                        CategoryHistoryEmptyState(label)
                    } else {
                        for (item in state.items) {
                            CategoryHistoryItemRow(item = item, onOpenProblem = onOpenProblem)
                        }
                    }
            }

            Spacer(Modifier.height(BcsDimens.space6))
        }
    }
}

/**
 * 이력 목록의 한 줄 — 질문만(있으면 난이도 한 줄). 정답을 유추할 정보(모범답안·개념·해설·심화)는 담지 않고,
 * 눌러 들어가는 상세([CategoryHistoryProblemDetailScreen])에서만 펼친다(스크랩 목록과 같은 결).
 */
@Composable
private fun CategoryHistoryItemRow(item: CategoryHistoryItem, onOpenProblem: (Long) -> Unit) {
    val colors = LocalBcsColors.current
    BcsCard(onClick = { onOpenProblem(item.problemId) }) {
        Text(
            text = item.question,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
        )
        difficultyLabel(item.difficulty)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary,
            )
        }
    }
}

/**
 * §5.10 긍정 빈 상태 — 이 카테고리에 아직 푼 문제가 없어도 실패가 아니다(UX 가이드 9).
 * '없음/끝/실패' 대신 0문제 프레이밍으로, 오늘의 한입을 이어가면 곧 만난다는 안내로 이어간다.
 */
@Composable
private fun CategoryHistoryEmptyState(categoryLabel: String) {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BcsDimens.space8),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space2, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(BcsDimens.emptyStateIcon)
                .clip(CircleShape)
                .background(colors.surfaceSubtle)
                .clearAndSetSemantics {},
        )
        Text(
            text = "아직 이 카테고리에서 푼 문제가 없어요",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Text(
            text = "오늘의 한입을 이어가다 보면 $categoryLabel 문제도 곧 만날 수 있어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

/** §5.12 로드 실패(시스템 오류) — 막다른 길 금지. 자산 안전 고지 + 재시도. */
@Composable
private fun CategoryHistoryDetailError(onRetry: () -> Unit) {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(vertical = BcsDimens.space6),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "문제를 불러오지 못했어요",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Text(
            text = "학습 기록은 안전해요. 잠시 후 다시 시도해 주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        PrimaryButton(text = "다시 시도하기", onClick = onRetry)
    }
}
