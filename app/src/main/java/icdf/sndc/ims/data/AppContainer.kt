package icdf.sndc.ims.data

import android.content.Context
import icdf.sndc.ims.BuildConfig
import icdf.sndc.ims.data.prefs.UserPreferences
import icdf.sndc.ims.data.remote.AgentApi
import icdf.sndc.ims.data.remote.AssetApi
import icdf.sndc.ims.data.remote.AuthApi
import icdf.sndc.ims.data.session.SessionManager
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** 建立網路、偏好設定、Session 等共用物件。 */
class AppContainer(context: Context) {

    val json = Json {
        ignoreUnknownKeys = true   // 後端新增欄位時，舊版 App 不會因此出錯
        explicitNulls = false
    }

    val session = SessionManager()
    val userPreferences = UserPreferences(context.applicationContext)

    /** 有登入時自動加上 Bearer Token；收到 401 時清除 Session，畫面會回到登入頁。 */
    private val authInterceptor = Interceptor { chain ->
        val token = session.token
        val request = if (token != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)
        if (response.code == 401 && token != null) {
            session.clear()
        }
        response
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)   // AI 助理回應可能需要較久
        .addInterceptor(authInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val authRepository = AuthRepository(retrofit.create(AuthApi::class.java), session, userPreferences, json)
    val assetRepository = AssetRepository(retrofit.create(AssetApi::class.java), json)
    val agentRepository = AgentRepository(retrofit.create(AgentApi::class.java), json)
}
