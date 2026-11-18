package icdf.sndc.ims.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icdf.sndc.ims.data.AssetRepository
import icdf.sndc.ims.data.remote.ApiResult
import icdf.sndc.ims.data.remote.ImportPreviewDto
import icdf.sndc.ims.data.remote.ImportResultDto
import icdf.sndc.ims.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import icdf.sndc.ims.ui.common.tr

/**
 * 匯入主管的財產清單（.xlsx／.xls／.csv）：選檔 → 選盤點期別 → 檢查（預覽）→ 確認寫入。
 * 檢查只讀檔與比對，不會改動資料庫；有任何錯誤就不能確認。
 */
data class AssetImportUiState(
    val fileName: String? = null,
    val bytes: ByteArray? = null,
    val year: Int = LocalDate.now().year,
    val quarter: Int = (LocalDate.now().monthValue - 1) / 3 + 1,
    /** 留空時由伺服器從 Excel 標題判斷（CSV 沒有標題，需要填寫）。 */
    val techMission: String = "",
    val checking: Boolean = false,
    val preview: ImportPreviewDto? = null,
    val confirming: Boolean = false,
    val result: ImportResultDto? = null,
    val error: String? = null,
) {
    val isCsv: Boolean get() = fileName?.lowercase()?.endsWith(".csv") == true
}

class AssetImportViewModel(private val repo: AssetRepository) : ViewModel() {

    private val _state = MutableStateFlow(AssetImportUiState())
    val state: StateFlow<AssetImportUiState> = _state.asStateFlow()

    /** 選好檔案後呼叫；檔名含「2026Q3」時自動帶入年度與季度。 */
    fun fileSelected(name: String, bytes: ByteArray) {
        discardPreview()
        val period = PERIOD.find(name)
        _state.update {
            it.copy(
                fileName = name,
                bytes = bytes,
                year = period?.groupValues?.get(1)?.toInt() ?: it.year,
                quarter = period?.groupValues?.get(2)?.toInt() ?: it.quarter,
                techMission = if (name.lowercase().endsWith(".csv") && it.techMission.isBlank())
                    AssetFormUiState.DEFAULT_TECH_MISSION else it.techMission,
                preview = null, result = null, error = null,
            )
        }
    }

    fun fileTooLarge() = _state.update { it.copy(error = tr("檔案超過 10 MB，請分批匯入。", "The file is larger than 10 MB. Please split it.")) }

    fun fileUnreadable() = _state.update { it.copy(error = tr("無法讀取這個檔案，請重新選擇。", "Could not read this file. Please choose it again.")) }

    // 改變期別或技術團後，原本的檢查結果就不適用，需重新檢查
    fun setYear(y: Int) { discardPreview(); _state.update { it.copy(year = y, preview = null) } }
    fun setQuarter(q: Int) { discardPreview(); _state.update { it.copy(quarter = q, preview = null) } }
    fun setTechMission(v: String) { discardPreview(); _state.update { it.copy(techMission = v, preview = null) } }

    fun check() {
        val s = _state.value
        val bytes = s.bytes ?: return
        _state.update { it.copy(checking = true, error = null, preview = null) }
        viewModelScope.launch {
            when (val r = repo.previewImport(s.fileName ?: "import.xlsx", bytes, s.year, s.quarter, s.techMission)) {
                is ApiResult.Success -> _state.update { it.copy(checking = false, preview = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(checking = false, error = r.error.toUserMessage()) }
            }
        }
    }

    fun confirm() {
        val token = _state.value.preview?.token ?: return
        _state.update { it.copy(confirming = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.confirmImport(token)) {
                is ApiResult.Success -> _state.update { it.copy(confirming = false, result = r.data, preview = null) }
                // 預覽過期（10 分鐘）或其他錯誤：請使用者重新檢查
                is ApiResult.Failure -> _state.update {
                    it.copy(confirming = false, preview = null, error = r.error.toUserMessage() + tr("\n請重新按「檢查檔案」。", "\nPlease tap \"Check file\" again."))
                }
            }
        }
    }

    /** 關閉匯入視窗時呼叫：清掉伺服器上尚未確認的預覽與畫面狀態。 */
    fun reset() {
        discardPreview()
        _state.value = AssetImportUiState()
    }

    private fun discardPreview() {
        val token = _state.value.preview?.token ?: return
        viewModelScope.launch { repo.discardImport(token) }
    }

    companion object {
        private val PERIOD = Regex("(20\\d{2})\\s*[Qq]\\s*([1-4])")
    }
}
