package watson.bytecs.categoryhistory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 면접 결과의 '그때 푼 문제 다시 보기'(DI10) 화면. 카테고리 문제 상세와 같은 읽기 전용 렌더
 * ([CategoryHistoryProblemDetailScreenContent])를 재사용하되, 데이터 소스만 단건 재열람([ReviewProblemViewModel])이다.
 */
@Composable
fun ReviewProblemScreen(
    viewModel: ReviewProblemViewModel,
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
