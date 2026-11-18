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
import icdf.sndc.ims.ui.common.tr

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
    const val SPEC = "specification"
    const val ACQUISITION = "acquisitionType"
    const val UNIT = "unit"
    const val TWD = "valueTwd"
    const val USD = "valueUsd"

    /** 必填欄位與畫面名稱（依表單順序），用於「尚未填寫」提醒視窗。 */
    val REQUIRED: Map<String, String> get() = linkedMapOf(
        CODE to tr("編號", "Asset code"),
        NAME to tr("名稱", "Name"),
        MISSION to tr("所屬技術團", "Technical mission"),
        DATE to tr("購置日期", "Purchase date"),
        LOCATION to tr("存放位置", "Location"),
        CUSTODIAN to tr("保管人", "Custodian"),
        QUANTITY to tr("數量", "Quantity"),
    )

    val LABELS: Map<String, String> get() = REQUIRED + mapOf(
        LIFE to tr("使用年限", "Useful life"), SPEC to tr("規格及摘要", "Specification"), ACQUISITION to tr("購置類型", "Acquisition type"), UNIT to tr("單位", "Unit"),
        TWD to tr("價值（台幣）", "Value (TWD)"), USD to tr("價值（美金）", "Value (USD)"), COLOR to tr("顏色", "Color"), NOTE to tr("備註", "Remarks"),
    )
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
    val specification: String = "",
    val acquisitionType: String = "",
    val unit: String = "",
    val valueTwd: String = "",
    val valueUsd: String = "",
    /** 選填：儲存時把資產加入此盤點期別；兩者皆為 null 代表不加入。 */
    val periodYear: Int? = null,
    val periodQuarter: Int? = null,
    /** 修改時顯示：資產目前所屬的期別。 */
    val existingPeriods: List<String> = emptyList(),
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

class AssetFormViewModel(
    private val repo: AssetRepository,
    private val assetId: String?,
    initialYear: Int? = null,
    initialQuarter: Int? = null,
) : ViewModel() {

    // 新增時預設加入清單目前選的盤點期別
    private val _state = MutableStateFlow(
        AssetFormUiState(
            isEdit = assetId != null,
            periodYear = if (assetId == null && initialQuarter != null) initialYear else null,
            periodQuarter = if (assetId == null && initialYear != null) initialQuarter else null,
        ),
    )
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
                    specification = asset.specification.orEmpty(),
                    acquisitionType = asset.acquisitionType.orEmpty(),
                    unit = asset.unit.orEmpty(),
                    valueTwd = AssetFormat.money(asset.valueTwd),
                    valueUsd = AssetFormat.money(asset.valueUsd),
                    existingPeriods = asset.periods.map { it.label },
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
            Field.SPEC -> it.copy(specification = value)
            Field.ACQUISITION -> it.copy(acquisitionType = value)
            Field.UNIT -> it.copy(unit = value)
            Field.TWD -> it.copy(valueTwd = value)
            Field.USD -> it.copy(valueUsd = value)
            else -> it
        }
        n.copy(errors = n.errors - field, message = null)
    }

    /** 由日曆選擇購置日期。 */
    fun selectPurchaseDate(date: java.time.LocalDate) = _state.update {
        it.copy(purchaseDate = AssetFormat.toDisplay(date), errors = it.errors - Field.DATE)
    }

    /** 盤點期別：year 為 null 代表不加入；選年度後預設第 1 季。 */
    fun setPeriodYear(year: Int?) = _state.update {
        it.copy(periodYear = year, periodQuarter = if (year == null) null else (it.periodQuarter ?: 1))
    }

    fun setPeriodQuarter(quarter: Int) = _state.update { it.copy(periodQuarter = quarter) }

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
                it.copy(errors = errors, message = tr("請修正標示紅色的欄位", "Please fix the fields marked in red"),
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
            usefulLifeYears = s.usefulLifeYears.trim().ifEmpty { null }?.toDouble(),
            locationId = s.location!!.id,
            specification = s.specification.trim().ifEmpty { null },
            acquisitionType = s.acquisitionType.trim().ifEmpty { null },
            unit = s.unit.trim().ifEmpty { null },
            quantity = s.quantity.trim().toInt(),
            valueTwd = AssetFormat.parseMoney(s.valueTwd),
            valueUsd = AssetFormat.parseMoney(s.valueUsd),
            color = s.color.trim().ifEmpty { null },
            description = s.description.trim().ifEmpty { null },
            periodYear = s.periodYear?.takeIf { s.periodQuarter != null },
            periodQuarter = s.periodQuarter?.takeIf { s.periodYear != null },
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
        if (s.assetCode.isBlank()) put(Field.CODE, tr("請輸入編號", "Enter an asset code")) else if (s.assetCode.trim().length > 64) put(Field.CODE, tr("編號最多 64 個字", "Asset code: at most 64 characters"))
        if (s.itemName.isBlank()) put(Field.NAME, tr("請輸入名稱", "Enter a name")) else if (s.itemName.trim().length > 200) put(Field.NAME, tr("名稱最多 200 個字", "Name: at most 200 characters"))
        if (s.techMission.isBlank()) put(Field.MISSION, tr("請輸入所屬技術團", "Enter the technical mission")) else if (s.techMission.trim().length > 100) put(Field.MISSION, tr("最多 100 個字", "At most 100 characters"))
        AssetFormat.purchaseDateError(s.purchaseDate)?.let { put(Field.DATE, it) }
        if (s.location == null) put(Field.LOCATION, tr("請選擇存放位置", "Select a location"))
        AssetFormat.usefulLifeError(s.usefulLifeYears)?.let { put(Field.LIFE, it) }
        if (s.custodian == null) put(Field.CUSTODIAN, tr("請選擇保管人", "Select a custodian"))
        AssetFormat.quantityError(s.quantity)?.let { put(Field.QUANTITY, it) }
        if (s.specification.trim().length > 2000) put(Field.SPEC, tr("最多 2000 個字", "At most 2000 characters"))
        if (s.acquisitionType.trim().length > 100) put(Field.ACQUISITION, tr("最多 100 個字", "At most 100 characters"))
        if (s.unit.trim().length > 20) put(Field.UNIT, tr("最多 20 個字", "At most 20 characters"))
        AssetFormat.moneyError(s.valueTwd)?.let { put(Field.TWD, it) }
        AssetFormat.moneyError(s.valueUsd)?.let { put(Field.USD, it) }
        if (s.color.trim().length > 50) put(Field.COLOR, tr("最多 50 個字", "At most 50 characters"))
        if (s.description.trim().length > 2000) put(Field.NOTE, tr("最多 2000 個字", "At most 2000 characters"))
    }

    private fun isMissing(s: AssetFormUiState, field: String): Boolean = when (field) {
        Field.CODE -> s.assetCode.isBlank()
        Field.NAME -> s.itemName.isBlank()
        Field.MISSION -> s.techMission.isBlank()
        Field.DATE -> s.purchaseDate.isBlank()
        Field.LOCATION -> s.location == null
        Field.CUSTODIAN -> s.custodian == null
        Field.QUANTITY -> s.quantity.isBlank()
        else -> false
    }

    private fun serverErrors(e: AppError): Map<String, String> = when {
        e is AppError.Validation && e.code == "ASSET_CODE_DUPLICATE" -> mapOf(Field.CODE to tr("此編號已被使用", "This asset code is already in use"))
        e is AppError.Validation -> e.fieldErrors.mapValues { tr("內容不正確，請檢查", "Invalid value, please check") }
        else -> emptyMap()
    }

    private fun formError(e: AppError): String =
        if (e is AppError.Validation && e.code == "ASSET_CODE_DUPLICATE") tr("此編號已被使用，請換一個編號。", "This asset code is already in use. Please use another one.") else e.toUserMessage()

    private fun locationError(e: AppError): String =
        if (e is AppError.Validation && e.code == "LOCATION_NAME_DUPLICATE") tr("此存放位置已存在。", "This location already exists.") else e.toUserMessage()
}
