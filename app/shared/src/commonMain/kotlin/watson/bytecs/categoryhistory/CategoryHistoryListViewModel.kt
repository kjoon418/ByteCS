package watson.bytecs.categoryhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 카테고리 목록 화면의 상태 홀더. 8개 카테고리를 서버가 준 순서 그대로 보여준다 — 문제가 없는 카테고리도
 * [CategoryHistoryListUiState.Ready]의 빈 items로 표현해 화면이 '준비 중'(긍정 빈 상태)을 그리게 한다.
 */
class CategoryHistoryListViewModel(
    private val repository: CategoryHistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CategoryHistoryListUiState>(CategoryHistoryListUiState.Loading)
    val uiState: StateFlow<CategoryHistoryListUiState> = _uiState.asStateFlow()

    /** 목록을 (다시) 불러온다. 화면 진입·오류 재시도에 쓰인다(스크랩 목록과 같은 관례). */
    fun refresh() = load()

    private fun load() {
        _uiState.value = CategoryHistoryListUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = CategoryHistoryListUiState.Ready(repository.getByCategory())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.value = CategoryHistoryListUiState.Error
            }
        }
    }
}

/** 카테고리 목록 상태. */
sealed interface CategoryHistoryListUiState {
    data object Loading : CategoryHistoryListUiState
    data class Ready(val groups: List<CategoryHistoryGroup>) : CategoryHistoryListUiState
    data object Error : CategoryHistoryListUiState
}
