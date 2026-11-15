package icdf.sndc.ims.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface AssetApi {
    @GET("assets")
    suspend fun search(
        @Query("keyword") keyword: String?,
        @Query("locationId") locationId: String?,
        @Query("managerId") managerId: String?,
        @Query("expiryStatus") expiryStatus: String?,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort") sort: String = "itemName,asc",
    ): PageDto<AssetDto>

    @GET("assets/{id}")
    suspend fun get(@Path("id") id: String): AssetDto

    @POST("assets")
    suspend fun create(@Body body: AssetRequestDto): AssetDto

    @PUT("assets/{id}")
    suspend fun update(@Path("id") id: String, @Body body: AssetRequestDto): AssetDto

    @DELETE("assets/{id}")
    suspend fun delete(@Path("id") id: String)

    @GET("locations")
    suspend fun locations(): List<LocationDto>

    @POST("locations")
    suspend fun createLocation(@Body body: CreateLocationRequest): LocationDto

    @GET("managers")
    suspend fun managers(): List<ManagerDto>

    @POST("managers")
    suspend fun createManager(@Body body: CreateManagerRequest): ManagerDto
}
