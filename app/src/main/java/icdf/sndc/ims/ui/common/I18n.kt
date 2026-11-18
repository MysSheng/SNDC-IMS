package icdf.sndc.ims.ui.common

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale

/** App 介面語言。預設中文；選擇會存在手機上，下次開啟沿用。 */
enum class AppLanguage(val code: String, val locale: Locale) {
    ZH("zh", Locale.TRADITIONAL_CHINESE),
    EN("en", Locale.ENGLISH),
}

/**
 * 介面語言切換（中文／EN）。
 *
 * 文字直接寫在使用處：`tr("登入", "Log in")`。語言存在 Compose 狀態中，切換後畫面立即重繪，
 * 不需要重新開啟 Activity。ViewModel 產生的訊息（例如欄位錯誤）以產生當下的語言為準。
 * 後端回傳的文字（AI 助理回覆、匯入錯誤說明）目前仍為中文。
 */
object I18n {
    private const val PREFS = "settings"
    private const val KEY = "language"

    var language by mutableStateOf(AppLanguage.ZH)
        private set

    private var context: Context? = null

    /** 在 Application.onCreate 呼叫，讀取上次選擇的語言。 */
    fun init(appContext: Context) {
        context = appContext.applicationContext
        val saved = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        language = AppLanguage.entries.firstOrNull { it.code == saved } ?: AppLanguage.ZH
    }

    fun select(lang: AppLanguage) {
        language = lang
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, lang.code)?.apply()
    }
}

/** 依目前語言回傳中文或英文。 */
fun tr(zh: String, en: String): String = if (I18n.language == AppLanguage.EN) en else zh

/** 「中文 | EN」切換按鈕，放在登入頁、註冊頁與首頁右上角。 */
@Composable
fun LanguageToggle(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LanguageOption("中文", AppLanguage.ZH)
        Text("|", style = MaterialTheme.typography.bodyMedium)
        LanguageOption("EN", AppLanguage.EN)
    }
}

@Composable
private fun LanguageOption(label: String, lang: AppLanguage) {
    val selected = I18n.language == lang
    TextButton(onClick = { I18n.select(lang) }) {
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
