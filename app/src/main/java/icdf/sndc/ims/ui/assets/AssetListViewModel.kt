package icdf.sndc.ims.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icdf.sndc.ims.data.AssetRepository
import icdf.sndc.ims.data.remote.ApiResult
import icdf.sndc.ims.data.remote.AgentFiltersDto
import icdf.sndc.ims.data.remote.AssetDto
import icdf.sndc.ims.data.remote.PeriodDto
import icdf.sndc.ims.data.remote.RefDto
import icdf.sndc.ims.data.remote.WriteOffItemDto
import icdf.sndc.ims.ui.common.AppPrefs
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
    val selected: Map<String, AssetDto> = emptyMap(),
    /** 報銷核算的設定與備註編輯（null 代表沒有開啟）。 */
    val writeOff: WriteOffDraft? = null,
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

    fun toggleSelected(asset: AssetDto) = _state.update {
        it.copy(selected = if (asset.id in it.selected) it.selected - asset.id else it.selected + (asset.id to asset))
    }

    /** 勾選目前清單上已載入的全部資產。 */
    fun selectAllLoaded() = _state.update { it.copy(selected = it.selected + it.items.associateBy { a -> a.id }) }

    fun clearSelection() = _state.update { it.copy(selected = emptyMap()) }

    // ---------------- 報銷核算 ----------------

    /** 開啟報銷核算：先檢查備註，詢問是否要編輯。 */
    fun startWriteOff() {
        val s = _state.value
        if (s.selected.isEmpty()) return
        val today = java.time.LocalDate.now()
        val rows = s.selected.values.sortedBy { it.assetCode }.map {
            WriteOffRow(it.id, it.assetCode, it.itemName, it.description, it.description.orEmpty())
        }
        _state.update {
            it.copy(
                writeOff = WriteOffDraft(
                    title = AppPrefs.getString(WriteOffDraft.PREF_TITLE, WriteOffDraft.DEFAULT_TITLE),
                    year = s.year ?: today.year,
                    quarter = if (s.year != null) s.quarter else (today.monthValue - 1) / 3 + 1,
                    rows = rows,
                ),
            )
        }
    }

    private fun updateWriteOff(f: (WriteOffDraft) -> WriteOffDraft) =
        _state.update { s -> s.copy(writeOff = s.writeOff?.let(f)) }

    fun editWriteOffRemarks() = updateWriteOff { it.copy(editing = true) }
    fun setWriteOffTitle(v: String) = updateWriteOff { it.copy(title = v.take(200)) }
    fun setWriteOffYear(y: Int?) = updateWriteOff { it.copy(year = y, quarter = if (y == null) null else it.quarter) }
    fun setWriteOffQuarter(q: Int?) = updateWriteOff { it.copy(quarter = q) }
    fun setWriteOffSaveRemarks(v: Boolean) = updateWriteOff { it.copy(saveRemarks = v) }
    fun setWriteOffBatchText(v: String) = updateWriteOff { it.copy(batchText = v.take(2000)) }
    fun setWriteOffRemark(id: String, v: String) = updateWriteOff { d ->
        d.copy(rows = d.rows.map { if (it.id == id) it.copy(remark = v.take(2000)) else it })
    }

    /** 批量填寫：onlyEmpty = true 只填入目前沒有備註的資產。 */
    fun applyWriteOffBatch(onlyEmpty: Boolean) = updateWriteOff { d ->
        val text = d.batchText.trim()
        if (text.isEmpty()) d else d.copy(rows = d.rows.map {
            if (!onlyEmpty || it.remark.isBlank()) it.copy(remark = text) else it
        })
    }

    fun cancelWriteOff() = _state.update { it.copy(writeOff = null) }

    /** 匯出報銷核算。沒有進入編輯時，沿用資產目前的備註且不寫回。 */
    fun exportWriteOff() {
        val d = _state.value.writeOff ?: return
        if (d.exporting) return
        AppPrefs.putString(WriteOffDraft.PREF_TITLE, d.title.trim())
        updateWriteOff { it.copy(exporting = true) }
        viewModelScope.launch {
            val items = d.rows.map { WriteOffItemDto(it.id, if (d.editing) it.remark.trim().ifEmpty { null } else null) }
            val save = d.editing && d.saveRemarks
            when (val r = repo.exportWriteOff(items, d.title, d.year, d.quarter, save)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(writeOff = null, pendingExport = r.data) }
                    if (save) refresh()
                }
                is ApiResult.Failure -> {
                    updateWriteOff { it.copy(exporting = false) }
                    _state.update { it.copy(message = r.error.toUserMessage()) }
                }
            }
        }
    }

    fun exportSelected() {
        val s = _state.value
        if (s.selected.isEmpty() || s.exporting) return
        _state.update { it.copy(exporting = true) }
        viewModelScope.launch {
            when (val r = repo.export(s.selected.keys.toList(), s.year, s.quarter)) {
                is ApiResult.Success -> _state.update { it.copy(exporting = false, pendingExport = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(exporting = false, message = r.error.toUserMessage()) }
            }
        }
    }

    /** 使用者已選擇存檔位置（saved = true）或取消。 */
    fun exportHandled(saved: Boolean, error: String? = null) = _state.update {
        it.copy(
            pendingExport = null,
            selected = if (saved) emptyMap() else it.selected,
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
            itemNames = f.itemNames?.takeIf { it.isNotEmpty() },
        )
        val labels = buildList {
            f.itemNames?.takeIf { it.isNotEmpty() }?.let { add(tr("名稱：", "name: ") + it.joinToString("、")) }
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

/** 報銷核算的一列：original 為資產目前的備註，remark 為要寫進報銷核算的備註。 */
data class WriteOffRow(
    val id: String,
    val assetCode: String,
    val itemName: String,
    val original: String?,
    val remark: String,
)

data class WriteOffDraft(
    /** 計畫名稱（標題列），會記住上次輸入的內容。 */
    val title: String,
    val year: Int?,
    val quarter: Int?,
    val rows: List<WriteOffRow>,
    /** false：詢問是否編輯備註；true：顯示備註編輯畫面。 */
    val editing: Boolean = false,
    val batchText: String = DEFAULT_REASON,
    /** 把編輯後的備註同時存回資產；原本的備註會移到後面的括號內，例如「報廢原因:…(財國合發字第1號)」。 */
    val saveRemarks: Boolean = true,
    val exporting: Boolean = false,
) {
    val missingRemarks: Int get() = rows.count { it.remark.isBlank() }

    companion object {
        const val PREF_TITLE = "writeOffTitle"
        const val DEFAULT_TITLE = "索馬利蘭政府資料中心與資訊安全能提升計畫"
        const val DEFAULT_REASON = "報廢原因:超過使用年限且損壞。"
    }
}
