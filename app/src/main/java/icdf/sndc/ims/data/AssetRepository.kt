package icdf.sndc.ims.data

import icdf.sndc.ims.data.remote.ApiResult
import icdf.sndc.ims.data.remote.AssetApi
import icdf.sndc.ims.data.remote.AssetDto
import icdf.sndc.ims.data.remote.AssetRequestDto
import icdf.sndc.ims.data.remote.CreateLocationRequest
import icdf.sndc.ims.data.remote.CreateManagerRequest
import icdf.sndc.ims.data.remote.ExportRequestDto
import icdf.sndc.ims.data.remote.ImportPreviewDto
import icdf.sndc.ims.data.remote.ImportResultDto
import icdf.sndc.ims.data.remote.PeriodDto
import icdf.sndc.ims.data.remote.WriteOffExportRequestDto
import icdf.sndc.ims.data.remote.WriteOffItemDto
import okhttp3.ResponseBody
import retrofit2.Response
import icdf.sndc.ims.data.remote.toAppError
import icdf.sndc.ims.data.remote.LocationDto
import icdf.sndc.ims.data.remote.ManagerDto
import icdf.sndc.ims.data.remote.PageDto
import icdf.sndc.ims.data.remote.safeApiCall
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder

/** 所有資產資料都經由後端 API 存取，App 本身不保存資產資料。 */
class AssetRepository(private val api: AssetApi, private val json: Json) {

    /** 所有條件皆為選填，null 代表不篩選；多個條件會同時套用。 */
    data class Filters(
        val keyword: String? = null,
        val locationId: String? = null,
        val managerId: String? = null,
        val expiryStatus: String? = null,
        // 以下條件目前只由 AI 助理設定
        val itemName: String? = null,
        val assetCode: String? = null,
        val techMission: String? = null,
        val color: String? = null,
        val purchasedFrom: String? = null,
        val purchasedTo: String? = null,
        val expiresFrom: String? = null,
        val expiresTo: String? = null,
        val minQuantity: Int? = null,
        /** 盤點期別：只給 year 為該年度任一季；quarter 需搭配 year。 */
        val year: Int? = null,
        val quarter: Int? = null,
        /** 名稱完全符合其中任一個（由小管家設定）。 */
        val itemNames: List<String>? = null,
    )

    /** 匯出的檔案（尚未存到手機）。 */
    class ExportedFile(val fileName: String, val bytes: ByteArray)

    suspend fun search(filters: Filters, page: Int, size: Int = PAGE_SIZE): ApiResult<PageDto<AssetDto>> =
        safeApiCall(json) {
            api.search(
                keyword = filters.keyword?.trim()?.takeIf { it.isNotEmpty() },
                locationId = filters.locationId,
                managerId = filters.managerId,
                expiryStatus = filters.expiryStatus,
                itemName = filters.itemName,
                assetCode = filters.assetCode,
                techMission = filters.techMission,
                color = filters.color,
                purchasedFrom = filters.purchasedFrom,
                purchasedTo = filters.purchasedTo,
                expiresFrom = filters.expiresFrom,
                expiresTo = filters.expiresTo,
                minQuantity = filters.minQuantity,
                year = filters.year,
                quarter = filters.quarter?.takeIf { filters.year != null },
                itemNames = filters.itemNames?.takeIf { it.isNotEmpty() },
                page = page,
                size = size,
            )
        }

    suspend fun get(id: String): ApiResult<AssetDto> = safeApiCall(json) { api.get(id) }

    suspend fun create(body: AssetRequestDto): ApiResult<AssetDto> = safeApiCall(json) { api.create(body) }

    suspend fun update(id: String, body: AssetRequestDto): ApiResult<AssetDto> =
        safeApiCall(json) { api.update(id, body) }

    suspend fun delete(id: String): ApiResult<Unit> = safeApiCall(json) { api.delete(id) }

    suspend fun locations(): ApiResult<List<LocationDto>> = safeApiCall(json) { api.locations() }

    suspend fun createLocation(name: String): ApiResult<LocationDto> =
        safeApiCall(json) { api.createLocation(CreateLocationRequest(name.trim())) }

    suspend fun managers(): ApiResult<List<ManagerDto>> = safeApiCall(json) { api.managers() }

    suspend fun createManager(name: String): ApiResult<ManagerDto> =
        safeApiCall(json) { api.createManager(CreateManagerRequest(name.trim())) }

    suspend fun periods(): ApiResult<List<PeriodDto>> = safeApiCall(json) { api.periods() }

    /** 上傳 Excel／CSV 並檢查（不寫入）；year／quarter 為 null 時由伺服器從檔名判斷。 */
    suspend fun previewImport(
        fileName: String,
        bytes: ByteArray,
        year: Int?,
        quarter: Int?,
        techMission: String?,
    ): ApiResult<ImportPreviewDto> = safeApiCall(json) {
        val text = "text/plain; charset=utf-8".toMediaType()
        val file = MultipartBody.Part.createFormData(
            "file", fileName, bytes.toRequestBody("application/octet-stream".toMediaType()),
        )
        api.importPreview(
            file = file,
            year = year?.toString()?.toRequestBody(text),
            quarter = quarter?.toString()?.toRequestBody(text),
            techMission = techMission?.trim()?.takeIf { it.isNotEmpty() }?.toRequestBody(text),
        )
    }

    suspend fun confirmImport(token: String): ApiResult<ImportResultDto> = safeApiCall(json) { api.importConfirm(token) }

    suspend fun discardImport(token: String): ApiResult<Unit> = safeApiCall(json) { api.importDiscard(token) }

    /** 匯出勾選的資產為 .xlsx（主管清單格式）；year／quarter 只用於標題與檔名。 */
    suspend fun export(ids: List<String>, year: Int?, quarter: Int?): ApiResult<ExportedFile> =
        toFile(safeApiCall(json) { api.export(ExportRequestDto(ids, year, quarter?.takeIf { year != null })) })

    /** 匯出報銷核算；saveRemarks 時同時把編輯過的備註寫回資產。 */
    suspend fun exportWriteOff(
        items: List<WriteOffItemDto>,
        title: String?,
        year: Int?,
        quarter: Int?,
        saveRemarks: Boolean,
    ): ApiResult<ExportedFile> = toFile(safeApiCall(json) {
        api.exportWriteOff(
            WriteOffExportRequestDto(items, title?.trim()?.ifEmpty { null }, year, quarter?.takeIf { year != null }, saveRemarks),
        )
    })

    private fun toFile(call: ApiResult<Response<ResponseBody>>): ApiResult<ExportedFile> {
        return when (call) {
            is ApiResult.Failure -> call
            is ApiResult.Success -> {
                val response = call.data
                if (!response.isSuccessful) {
                    // 以 Response 接收時 Retrofit 不會丟 HttpException，需自行轉換錯誤
                    ApiResult.Failure(toAppError(json, response.code(), response.errorBody()?.string()))
                } else {
                    // 未標 @Streaming 時 Retrofit 已把內容讀進記憶體
                    val bytes = response.body()?.bytes() ?: ByteArray(0)
                    ApiResult.Success(ExportedFile(fileName(response.headers()["Content-Disposition"]), bytes))
                }
            }
        }
    }

    /** 由 Content-Disposition 取得檔名（優先使用 filename*=UTF-8''…）。 */
    private fun fileName(header: String?): String {
        val fallback = "財產清單.xlsx"
        if (header == null) return fallback
        Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(header)?.let {
            return runCatching { URLDecoder.decode(it.groupValues[1], "UTF-8") }.getOrDefault(fallback)
        }
        Regex("filename=\"([^\"]+)\"").find(header)?.let { return it.groupValues[1] }
        return fallback
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
