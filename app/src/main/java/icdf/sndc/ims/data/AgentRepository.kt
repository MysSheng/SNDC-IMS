package icdf.sndc.ims.data

import icdf.sndc.ims.data.remote.AgentApi
import icdf.sndc.ims.data.remote.AgentRequestDto
import icdf.sndc.ims.data.remote.AgentResponseDto
import icdf.sndc.ims.data.remote.ApiResult
import icdf.sndc.ims.data.remote.ChatTurnDto
import icdf.sndc.ims.data.remote.safeApiCall
import kotlinx.serialization.json.Json

/** AI 助理：自然語言 → 後端驗證後的查詢結果或待確認動作。Groq API Key 只在伺服器端。 */
class AgentRepository(private val api: AgentApi, private val json: Json) {

    suspend fun ask(message: String, history: List<ChatTurnDto>): ApiResult<AgentResponseDto> =
        safeApiCall(json) { api.ask(AgentRequestDto(message.trim(), history.takeLast(MAX_HISTORY))) }

    suspend fun confirm(actionId: String): ApiResult<AgentResponseDto> = safeApiCall(json) { api.confirm(actionId) }

    suspend fun cancel(actionId: String): ApiResult<Unit> = safeApiCall(json) { api.cancel(actionId) }

    companion object {
        const val MAX_HISTORY = 6
        const val MAX_MESSAGE = 500
    }
}
