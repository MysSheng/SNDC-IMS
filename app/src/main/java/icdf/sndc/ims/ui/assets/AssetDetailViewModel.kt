package icdf.sndc.ims.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icdf.sndc.ims.data.AssetRepository
import icdf.sndc.ims.data.remote.ApiResult
import icdf.sndc.ims.data.remote.AssetDto
import icdf.sndc.ims.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetDetailUiState(
    val asset: AssetDto? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val confirmDelete: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
)

class AssetDetailViewModel(private val repo: AssetRepository, private val id: String) : ViewModel() {

    private val _state = MutableStateFlow(AssetDetailUiState())
    val state: StateFlow<AssetDetailUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.get(id)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, asset = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.error.toUserMessage()) }
            }
        }
    }

    fun askDelete() = _state.update { it.copy(confirmDelete = true) }
    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    fun delete() {
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            when (val r = repo.delete(id)) {
                is ApiResult.Success -> _state.update { it.copy(deleting = false, confirmDelete = false, deleted = true) }
                is ApiResult.Failure -> _state.update {
                    it.copy(deleting = false, confirmDelete = false, error = r.error.toUserMessage())
                }
            }
        }
    }
}
