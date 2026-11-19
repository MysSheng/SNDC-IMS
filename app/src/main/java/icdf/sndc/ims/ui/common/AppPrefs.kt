package icdf.sndc.ims.ui.common

import android.content.Context
import android.content.SharedPreferences

/** 存在手機上的小設定（例如報銷核算上次使用的計畫名稱）。 */
object AppPrefs {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    fun getString(key: String, default: String): String = prefs?.getString(key, default) ?: default

    fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }
}
