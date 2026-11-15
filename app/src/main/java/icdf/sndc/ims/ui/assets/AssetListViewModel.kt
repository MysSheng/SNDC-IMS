package icdf.sndc.ims.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icdf.sndc.ims.data.AssetRepository
import icdf.sndc.ims.data.remote.ApiResult
import icdf.sndc.ims.data.remote.AssetDto
import icdf.sndc.ims.data.remote.RefDto
import icdf.sndc.ims.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 點選清單項目時要做的事：一般為查看；由 CRUD 選單選「修改」「刪除」時改為直接修改或刪除。 */
enum class PickMode { VIEW, EDIT, DELETE }

/** 使用年限篩選；apiValue 對應後端 expiryStatus 參數。 */
enum class ExpiryFilter(val label: String, val apiValue: String?) {
    ALL("全部", null),
    EXPIRED("已過使用年限", "EXPIRED"),
    EXPIRING_SOON("90 天內到期", "EXPIRING_SOON"),
    VALID("使用年限內", "VALID"),
}

data class AssetListUiState(
    val keyword: String = "",
    val location: RefDto? = null,
    val custodian: RefDto? = null,
    val expiry: ExpiryFilter = ExpiryFilter.ALL,
    val locationOptions: List<RefDto> = emptyList(),
    val custodianOptions: List<RefDto> = emptyList(),
    val items: List<AssetDto> = emptyList(),
    val totalItems: Long = 0,
    val page: Int = 0,
    val totalPages: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val mode: PickMode = PickMode.VIEW,
    val pendingDelete: AssetDto? = null,
    val deleting: Boolean = false,
    val message: String? = null,
) {
    val hasMore: Boolean get() = page + 1 < totalPages
    val hasFilters: Boolean get() = keyword.isNotBlank() || location != null || custodian != null || expiry != ExpiryFilter.ALL

    fun toFilters() = AssetRepository.Filters(keyword, location?.id, custodian?.id, expiry.apiValue)
}

class AssetListViewModel(private val repo: AssetRepository) : ViewModel() {

    private val _state = MutableStateFlow(AssetListUiState())
    val state: StateFlow<AssetListUiState> = _state.asStateFlow()

    fun onKeywordChange(v: String) = _state.update { it.copy(keyword = v) }

    // 選擇篩選條件後立即重新查詢
    fun setLocation(ref: RefDto?) { _state.update { it.copy(location = ref) }; refresh() }
    fun setCustodian(ref: RefDto?) { _state.update { it.copy(custodian = ref) }; refresh() }
    fun setExpiry(f: ExpiryFilter) { _state.update { it.copy(expiry = f) }; refresh() }

    fun clearFilters() {
        _state.update { it.copy(keyword = "", location = null, custodian = null, expiry = ExpiryFilter.ALL) }
        refresh()
    }

    /** 載入「存放位置」「保管人」選項（篩選用）。 */
    fun loadOptions() = viewModelScope.launch {
        val locs = when (val r = repo.locations()) {
            is ApiResult.Success -> r.data.map { RefDto(it.id, it.name) }
            is ApiResult.Failure -> null
        }
        val mgrs = when (val r = repo.managers()) {
            is ApiResult.Success -> r.data.map { RefDto(it.id, it.displayName) }
            is ApiResult.Failure -> null
        }
        _state.update {
            it.copy(
                locationOptions = locs ?: it.locationOptions,
                custodianOptions = mgrs ?: it.custodianOptions,
            )
        }
    }

    /** 重新載入第一頁（進入畫面、搜尋、新增／修改／刪除後）。 */
    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.search(_state.value.toFilters(), page = 0)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, items = r.data.items, page = 0,
                        totalPages = r.data.totalPages, totalItems = r.data.totalItems)
                }
                // 失敗時保留畫面上原有的清單，只顯示錯誤
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.error.toUserMessage()) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val r = repo.search(s.toFilters(), page = s.page + 1)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loadingMore = false, items = it.items + r.data.items, page = r.data.page,
                        totalPages = r.data.totalPages, totalItems = r.data.totalItems)
                }
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false, message = r.error.toUserMessage()) }
            }
        }
    }

    fun setMode(mode: PickMode) = _state.update { it.copy(mode = mode) }

    fun requestDelete(asset: AssetDto) = _state.update { it.copy(pendingDelete = asset, mode = PickMode.VIEW) }

    fun dismissDelete() = _state.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            when (val r = repo.delete(target.id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(deleting = false, pendingDelete = null, message = "已刪除「${target.itemName}」") }
                    refresh()
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(deleting = false, pendingDelete = null, message = r.error.toUserMessage())
                }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
