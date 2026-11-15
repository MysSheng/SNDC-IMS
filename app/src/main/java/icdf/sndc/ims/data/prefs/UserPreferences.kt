package icdf.sndc.ims.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

/** 只保存非敏感偏好：上次登入的使用者名稱（規格書第 3.2 節）。不保存 PIN。 */
class UserPreferences(private val context: Context) {

    private val lastUsernameKey = stringPreferencesKey("last_username")

    val lastUsername: Flow<String?> = context.dataStore.data.map { it[lastUsernameKey] }

    suspend fun saveLastUsername(username: String) {
        context.dataStore.edit { it[lastUsernameKey] = username }
    }
}
