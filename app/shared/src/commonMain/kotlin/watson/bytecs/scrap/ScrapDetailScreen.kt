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
import watson.bytecs.ui.components.EnrichmentBlock
import watson.bytecs.ui.components.ModelAnswerBlock
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.ScrapToggle
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.BcsType
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 스크랩한 문제의 읽기 전용 재열람(기능 5). 전용 시안이 없어 DESIGN_SYSTEM.md와 기존 화면 관례를 따른다.
 *
 * ⭐️ 이미 정답 접근이 가능한 맥락이므로(스크랩 = 지나온 문제) 모범답안·해설·개념을 공개한다 —
 * 미해결 문제의 유출과 달리 학습을 해치지 않는다. 스크랩 토글(해제/재스크랩)도 이 맥락에만 둔다.
 */
@Composable
fun ScrapDetailScreen(
    viewModel: ScrapDetailViewModel,
    onReport: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScrapDetailScreenContent(
        state = state,
        onToggleScrap = viewModel::toggleScrap,
        onReport = onReport,
        onBack = onBack,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
internal fun ScrapDetailScreenContent(
    state: ScrapDetailUiState,
    onToggleScrap: () -> Unit,
    onReport: (Long) -> Unit,
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
                Spacer(Modifier.weight(1f))
                // 스크랩 토글 — 이미 정답 접근이 가능한 재열람 맥락에만 노출한다.
                if (state is ScrapDetailUiState.Ready) {
                    ScrapToggle(scrapped = state.scrapped, onToggle = { onToggleScrap() })
                }
            }
        },
    ) {
        when (state) {
            is ScrapDetailUiState.Loading -> Text(
                text = "불러오는 중…",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space6),
            )

            is ScrapDetailUiState.Error -> ScrapDetailError(
                onRetry = onRetry,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5),
            )

            is ScrapDetailUiState.Ready -> ReadyContent(
                detail = state.detail,
                onReport = onReport,
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
    detail: ScrapDetail,
    onReport: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space4),
    ) {
        Spacer(Modifier.height(BcsDimens.space2))

        // 문제 질문.
        Text(
            text = detail.question,
            style = BcsType.question,
            color = colors.textPrimary,
        )

        detail.codeSnippet?.let { CodeSnippetBlock(code = it) }

        // 개념 칩(재열람이므로 공개해도 된다) — 개념 수만큼 렌더.
        ConceptChips(detail.concepts)

        // 모범답안 + 해설.
        ModelAnswerBlock(
            representativeAnswer = detail.representativeAnswer,
            explanation = detail.explanation,
        )

        // '더 알아보기'(§5.7) — 재열람은 이미 정답 접근이 가능한 맥락이라 바로 보인다.
        EnrichmentBlock(enrichment = detail.enrichment)

        // 콘텐츠 오류 신고 진입점(07).
        TextLink(
            text = "콘텐츠 오류 신고",
            onClick = { onReport(detail.problemId) },
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(BcsDimens.space6))
    }
}

/** §5.12 로드 실패(시스템 오류) — 막다른 길 금지. 자산 안전 고지 + 재시도. */
@Composable
private fun ScrapDetailError(
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
