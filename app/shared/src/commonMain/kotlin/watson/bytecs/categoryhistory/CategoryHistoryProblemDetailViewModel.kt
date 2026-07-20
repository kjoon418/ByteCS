package watson.bytecs.categoryhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 카테고리 이력에서 고른 한 문제의 읽기 전용 상세(레벨3) 상태 홀더. 카테고리별 조회 엔드포인트가 전체
 * 그룹을 한 번에 돌려주므로([CategoryHistoryRepository.getByCategory]), [category]·[problemId]에 해당하는
 * 항목만 골라 보여준다(스크랩 상세와 같은 성격 — 자동 재출제 아님).
 *
 * 항목을 찾지 못하면(이력이 바뀌어 항목이 사라진 경우) 방어적으로
 * [CategoryHistoryProblemDetailUiState.Error]로 떨어뜨려 막다른 길을 만들지 않는다.
 */
class CategoryHistoryProblemDetailViewModel(
    private val repository: CategoryHistoryRepository,
    private val category: String,
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
            try {
                val groups = repository.getByCategory()
                val item = groups.find { it.category == category }
                    ?.items?.find { it.problemId == problemId }
                _uiState.value = if (item != null) {
                    CategoryHistoryProblemDetailUiState.Ready(item)
                } else {
                    // 이력이 바뀌어 항목이 사라진 경우 — 오류로 떨어뜨려 막다른 길을 만들지 않는다.
                    CategoryHistoryProblemDetailUiState.Error
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.value = CategoryHistoryProblemDetailUiState.Error
            }
        }
    }
}

/** 카테고리 이력의 한 문제 상세(레벨3) 상태. */
sealed interface CategoryHistoryProblemDetailUiState {
    data object Loading : CategoryHistoryProblemDetailUiState
    data class Ready(val item: CategoryHistoryItem) : CategoryHistoryProblemDetailUiState
    data object Error : CategoryHistoryProblemDetailUiState
}
