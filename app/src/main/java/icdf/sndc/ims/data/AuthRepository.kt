package icdf.sndc.ims.data

import icdf.sndc.ims.data.prefs.UserPreferences
import icdf.sndc.ims.data.remote.ApiResult
import icdf.sndc.ims.data.remote.AuthApi
import icdf.sndc.ims.data.remote.AuthResponse
import icdf.sndc.ims.data.remote.LoginRequest
import icdf.sndc.ims.data.remote.RegisterRequest
import icdf.sndc.ims.data.remote.safeApiCall
import icdf.sndc.ims.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class AuthRepository(
    private val api: AuthApi,
    private val session: SessionManager,
    private val prefs: UserPreferences,
    private val json: Json,
) {
    val lastUsername: Flow<String?> = prefs.lastUsername

    suspend fun login(username: String, pin: String): ApiResult<AuthResponse> =
        safeApiCall(json) { api.login(LoginRequest(username.trim(), pin)) }.also { onAuthenticated(it) }

    suspend fun register(username: String, pin: String, pinConfirm: String, code: String): ApiResult<AuthResponse> =
        safeApiCall(json) { api.register(RegisterRequest(username.trim(), pin, pinConfirm, code.trim())) }
            .also { onAuthenticated(it) }

    fun logout() = session.clear()

    private suspend fun onAuthenticated(result: ApiResult<AuthResponse>) {
        if (result is ApiResult.Success) {
            session.start(result.data.accessToken, result.data.user)
            prefs.saveLastUsername(result.data.user.username)
        }
    }
}
