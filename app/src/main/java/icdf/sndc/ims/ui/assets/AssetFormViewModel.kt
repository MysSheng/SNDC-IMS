package icdf.sndc.ims.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icdf.sndc.ims.data.AssetRepository
import icdf.sndc.ims.data.remote.ApiResult
import icdf.sndc.ims.data.remote.AppError
import icdf.sndc.ims.data.remote.AssetRequestDto
import icdf.sndc.ims.data.remote.RefDto
import icdf.sndc.ims.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 表單欄位，鍵值與後端 API 欄位名稱一致，方便顯示伺服器回傳的欄位錯誤。 */
object Field {
    const val CODE = "assetCode"
    const val NAME = "itemName"
    const val MISSION = "techMission"
    const val DATE = "purchaseDate"
    const val LOCATION = "locationId"
    const val LIFE = "usefulLifeYears"
    const val CUSTODIAN = "managerId"
    const val QUANTITY = "quantity"
    const val COLOR = "color"
    const val NOTE = "description"

    /** 必填欄位與畫面名稱（依表單順序），用於「尚未填寫」提醒視窗。 */
    val REQUIRED = linkedMapOf(
        CODE to "編號",
        NAME to "名稱",
        MISSION to "所屬技術團",
        DATE to "購置日期",
        LOCATION to "存放位置",
        LIFE to "使用年限",
        CUSTODIAN to "保管人",
        QUANTITY to "數量",
    )

    val LABELS = REQUIRED + mapOf(COLOR to "顏色", NOTE to "備註")
}

data class AssetFormUiState(
    val isEdit: Boolean = false,
    val assetCode: String = "",
    val itemName: String = "",
    val techMission: String = DEFAULT_TECH_MISSION,
    val purchaseDate: String = "",
    val location: RefDto? = null,
    val usefulLifeYears: String = "",
    val custodian: RefDto? = null,
    val quantity: String = "1",
    val color: String = "",
    val description: String = "",
    val locations: List<RefDto> = emptyList(),
    val custodians: List<RefDto> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val loadError: String? = null,
    val message: String? = null,
    val saved: Boolean = false,
    /** 非 null 時顯示提醒視窗：尚未填寫的必填欄位名稱。 */
    val missingFields: List<String>? = null,
    /** 非 null 時顯示提醒視窗：已填寫但格式不正確的欄位名稱。 */
    val invalidFields: List<String>? = null,
) {
    companion object {
        const val DEFAULT_TECH_MISSION = "駐索馬利蘭技術團"
    }
}

class AssetFormViewModel(private val repo: AssetRepository, private val assetId: String?) : ViewModel() {

    private val _state = MutableStateFlow(AssetFormUiState(isEdit = assetId != null))
    val state: StateFlow<AssetFormUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val locs = when (val r = repo.locations()) {
                is ApiResult.Success -> r.data.map { RefDto(it.id, it.name) }
                is ApiResult.Failure -> return@launch loadFailed(r.error)
            }
            val custodians = when (val r = repo.managers()) {
                is ApiResult.Success -> r.data.map { RefDto(it.id, it.displayName) }
                is ApiResult.Failure -> return@launch loadFailed(r.error)
            }
            val asset = if (assetId == null) null else when (val r = repo.get(assetId)) {
                is ApiResult.Success -> r.data
                is ApiResult.Failure -> return@launch loadFailed(r.error)
            }
            _state.update { s ->
                val base = s.copy(loading = false, locations = locs, custodians = custodians)
                if (asset == null) base else base.copy(
                    assetCode = asset.assetCode,
                    itemName = asset.itemName,
                    // 舊資料沒有所屬技術團時，預先填入預設值
                    techMission = asset.techMission?.takeIf { it.isNotBlank() } ?: AssetFormUiState.DEFAULT_TECH_MISSION,
                    purchaseDate = AssetFormat.isoToDisplay(asset.purchaseDate),
                    location = asset.location,
                    usefulLifeYears = AssetFormat.years(asset.usefulLifeYears),
                    custodian = asset.manager,
                    quantity = asset.quantity.toString(),
                    color = asset.color.orEmpty(),
                    description = asset.description.orEmpty(),
                )
            }
        }
    }

    private fun loadFailed(error: AppError) {
        _state.update { it.copy(loading = false, loadError = error.toUserMessage()) }
    }

    fun update(field: String, value: String) = _state.update {
        val n = when (field) {
            Field.CODE -> it.copy(assetCode = value)
            Field.NAME -> it.copy(itemName = value)
            Field.MISSION -> it.copy(techMission = value)
            Field.DATE -> it.copy(purchaseDate = value)
            Field.LIFE -> it.copy(usefulLifeYears = value)
            Field.QUANTITY -> it.copy(quantity = value.filter { c -> c.isDigit() })
            Field.COLOR -> it.copy(color = value)
            Field.NOTE -> it.copy(description = value)
            else -> it
        }
        n.copy(errors = n.errors - field, message = null)
    }

    /** 由日曆選擇購置日期。 */
    fun selectPurchaseDate(date: java.time.LocalDate) = _state.update {
        it.copy(purchaseDate = AssetFormat.toDisplay(date), errors = it.errors - Field.DATE)
    }

    fun dismissValidationDialog() = _state.update { it.copy(missingFields = null, invalidFields = null) }

    fun selectLocation(ref: RefDto) = _state.update { it.copy(location = ref, errors = it.errors - Field.LOCATION) }
    fun selectCustodian(ref: RefDto) = _state.update { it.copy(custodian = ref, errors = it.errors - Field.CUSTODIAN) }

    /** 在選單中新增存放位置；成功後自動選取。 */
    fun addLocation(name: String) = viewModelScope.launch {
        when (val r = repo.createLocation(name)) {
            is ApiResult.Success -> {
                val ref = RefDto(r.data.id, r.data.name)
                _state.update { it.copy(locations = (it.locations + ref).sortedBy { l -> l.name }, location = ref,
                    errors = it.errors - Field.LOCATION) }
            }
            is ApiResult.Failure -> _state.update { it.copy(message = locationError(r.error)) }
        }
    }

    /** 在選單中新增保管人；成功後自動選取。 */
    fun addCustodian(name: String) = viewModelScope.launch {
        when (val r = repo.createManager(name)) {
            is ApiResult.Success -> {
                val ref = RefDto(r.data.id, r.data.displayName)
                _state.update { it.copy(custodians = (it.custodians + ref).sortedBy { c -> c.name }, custodian = ref,
                    errors = it.errors - Field.CUSTODIAN) }
            }
            is ApiResult.Failure -> _state.update { it.copy(message = r.error.toUserMessage()) }
        }
    }

    fun submit() {
        val s = _state.value
        val errors = validate(s)
        if (errors.isNotEmpty()) {
            // 在送出前就擋下：跳出視窗列出未填寫／格式不正確的欄位，欄位下方也會顯示紅字
            val missing = Field.REQUIRED.filterKeys { isMissing(s, it) }.values.toList()
            val invalid = errors.keys.filterNot { isMissing(s, it) }.mapNotNull { Field.LABELS[it] }
            _state.update {
                it.copy(errors = errors, message = "請修正標示紅色的欄位",
                    missingFields = missing, invalidFields = invalid)
            }
            return
        }
        val body = AssetRequestDto(
            assetCode = s.assetCode.trim(),
            itemName = s.itemName.trim(),
            techMission = s.techMission.trim(),
            managerId = s.custodian!!.id,
            purchaseDate = AssetFormat.toIso(s.purchaseDate),
            usefulLifeYears = s.usefulLifeYears.trim().toDouble(),
            locationId = s.location!!.id,
            color = s.color.trim().ifEmpty { null },
            quantity = s.quantity.trim().toInt(),
            description = s.description.trim().ifEmpty { null },
        )
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val r = if (assetId == null) repo.create(body) else repo.update(assetId, body)
            when (r) {
                is ApiResult.Success -> _state.update { it.copy(saving = false, saved = true) }
                // 失敗時保留所有已輸入內容（規格書第 11.3 節）
                is ApiResult.Failure -> _state.update { it.copy(saving = false, errors = serverErrors(r.error), message = formError(r.error)) }
            }
        }
    }

    private fun validate(s: AssetFormUiState): Map<String, String> = buildMap {
        if (s.assetCode.isBlank()) put(Field.CODE, "請輸入編號") else if (s.assetCode.trim().length > 64) put(Field.CODE, "編號最多 64 個字")
        if (s.itemName.isBlank()) put(Field.NAME, "請輸入名稱") else if (s.itemName.trim().length > 200) put(Field.NAME, "名稱最多 200 個字")
        if (s.techMission.isBlank()) put(Field.MISSION, "請輸入所屬技術團") else if (s.techMission.trim().length > 100) put(Field.MISSION, "最多 100 個字")
        AssetFormat.purchaseDateError(s.purchaseDate)?.let { put(Field.DATE, it) }
        if (s.location == null) put(Field.LOCATION, "請選擇存放位置")
        AssetFormat.usefulLifeError(s.usefulLifeYears)?.let { put(Field.LIFE, it) }
        if (s.custodian == null) put(Field.CUSTODIAN, "請選擇保管人")
        AssetFormat.quantityError(s.quantity)?.let { put(Field.QUANTITY, it) }
        if (s.color.trim().length > 50) put(Field.COLOR, "最多 50 個字")
        if (s.description.trim().length > 2000) put(Field.NOTE, "最多 2000 個字")
    }

    private fun isMissing(s: AssetFormUiState, field: String): Boolean = when (field) {
        Field.CODE -> s.assetCode.isBlank()
        Field.NAME -> s.itemName.isBlank()
        Field.MISSION -> s.techMission.isBlank()
        Field.DATE -> s.purchaseDate.isBlank()
        Field.LOCATION -> s.location == null
        Field.LIFE -> s.usefulLifeYears.isBlank()
        Field.CUSTODIAN -> s.custodian == null
        Field.QUANTITY -> s.quantity.isBlank()
        else -> false
    }

    private fun serverErrors(e: AppError): Map<String, String> = when {
        e is AppError.Validation && e.code == "ASSET_CODE_DUPLICATE" -> mapOf(Field.CODE to "此編號已被使用")
        e is AppError.Validation -> e.fieldErrors.mapValues { "內容不正確，請檢查" }
        else -> emptyMap()
    }

    private fun formError(e: AppError): String =
        if (e is AppError.Validation && e.code == "ASSET_CODE_DUPLICATE") "此編號已被使用，請換一個編號。" else e.toUserMessage()

    private fun locationError(e: AppError): String =
        if (e is AppError.Validation && e.code == "LOCATION_NAME_DUPLICATE") "此存放位置已存在。" else e.toUserMessage()
}
