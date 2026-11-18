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
    /** 規格及摘要。 */
    val specification: String? = null,
    /** 購置類型／經費來源。 */
    val acquisitionType: String? = null,
    val unit: String? = null,
    val quantity: Int = 1,
    val valueTwd: Double? = null,
    val valueUsd: Double? = null,
    val color: String? = null,
    val description: String? = null,
    /** 這筆資產出現在哪些盤點期別的清單中（舊到新）。 */
    val periods: List<PeriodRefDto> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class PeriodRefDto(val year: Int, val quarter: Int) {
    val label: String get() = "${year}Q$quarter"
}

/** 使用年限、規格等選填欄位為 null 時不會送出（Json explicitNulls = false）。 */
@Serializable
data class AssetRequestDto(
    val assetCode: String,
    val itemName: String,
    val techMission: String,
    val managerId: String,
    val purchaseDate: String,
    val usefulLifeYears: Double?,
    val locationId: String,
    val specification: String?,
    val acquisitionType: String?,
    val unit: String?,
    val quantity: Int,
    val valueTwd: Double?,
    val valueUsd: Double?,
    val color: String?,
    val description: String?,
    /** 選填；兩者需同時提供，會把資產加入該盤點期別。 */
    val periodYear: Int? = null,
    val periodQuarter: Int? = null,
)

/** GET /periods：所有盤點期別與資產筆數（新到舊）。 */
@Serializable
data class PeriodDto(val year: Int, val quarter: Int, val label: String, val assetCount: Long = 0)

@Serializable
data class ImportErrorDto(val sheet: String? = null, val row: Int? = null, val assetCode: String? = null, val message: String)

/** 匯入預覽：token 為 null 代表有錯誤，不能確認。 */
@Serializable
data class ImportPreviewDto(
    val token: String? = null,
    val fileName: String? = null,
    val year: Int,
    val quarter: Int,
    val period: String,
    val techMission: String? = null,
    val totalRows: Int = 0,
    val createCount: Int = 0,
    val updateCount: Int = 0,
    val restoreCount: Int = 0,
    val newLocations: List<String> = emptyList(),
    val newCustodians: List<String> = emptyList(),
    val errorCount: Int = 0,
    val errors: List<ImportErrorDto> = emptyList(),
)

@Serializable
data class ImportResultDto(val period: String, val created: Int, val updated: Int, val restored: Int)

@Serializable
data class ExportRequestDto(val ids: List<String>, val year: Int? = null, val quarter: Int? = null)

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
