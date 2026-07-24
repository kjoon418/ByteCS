package watson.bytecs.categoryhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 면접 결과의 '그때 푼 문제 다시 보기'(DI10) 상태 홀더. 카테고리 상세와 달리 **문제 id 하나만** 알고 진입하므로
 * 전체 카테고리 목록을 훑지 않고 단건 재열람 엔드포인트([CategoryHistoryRepository.getSolvedProblem])로 바로 가져온다
 * (미분류 개념의 문제도 열려야 해 카테고리 그룹핑에 기대지 않는다). 렌더는 카테고리 문제 상세와 같은
 * [CategoryHistoryProblemDetailScreenContent]·[CategoryHistoryProblemDetailUiState]를 재사용한다.
 *
 * 조회 실패(네트워크·404 등)는 막다른 길을 만들지 않도록 [CategoryHistoryProblemDetailUiState.Error]로 떨어뜨린다(재시도 가능).
 */
class ReviewProblemViewModel(
    private val repository: CategoryHistoryRepository,
    private val problemId: Long,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<CategoryHistoryProblemDetailUiState>(CategoryHistoryProblemDetailUiState.Loading)
    val uiState: StateFlow<CategoryHistoryProblemDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** 이 문제의 상세를 (다시) 불러온다. */
    fun retry() = load()

    private fun load() {
        _uiState.value = CategoryHistoryProblemDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                CategoryHistoryProblemDetailUiState.Ready(repository.getSolvedProblem(problemId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                CategoryHistoryProblemDetailUiState.Error
            }
        }
    }
}
