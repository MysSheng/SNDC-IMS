package icdf.sndc.ims.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF1F3A5F)
private val Teal = Color(0xFF2E7D7A)

private val LightColors = lightColorScheme(primary = Navy, secondary = Teal)
private val DarkColors = darkColorScheme(primary = Color(0xFF9EC3F0), secondary = Color(0xFF8FD3CF))

@Composable
fun SndcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
