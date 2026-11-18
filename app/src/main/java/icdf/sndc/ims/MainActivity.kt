package icdf.sndc.ims

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import icdf.sndc.ims.ui.SndcNavHost
import icdf.sndc.ims.ui.common.I18n
import icdf.sndc.ims.ui.theme.SndcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SndcApp).container
        setContent {
            // 讓日曆等系統元件的月份、星期名稱也跟著 App 語言切換
            val base = LocalConfiguration.current
            val language = I18n.language
            val config = remember(base, language) { Configuration(base).apply { setLocale(language.locale) } }
            CompositionLocalProvider(LocalConfiguration provides config) {
                SndcTheme {
                    SndcNavHost(container)
                }
            }
        }
    }
}
