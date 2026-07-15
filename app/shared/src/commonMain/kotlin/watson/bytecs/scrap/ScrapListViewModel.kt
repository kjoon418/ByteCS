package watson.bytecs.scrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 스크랩 목록 화면의 상태 홀더. 빈 목록은 오류가 아니라 [ScrapListUiState.Ready]의 빈 리스트로 표현해
 * 화면이 긍정 빈 상태(EmptyState)를 그리게 한다.
 */
class ScrapListViewModel(
    private val repository: ScrapRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScrapListUiState>(ScrapListUiState.Loading)
    val uiState: StateFlow<ScrapListUiState> = _uiState.asStateFlow()

    /**
     * 목록을 (다시) 불러온다. 화면 진입·오류 재시도·재열람 후 갱신에 쓰인다.
     * ⭐️ init에서 불러오지 않는 이유: 재열람에서 스크랩을 해제하고 돌아왔을 때 목록이 갱신되도록,
     * 화면이 진입할 때마다([ScrapListScreen]의 LaunchedEffect) 새로 불러온다.
     */
    fun refresh() = load()

    private fun load() {
        _uiState.value = ScrapListUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = ScrapListUiState.Ready(repository.list())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.value = ScrapListUiState.Error
            }
        }
    }
}

/** 스크랩 목록 상태. 빈 목록은 [Ready]의 빈 리스트(EmptyState)로 표현한다. */
sealed interface ScrapListUiState {
    data object Loading : ScrapListUiState
    data class Ready(val items: List<ScrapListItem>) : ScrapListUiState
    data object Error : ScrapListUiState
}
