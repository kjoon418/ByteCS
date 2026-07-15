package watson.bytecs.scrap

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
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 스크랩 목록 화면(기능 5). 전용 시안이 없어 DESIGN_SYSTEM.md와 기존 화면 관례를 따른다.
 *
 * ⭐️ 목록은 **정답을 유추할 정보를 담지 않는다** — 질문만 보여 주고, 모범답안·해설은 재열람([ScrapDetailScreen])에서만.
 * 빈 목록은 실패가 아니라 긍정 빈 상태(§5.10)로 안내한다.
 */
@Composable
fun ScrapListScreen(
    viewModel: ScrapListViewModel,
    onOpenScrap: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 화면에 (재)진입할 때마다 목록을 새로 불러온다 — 재열람에서 스크랩을 해제하고 돌아오면 반영되도록.
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScrapListScreenContent(
        state = state,
        onOpenScrap = onOpenScrap,
        onBack = onBack,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

@Composable
internal fun ScrapListScreenContent(
    state: ScrapListUiState,
    onOpenScrap: (Long) -> Unit,
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
                text = "스크랩한 문제",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(BcsDimens.space2))

            when (state) {
                is ScrapListUiState.Loading -> Text(
                    text = "불러오는 중…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                is ScrapListUiState.Error -> ScrapListError(onRetry)

                is ScrapListUiState.Ready ->
                    if (state.items.isEmpty()) {
                        ScrapEmptyState()
                    } else {
                        for (item in state.items) {
                            val question = item.question
                            if (question == null) {
                                // 회수·삭제된 문제 — 재열람이 불가(서버 상세가 404)하므로 진입점을 열지 않는다.
                                WithdrawnScrapCard(scrappedAt = item.scrappedAt)
                            } else {
                                BcsCard(onClick = { onOpenScrap(item.problemId) }) {
                                    Text(
                                        text = question,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        text = "${item.scrappedAt} 스크랩",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textTertiary,
                                    )
                                }
                            }
                        }
                    }
            }

            Spacer(Modifier.height(BcsDimens.space6))
        }
    }
}

/**
 * 회수·삭제된 스크랩 항목. 문제가 사라져 재열람할 수 없으므로 클릭(진입)을 열지 않고
 * '더 이상 볼 수 없어요'만 담담히 알린다(막다른 길 아님 — 목록의 다른 항목은 그대로 열린다).
 */
@Composable
private fun WithdrawnScrapCard(scrappedAt: String) {
    val colors = LocalBcsColors.current
    // onClick 없음(null) — 진입 차단. BcsCard는 onClick이 null이면 눌리지 않는다.
    BcsCard {
        Text(
            text = "더 이상 볼 수 없어요",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
        Text(
            text = "이 문제는 더 이상 제공되지 않아요. ${scrappedAt} 스크랩",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textTertiary,
        )
    }
}

/** §5.10 긍정 빈 상태 — 스크랩이 없어도 실패가 아니다. 어떻게 채우는지 안내한다. */
@Composable
private fun ScrapEmptyState() {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BcsDimens.space8),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space2, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "아직 스크랩한 문제가 없어요",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Text(
            text = "다시 보고 싶은 문제를 스크랩하면 여기 모여요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

/** §5.12 로드 실패(시스템 오류) — 막다른 길 금지. 자산 안전 고지 + 재시도. */
@Composable
private fun ScrapListError(onRetry: () -> Unit) {
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
            text = "스크랩을 불러오지 못했어요",
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
