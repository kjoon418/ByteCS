package watson.bytecs.categoryhistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.ui.components.BcsCard
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.components.categoryLabel
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 카테고리별 학습 이력(도메인 명세 §7, 1차) 목록 화면 — 8개 고정 대분류와 각 카테고리에 푼 문제 수.
 * 스크랩 목록([watson.bytecs.scrap.ScrapListScreen])과 같은 관례를 따른다(전용 시안 없음).
 *
 * ⭐️ 문제가 없는 카테고리도 목록에서 빠지지 않는다 — 푼 문제 수를 '0문제'로 그대로 표시하고(§7 수용
 * 기준), 눌러도 상세 화면에서 같은 톤의 긍정 빈 상태로 이어진다(오류처럼 보이지 않게, UX 가이드 9).
 */
@Composable
fun CategoryHistoryListScreen(
    viewModel: CategoryHistoryListViewModel,
    onOpenCategory: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 화면에 (재)진입할 때마다 목록을 새로 불러온다(스크랩 목록과 같은 관례 — 최신 이력 반영).
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoryHistoryListScreenContent(
        state = state,
        onOpenCategory = onOpenCategory,
        onBack = onBack,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

@Composable
internal fun CategoryHistoryListScreenContent(
    state: CategoryHistoryListUiState,
    onOpenCategory: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
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
                text = "카테고리별 학습 이력",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(BcsDimens.space2))

            when (state) {
                is CategoryHistoryListUiState.Loading -> Text(
                    text = "불러오는 중…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                is CategoryHistoryListUiState.Error -> CategoryHistoryListError(onRetry)

                is CategoryHistoryListUiState.Ready ->
                    for (group in state.groups) {
                        CategoryRow(group = group, onOpenCategory = onOpenCategory)
                    }
            }

            Spacer(Modifier.height(BcsDimens.space6))
        }
    }
}

/**
 * 카테고리 한 줄 — 한글 라벨 + 푼 문제 수. 0개도 '0문제'로 그대로 표시한다(§7 수용 기준, 오류처럼 보이지
 * 않게). 모르는 카테고리 코드는 코드 그대로 라벨로 쓴다(막다른 길 방지 — 서버가 8개를 항상 주므로 실제로는
 * 일어나지 않지만, 방어적으로 화면이 비지 않게 한다).
 */
@Composable
private fun CategoryRow(group: CategoryHistoryGroup, onOpenCategory: (String) -> Unit) {
    val colors = LocalBcsColors.current
    val label = categoryLabel(group.category) ?: group.category
    val count = group.items.size

    BcsCard(onClick = { onOpenCategory(group.category) }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${count}문제",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
    }
}

/** §5.12 로드 실패(시스템 오류) — 막다른 길 금지. 자산 안전 고지 + 재시도. */
@Composable
private fun CategoryHistoryListError(onRetry: () -> Unit) {
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
            text = "학습 이력을 불러오지 못했어요",
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
