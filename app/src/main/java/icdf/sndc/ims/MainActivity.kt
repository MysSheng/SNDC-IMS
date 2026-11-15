package icdf.sndc.ims

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import icdf.sndc.ims.ui.SndcNavHost
import icdf.sndc.ims.ui.theme.SndcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SndcApp).container
        setContent {
            SndcTheme {
                SndcNavHost(container)
            }
        }
    }
}
