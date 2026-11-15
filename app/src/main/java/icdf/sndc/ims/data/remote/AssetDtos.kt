package icdf.sndc.ims.data.remote

import kotlinx.serialization.Serializable

/** 資產相關 DTO，對應 docs/API.md。日期在 API 中為 ISO 格式 YYYY-MM-DD。 */

@Serializable
data class RefDto(val id: String, val name: String)

@Serializable
data class AssetDto(
    val id: String,
    val assetCode: String,
    val itemName: String,
    val techMission: String? = null,
    val manager: RefDto? = null,
    val purchaseDate: String? = null,
    val usefulLifeYears: Double? = null,
    val expectedExpiryDate: String? = null,
    /** EXPIRED／EXPIRING_SOON／VALID；缺少購置日期或年限時為 null。 */
    val expiryStatus: String? = null,
    val location: RefDto? = null,
    val color: String? = null,
    val quantity: Int = 1,
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class AssetRequestDto(
    val assetCode: String,
    val itemName: String,
    val techMission: String,
    val managerId: String,
    val purchaseDate: String,
    val usefulLifeYears: Double,
    val locationId: String,
    val color: String?,
    val quantity: Int,
    val description: String?,
)

@Serializable
data class PageDto<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)

@Serializable
data class LocationDto(val id: String, val name: String, val description: String? = null)

@Serializable
data class CreateLocationRequest(val name: String)

@Serializable
data class ManagerDto(val id: String, val displayName: String, val userId: String? = null)

@Serializable
data class CreateManagerRequest(val displayName: String)
