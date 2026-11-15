package icdf.sndc.ims.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

interface AgentApi {
    @POST("ai/asset-query")
    suspend fun ask(@Body body: AgentRequestDto): AgentResponseDto

    @POST("ai/actions/{id}/confirm")
    suspend fun confirm(@Path("id") id: String): AgentResponseDto

    @DELETE("ai/actions/{id}")
    suspend fun cancel(@Path("id") id: String)
}
