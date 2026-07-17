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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import watson.bytecs.ui.components.CodeSnippetBlock
import watson.bytecs.ui.components.ConceptChips
import watson.bytecs.ui.components.DifficultyIndicator
import watson.bytecs.ui.components.EnrichmentBlock
import watson.bytecs.ui.components.ModelAnswerBlock
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.components.categoryLabel
import watson.bytecs.ui.components.difficultyLabel
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.BcsType
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 한 카테고리의 학습 이력 상세(도메인 명세 §7, 1차) — 그 카테고리에서 정답으로 통과한 문제 목록을
 * 읽기 전용으로 보여준다(스크랩 재열람과 같은 성격 — 자동 재출제 아님). 항목마다 이미 API가 문제·모범답안·
 * 개념·해설·심화를 함께 내려주므로([CategoryHistoryItem]), 스크랩처럼 목록→상세 두 번 왕복하지 않고 이
 * 화면 하나에서 카드로 펼쳐 보여준다.
 *
 * ⭐️ 이 카테고리에 푼 문제가 없으면(items 빈 목록) '준비 중' 긍정 빈 상태로 안내한다 — 오류가 아니다
 * (UX 가이드 9 '엣지 케이스에서의 공감').
 */
@Composable
fun CategoryHistoryDetailScreen(
    viewModel: CategoryHistoryDetailViewModel,
    category: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoryHistoryDetailScreenContent(
        category = category,
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
internal fun CategoryHistoryDetailScreenContent(
    category: String,
    state: CategoryHistoryDetailUiState,
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
            verticalArrangement = Arrangement.spacedBy(BcsDimens.space4),
        ) {
            Spacer(Modifier.height(BcsDimens.space2))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )

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
                            CategoryHistoryItemCard(item)
                        }
                    }
            }

            Spacer(Modifier.height(BcsDimens.space6))
        }
    }
}

/** 이력 항목 한 장 — 문제·(있으면) 내가 쓴 답·모범답안·개념·심화. 이미 통과한 문제라 전부 공개한다. */
@Composable
private fun CategoryHistoryItemCard(item: CategoryHistoryItem) {
    val colors = LocalBcsColors.current
    BcsCard {
        difficultyLabel(item.difficulty)?.let { DifficultyIndicator(it) }

        Text(text = item.question, style = BcsType.question, color = colors.textPrimary)

        item.codeSnippet?.let { CodeSnippetBlock(code = it) }

        // ⚠️ 추가 학습에서만 푼 문제는 submittedAnswer가 null이 정상이다(서버 [결정]) — '—'로 대체 표기한다.
        LabeledBlock("내가 쓴 답", item.submittedAnswer ?: "—")

        ConceptChips(item.concepts)

        ModelAnswerBlock(
            representativeAnswer = item.representativeAnswer,
            explanation = item.explanation,
        )

        EnrichmentBlock(enrichment = item.enrichment)
    }
}

@Composable
private fun LabeledBlock(label: String, value: String) {
    val colors = LocalBcsColors.current
    Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space1)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BcsDimens.radiusCard))
                .background(colors.surfaceSubtle)
                .padding(BcsDimens.space3),
        )
    }
}

/**
 * §5.10 긍정 빈 상태 — 이 카테고리에 아직 푼 문제가 없어도 실패가 아니다(UX 가이드 9).
 * '없음/끝/실패' 대신 '준비 중' 톤으로, 다른 카테고리를 둘러보라는 안내로 이어간다.
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
            text = "$categoryLabel 문제를 아직 만나지 않았어요",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Text(
            text = "준비 중이에요. 오늘의 한입을 이어가다 보면 곧 만날 수 있어요.",
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
