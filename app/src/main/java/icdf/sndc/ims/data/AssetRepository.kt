package icdf.sndc.ims.data

import icdf.sndc.ims.data.remote.ApiResult
import icdf.sndc.ims.data.remote.AssetApi
import icdf.sndc.ims.data.remote.AssetDto
import icdf.sndc.ims.data.remote.AssetRequestDto
import icdf.sndc.ims.data.remote.CreateLocationRequest
import icdf.sndc.ims.data.remote.CreateManagerRequest
import icdf.sndc.ims.data.remote.LocationDto
import icdf.sndc.ims.data.remote.ManagerDto
import icdf.sndc.ims.data.remote.PageDto
import icdf.sndc.ims.data.remote.safeApiCall
import kotlinx.serialization.json.Json

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
    )

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

    companion object {
        const val PAGE_SIZE = 20
    }
}
