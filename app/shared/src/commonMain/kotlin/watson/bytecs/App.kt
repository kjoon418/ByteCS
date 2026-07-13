package watson.bytecs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import watson.bytecs.problem.ProblemRepository
import watson.bytecs.problem.ProblemScreen
import watson.bytecs.problem.ProblemViewModel
import watson.bytecs.problem.data.KtorProblemRepository
import watson.bytecs.problem.data.createProblemHttpClient
import watson.bytecs.ui.theme.BcsTheme

@Composable
@Preview
fun App(
    // 기본은 실제 백엔드에 붙는 Ktor 저장소. 프리뷰·테스트는 Fake를 주입할 수 있다.
    repository: ProblemRepository = rememberDefaultRepository(),
) {
    // 컴포지션 이탈 시 저장소(HTTP 클라이언트) 정리. 자원 없는 구현은 no-op이라 안전하다.
    DisposableEffect(repository) {
        onDispose { repository.close() }
    }
    BcsTheme {
        val viewModel = viewModel { ProblemViewModel(repository) }
        ProblemScreen(viewModel = viewModel)
    }
}

@Composable
private fun rememberDefaultRepository(): ProblemRepository =
    remember { KtorProblemRepository(createProblemHttpClient()) }
