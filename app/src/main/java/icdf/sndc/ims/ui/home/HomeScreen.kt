package icdf.sndc.ims.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import icdf.sndc.ims.ui.common.LanguageToggle
import icdf.sndc.ims.ui.common.tr

/** 規格書第 8.2 節：登入後顯示兩個系統入口。 */
@Composable
fun HomeScreen(username: String, onOpenSmartOffice: () -> Unit, onOpenNavigation: () -> Unit, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("你好，$username", "Hello, $username"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            LanguageToggle()
            TextButton(onClick = onLogout) { Text(tr("登出", "Log out")) }
        }
        EntryCard(tr("智慧辦公室管理系統", "Smart Office"), tr("資產清冊：新增、查詢、修改、刪除", "Asset register: create, search, edit, delete"), onOpenSmartOffice)
        EntryCard(tr("智慧導航系統", "Smart Navigation"), tr("功能尚未開放", "Not available yet"), onOpenNavigation)
    }
}

@Composable
private fun EntryCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** 尚未開發的頁面：清楚說明狀態，不放假資料。 */
@Composable
fun PlaceholderScreen(title: String, message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text(tr("← 返回", "← Back")) }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}
