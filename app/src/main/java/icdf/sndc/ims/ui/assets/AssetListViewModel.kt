package icdf.sndc.ims.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icdf.sndc.ims.data.AssetRepository
import icdf.sndc.ims.data.remote.ApiResult
import icdf.sndc.ims.data.remote.AgentFiltersDto
import icdf.sndc.ims.data.remote.AssetDto
import icdf.sndc.ims.data.remote.PeriodDto
import icdf.sndc.ims.data.remote.RefDto
import icdf.sndc.ims.ui.common.toUserMessage
import icdf.sndc.ims.ui.common.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 點選清單項目時要做的事：一般為查看；由 CRUD 選單選「修改」「刪除」時改為直接修改或刪除。 */
enum class PickMode { VIEW, EDIT, DELETE }

/** 使用年限篩選；apiValue 對應後端 expiryStatus 參數。 */
enum class ExpiryFilter(private val zh: String, private val en: String, val apiValue: String?) {
    ALL("全部", "All", null),
    EXPIRED("已過使用年限", "Past useful life", "EXPIRED"),
    EXPIRING_SOON("90 天內到期", "Expires within 90 days", "EXPIRING_SOON"),
    VALID("使用年限內", "Within useful life", "VALID"),
    ;

    val label: String get() = tr(zh, en)
}

data class AssetListUiState(
    val keyword: String = "",
    val location: RefDto? = null,
    val custodian: RefDto? = null,
    val expiry: ExpiryFilter = ExpiryFilter.ALL,
    /** 盤點期別：先選年度，再選季度；null 代表「全部」。 */
    val year: Int? = null,
    val quarter: Int? = null,
    val periods: List<PeriodDto> = emptyList(),
    val locationOptions: List<RefDto> = emptyList(),
    val custodianOptions: List<RefDto> = emptyList(),
    /** AI 助理設定、清單畫面沒有對應按鈕的其他條件（名稱、日期範圍等）。 */
    val agentExtra: AssetRepository.Filters? = null,
    val agentExtraLabel: String? = null,
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
    /** 勾選要匯出的資產 ID（換搜尋條件時保留，可跨頁、跨條件勾選）。 */
    val selected: Set<String> = emptySet(),
    val exporting: Boolean = false,
    /** 匯出完成、等待使用者選擇存檔位置的檔案。 */
    val pendingExport: AssetRepository.ExportedFile? = null,
) {
    val hasMore: Boolean get() = page + 1 < totalPages
    val hasFilters: Boolean get() = keyword.isNotBlank() || location != null || custodian != null ||
        expiry != ExpiryFilter.ALL || agentExtra != null || year != null

    /** 年度下拉選單：有資料的年度（新到舊）。 */
    val yearOptions: List<Int> get() = periods.map { it.year }.distinct()

    /** 季度下拉選單：所選年度中有資料的季度。 */
    val quarterOptions: List<Int> get() = periods.filter { it.year == year }.map { it.quarter }.distinct().sorted()

    val periodLabel: String? get() = year?.let { y -> if (quarter == null) tr("$y 年", "$y") else "${y}Q$quarter" }

    fun toFilters() = (agentExtra ?: AssetRepository.Filters()).copy(
        keyword = keyword, locationId = location?.id, managerId = custodian?.id, expiryStatus = expiry.apiValue,
        year = year, quarter = quarter,
    )
}

class AssetListViewModel(private val repo: AssetRepository) : ViewModel() {

    private val _state = MutableStateFlow(AssetListUiState())
    val state: StateFlow<AssetListUiState> = _state.asStateFlow()

    fun onKeywordChange(v: String) = _state.update { it.copy(keyword = v) }

    // 選擇篩選條件後立即重新查詢
    fun setLocation(ref: RefDto?) { _state.update { it.copy(location = ref) }; refresh() }
    fun setCustodian(ref: RefDto?) { _state.update { it.copy(custodian = ref) }; refresh() }
    fun setExpiry(f: ExpiryFilter) { _state.update { it.copy(expiry = f) }; refresh() }

    /** 選年度時清除季度（季度選項依年度而定）。 */
    fun setYear(year: Int?) { _state.update { it.copy(year = year, quarter = null) }; refresh() }
    fun setQuarter(quarter: Int?) { _state.update { it.copy(quarter = quarter) }; refresh() }

    /** 切換到指定期別（例如匯入完成後顯示剛匯入的那一季）。 */
    fun showPeriod(year: Int, quarter: Int) {
        _state.update { it.copy(year = year, quarter = quarter) }
        loadPeriods()
        refresh()
    }

    fun loadPeriods() = viewModelScope.launch {
        when (val r = repo.periods()) {
            is ApiResult.Success -> _state.update { it.copy(periods = r.data) }
            is ApiResult.Failure -> Unit   // 期別選單載入失敗不影響清單
        }
    }

    // ---------------- 勾選與匯出 ----------------

    fun toggleSelected(id: String) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    /** 勾選目前清單上已載入的全部資產。 */
    fun selectAllLoaded() = _state.update { it.copy(selected = it.selected + it.items.map { a -> a.id }) }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun exportSelected() {
        val s = _state.value
        if (s.selected.isEmpty() || s.exporting) return
        _state.update { it.copy(exporting = true) }
        viewModelScope.launch {
            when (val r = repo.export(s.selected.toList(), s.year, s.quarter)) {
                is ApiResult.Success -> _state.update { it.copy(exporting = false, pendingExport = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(exporting = false, message = r.error.toUserMessage()) }
            }
        }
    }

    /** 使用者已選擇存檔位置（saved = true）或取消。 */
    fun exportHandled(saved: Boolean, error: String? = null) = _state.update {
        it.copy(
            pendingExport = null,
            selected = if (saved) emptySet() else it.selected,
            message = error ?: if (saved) tr("已匯出 ${it.selected.size} 筆資產", "Exported ${it.selected.size} assets") else it.message,
        )
    }

    fun clearFilters() {
        _state.update {
            it.copy(keyword = "", location = null, custodian = null, expiry = ExpiryFilter.ALL,
                agentExtra = null, agentExtraLabel = null, year = null, quarter = null)
        }
        refresh()
    }

    /** 套用 AI 助理解析出的條件，讓主清單顯示同樣的結果。 */
    fun applyAgentFilters(f: AgentFiltersDto) {
        val extra = AssetRepository.Filters(
            itemName = f.itemName, assetCode = f.assetCode, techMission = f.techMission, color = f.color,
            purchasedFrom = f.purchasedFrom, purchasedTo = f.purchasedTo,
            expiresFrom = f.expiresFrom, expiresTo = f.expiresTo, minQuantity = f.minQuantity,
        )
        val labels = buildList {
            f.itemName?.let { add(tr("名稱含「$it」", "name contains \"$it\"")) }
            f.assetCode?.let { add(tr("編號 $it", "code $it")) }
            f.techMission?.let { add(tr("技術團：$it", "mission: $it")) }
            f.color?.let { add(tr("顏色：$it", "color: $it")) }
            if (f.purchasedFrom != null || f.purchasedTo != null) add(tr("購置日期 ${range(f.purchasedFrom, f.purchasedTo)}", "purchased ${range(f.purchasedFrom, f.purchasedTo)}"))
            if (f.expiresFrom != null || f.expiresTo != null) add(tr("到期日 ${range(f.expiresFrom, f.expiresTo)}", "expires ${range(f.expiresFrom, f.expiresTo)}"))
            f.minQuantity?.let { add(tr("數量 ≥ $it", "quantity ≥ $it")) }
        }
        _state.update {
            it.copy(
                keyword = f.keyword.orEmpty(),
                location = f.location,
                custodian = f.custodian,
                expiry = ExpiryFilter.entries.firstOrNull { e -> e.apiValue == f.expiryStatus } ?: ExpiryFilter.ALL,
                year = f.year,
                quarter = f.quarter?.takeIf { f.year != null },
                agentExtra = if (labels.isEmpty()) null else extra,
                agentExtraLabel = labels.takeIf { l -> l.isNotEmpty() }?.joinToString("、"),
                mode = PickMode.VIEW,
            )
        }
        refresh()
    }

    private fun range(from: String?, to: String?) =
        "${AssetFormat.isoToDisplay(from)}～${AssetFormat.isoToDisplay(to)}"

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
                    _state.update { it.copy(deleting = false, pendingDelete = null, message = tr("已刪除「${target.itemName}」", "Deleted \"${target.itemName}\"")) }
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
