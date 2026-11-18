package icdf.sndc.ims.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
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
        @Query("itemName") itemName: String?,
        @Query("assetCode") assetCode: String?,
        @Query("techMission") techMission: String?,
        @Query("color") color: String?,
        @Query("purchasedFrom") purchasedFrom: String?,
        @Query("purchasedTo") purchasedTo: String?,
        @Query("expiresFrom") expiresFrom: String?,
        @Query("expiresTo") expiresTo: String?,
        @Query("minQuantity") minQuantity: Int?,
        @Query("year") year: Int?,
        @Query("quarter") quarter: Int?,
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

    @GET("periods")
    suspend fun periods(): List<PeriodDto>

    @Multipart
    @POST("assets/import/preview")
    suspend fun importPreview(
        @Part file: MultipartBody.Part,
        @Part("year") year: RequestBody?,
        @Part("quarter") quarter: RequestBody?,
        @Part("techMission") techMission: RequestBody?,
    ): ImportPreviewDto

    @POST("assets/import/{token}/confirm")
    suspend fun importConfirm(@Path("token") token: String): ImportResultDto

    @DELETE("assets/import/{token}")
    suspend fun importDiscard(@Path("token") token: String)

    /** 回傳 .xlsx；檔名在 Content-Disposition。 */
    @POST("assets/export")
    suspend fun export(@Body body: ExportRequestDto): Response<ResponseBody>

    @GET("locations")
    suspend fun locations(): List<LocationDto>

    @POST("locations")
    suspend fun createLocation(@Body body: CreateLocationRequest): LocationDto

    @GET("managers")
    suspend fun managers(): List<ManagerDto>

    @POST("managers")
    suspend fun createManager(@Body body: CreateManagerRequest): ManagerDto
}
