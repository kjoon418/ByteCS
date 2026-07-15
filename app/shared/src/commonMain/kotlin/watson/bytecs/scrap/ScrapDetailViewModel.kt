package watson.bytecs.scrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 스크랩 재열람 화면의 상태 홀더. 읽기 전용으로 문제·모범답안·해설을 보여 주고, 스크랩 토글(해제/재스크랩)을 다룬다.
 *
 * ⭐️ 토글은 낙관적으로 반영하고 실패 시 되돌린다 — 재열람 흐름을 네트워크 왕복이 막지 않게 한다.
 */
class ScrapDetailViewModel(
    private val repository: ScrapRepository,
    private val problemId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScrapDetailUiState>(ScrapDetailUiState.Loading)
    val uiState: StateFlow<ScrapDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** 재열람 내용을 (다시) 불러온다. */
    fun retry() = load()

    private fun load() {
        _uiState.value = ScrapDetailUiState.Loading
        viewModelScope.launch {
            try {
                // 재열람으로 들어온 문제는 스크랩된 상태에서 열린다.
                _uiState.value = ScrapDetailUiState.Ready(detail = repository.get(problemId), scrapped = true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.value = ScrapDetailUiState.Error
            }
        }
    }

    /**
     * 스크랩 토글. 낙관적으로 상태를 뒤집고 서버에 반영하되, 실패하면 이전 상태로 되돌린다.
     */
    fun toggleScrap() {
        val current = _uiState.value as? ScrapDetailUiState.Ready ?: return
        val target = !current.scrapped
        _uiState.value = current.copy(scrapped = target)
        viewModelScope.launch {
            try {
                if (target) repository.add(problemId) else repository.remove(problemId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // 실패 시 되돌린다(다른 전이가 없었을 때만).
                _uiState.update { state ->
                    if (state is ScrapDetailUiState.Ready) state.copy(scrapped = current.scrapped) else state
                }
            }
        }
    }
}

/** 스크랩 재열람 상태. */
sealed interface ScrapDetailUiState {
    data object Loading : ScrapDetailUiState
    data class Ready(val detail: ScrapDetail, val scrapped: Boolean) : ScrapDetailUiState
    data object Error : ScrapDetailUiState
}
