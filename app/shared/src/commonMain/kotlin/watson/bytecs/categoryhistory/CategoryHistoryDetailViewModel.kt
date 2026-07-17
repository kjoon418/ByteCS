package watson.bytecs.categoryhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 한 카테고리의 이력(문제 목록·상세) 상태 홀더. 읽기 전용 — 카테고리별 조회 엔드포인트가 전체 그룹을
 * 한 번에 돌려주므로([CategoryHistoryRepository.getByCategory]), [category]에 해당하는 그룹만 골라 보여준다.
 *
 * 이 카테고리에 푼 문제가 없으면(items 빈 목록) [CategoryHistoryDetailUiState.Ready]의 빈 items로
 * 표현해 화면이 '준비 중'(긍정 빈 상태, UX 가이드 9)을 그리게 한다 — 오류가 아니다.
 */
class CategoryHistoryDetailViewModel(
    private val repository: CategoryHistoryRepository,
    private val category: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CategoryHistoryDetailUiState>(CategoryHistoryDetailUiState.Loading)
    val uiState: StateFlow<CategoryHistoryDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** 이 카테고리의 이력을 (다시) 불러온다. */
    fun retry() = load()

    private fun load() {
        _uiState.value = CategoryHistoryDetailUiState.Loading
        viewModelScope.launch {
            try {
                val groups = repository.getByCategory()
                // 서버가 8개 카테고리를 항상 전부 주므로 정상적으로는 항상 찾지만, 못 찾아도(방어적으로)
                // 빈 목록('준비 중')으로 떨어뜨려 막다른 길을 만들지 않는다.
                val items = groups.find { it.category == category }?.items.orEmpty()
                _uiState.value = CategoryHistoryDetailUiState.Ready(items)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.value = CategoryHistoryDetailUiState.Error
            }
        }
    }
}

/** 카테고리 상세(문제 목록) 상태. */
sealed interface CategoryHistoryDetailUiState {
    data object Loading : CategoryHistoryDetailUiState
    data class Ready(val items: List<CategoryHistoryItem>) : CategoryHistoryDetailUiState
    data object Error : CategoryHistoryDetailUiState
}
