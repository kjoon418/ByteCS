package watson.bytecs

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import watson.bytecs.problem.FakeProblemRepository
import watson.bytecs.problem.ProblemScreen
import watson.bytecs.problem.ProblemViewModel
import watson.bytecs.ui.theme.BcsTheme

@Composable
@Preview
fun App() {
    BcsTheme {
        // 통합 태스크 전까지 인메모리 Fake 저장소로 구동한다(세 피드백 상태 시연 가능).
        val viewModel = viewModel { ProblemViewModel(FakeProblemRepository()) }
        ProblemScreen(viewModel = viewModel)
    }
}
