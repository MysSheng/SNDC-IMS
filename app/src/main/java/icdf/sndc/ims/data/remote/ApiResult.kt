package icdf.sndc.ims.data.remote

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** API 呼叫結果；錯誤分為網路、驗證、認證、伺服器四類（規格書第 11.3 節）。 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: AppError) : ApiResult<Nothing>
}

sealed interface AppError {
    /** 無法連線到伺服器。 */
    data object Network : AppError

    /** 400／409 等輸入問題；fieldErrors 以欄位名稱對應訊息。 */
    data class Validation(val code: String, val message: String, val fieldErrors: Map<String, String>) : AppError

    /** 401／403／429。 */
    data class Auth(val status: Int, val code: String, val message: String) : AppError

    /** 503 且附帶錯誤代碼（例如 AI 服務未設定或忙碌）。 */
    data class Unavailable(val code: String, val message: String) : AppError

    /** 5xx 或無法解析的回應。 */
    data class Server(val status: Int) : AppError
}

suspend fun <T> safeApiCall(json: Json, block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        ApiResult.Failure(toAppError(json, e.code(), e.response()?.errorBody()?.string()))
    } catch (e: IOException) {
        ApiResult.Failure(AppError.Network)
    } catch (e: Exception) {
        ApiResult.Failure(AppError.Server(-1))
    }

internal fun toAppError(json: Json, status: Int, body: String?): AppError {
    val dto = body?.let { runCatching { json.decodeFromString(ApiErrorDto.serializer(), it) }.getOrNull() }
    return when {
        status == 503 && dto != null && dto.code.isNotEmpty() -> AppError.Unavailable(dto.code, dto.message)
        status >= 500 || dto == null -> AppError.Server(status)
        status == 401 || status == 403 || status == 429 -> AppError.Auth(status, dto.code, dto.message)
        else -> AppError.Validation(dto.code, dto.message, dto.details.associate { it.field to it.message })
    }
}
