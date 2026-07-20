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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.CodeSnippetBlock
import watson.bytecs.ui.components.ConceptChips
import watson.bytecs.ui.components.DifficultyIndicator
import watson.bytecs.ui.components.EnrichmentBlock
import watson.bytecs.ui.components.ModelAnswerBlock
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.components.difficultyLabel
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.BcsType
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 카테고리 이력에서 고른 한 문제의 읽기 전용 상세(도메인 명세 §7, 1차 · 레벨3). 스크랩 상세
 * ([watson.bytecs.scrap.ScrapDetailScreen])와 같은 결로 문제·모범답안·개념·해설·심화를 펼쳐 보여준다.
 *
 * ⭐️ 이미 정답으로 통과한 문제라 전부 공개한다. 다만 '내가 쓴 답'(오너 결정으로 이력에서 제거)과 스크랩
 * 토글은 이 맥락에 두지 않는다 — 카테고리 이력은 재열람 성격이되 스크랩과 별개의 진입점이다.
 */
@Composable
fun CategoryHistoryProblemDetailScreen(
    viewModel: CategoryHistoryProblemDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoryHistoryProblemDetailScreenContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
internal fun CategoryHistoryProblemDetailScreenContent(
    state: CategoryHistoryProblemDetailUiState,
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
        when (state) {
            is CategoryHistoryProblemDetailUiState.Loading -> Text(
                text = "불러오는 중…",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space6),
            )

            is CategoryHistoryProblemDetailUiState.Error -> CategoryHistoryProblemDetailError(
                onRetry = onRetry,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5),
            )

            is CategoryHistoryProblemDetailUiState.Ready -> ReadyContent(
                item = state.item,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = BcsDimens.space5),
            )
        }
    }
}

@Composable
private fun ReadyContent(
    item: CategoryHistoryItem,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space4),
    ) {
        Spacer(Modifier.height(BcsDimens.space2))

        // 난이도(있으면).
        difficultyLabel(item.difficulty)?.let { DifficultyIndicator(it) }

        // 문제 질문.
        Text(
            text = item.question,
            style = BcsType.question,
            color = colors.textPrimary,
        )

        item.codeSnippet?.let { CodeSnippetBlock(code = it) }

        // 개념 칩(재열람이므로 공개해도 된다) — 개념 수만큼 렌더.
        ConceptChips(item.concepts)

        // 모범답안 + 해설.
        ModelAnswerBlock(
            representativeAnswer = item.representativeAnswer,
            explanation = item.explanation,
        )

        // '더 알아보기'(§5.7) — 이미 정답 접근이 가능한 맥락이라 바로 보인다.
        EnrichmentBlock(enrichment = item.enrichment)

        Spacer(Modifier.height(BcsDimens.space6))
    }
}

/** §5.12 로드 실패(시스템 오류) — 막다른 길 금지. 자산 안전 고지 + 재시도. */
@Composable
private fun CategoryHistoryProblemDetailError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
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
