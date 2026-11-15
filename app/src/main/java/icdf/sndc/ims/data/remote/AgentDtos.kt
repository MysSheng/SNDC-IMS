package icdf.sndc.ims.data.remote

import kotlinx.serialization.Serializable

/** AI 助理 API（docs/API.md「AI」一節）。 */

@Serializable
data class ChatTurnDto(val role: String, val content: String)

@Serializable
data class AgentRequestDto(val message: String, val history: List<ChatTurnDto>)

@Serializable
data class AgentFiltersDto(
    val keyword: String? = null,
    val assetCode: String? = null,
    val itemName: String? = null,
    val techMission: String? = null,
    val color: String? = null,
    val location: RefDto? = null,
    val custodian: RefDto? = null,
    val expiryStatus: String? = null,
    val purchasedFrom: String? = null,
    val purchasedTo: String? = null,
    val expiresFrom: String? = null,
    val expiresTo: String? = null,
    val minQuantity: Int? = null,
)

@Serializable
data class AssetBriefDto(
    val id: String,
    val assetCode: String,
    val itemName: String,
    val quantity: Int = 1,
    val location: String? = null,
    val custodian: String? = null,
    val expectedExpiryDate: String? = null,
)

@Serializable
data class PendingActionDto(
    val id: String,
    val type: String,
    val description: String,
    val targets: List<AssetBriefDto> = emptyList(),
    val changes: Map<String, String> = emptyMap(),
    val expiresAt: String? = null,
)

@Serializable
data class CountSummaryDto(val recordCount: Long, val totalQuantity: Long)

@Serializable
data class DuplicateItemDto(val itemName: String, val totalQuantity: Long, val recordCount: Long)

@Serializable
data class AgentResponseDto(
    val intent: String,
    val reply: String,
    val historyText: String? = null,
    val filters: AgentFiltersDto? = null,
    val results: List<AssetDto>? = null,
    val totalResults: Long? = null,
    val summary: CountSummaryDto? = null,
    val duplicates: List<DuplicateItemDto>? = null,
    val pendingAction: PendingActionDto? = null,
)
