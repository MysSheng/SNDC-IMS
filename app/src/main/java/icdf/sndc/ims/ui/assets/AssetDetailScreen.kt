package icdf.sndc.ims.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AssetDetailScreen(viewModel: AssetDetailViewModel, onBack: () -> Unit, onEdit: () -> Unit, onDeleted: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 每次進入（包含修改後返回）都重新讀取
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 返回清單") }
        val asset = state.asset
        when {
            state.loading && asset == null -> CircularProgressIndicator()
            asset == null -> {
                Text(state.error ?: "找不到此資產。", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::load) { Text("重試") }
            }
            else -> {
                Text(asset.itemName, style = MaterialTheme.typography.headlineSmall)
                if (state.error != null) Text(state.error!!, color = MaterialTheme.colorScheme.error)
                HorizontalDivider()
                InfoRow("編號", asset.assetCode)
                InfoRow("名稱", asset.itemName)
                InfoRow("所屬技術團", asset.techMission)
                InfoRow("購置日期", AssetFormat.isoToDisplay(asset.purchaseDate))
                InfoRow("存放位置", asset.location?.name)
                InfoRow("使用年限", AssetFormat.years(asset.usefulLifeYears).let { if (it.isEmpty()) null else "$it 年" })
                InfoRow("預計到期日", AssetFormat.isoToDisplay(asset.expectedExpiryDate).ifEmpty { null }?.let {
                    when (asset.expiryStatus) {
                        "EXPIRED" -> "$it（已過使用年限）"
                        "EXPIRING_SOON" -> "$it（90 天內到期）"
                        else -> it
                    }
                })
                InfoRow("保管人", asset.manager?.name)
                InfoRow("數量", asset.quantity.toString())
                InfoRow("顏色", asset.color)
                InfoRow("備註", asset.description)
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onEdit) { Text("修改") }
                    OutlinedButton(
                        onClick = viewModel::askDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("刪除") }
                }
                if (state.confirmDelete) {
                    DeleteConfirmDialog(asset, state.deleting, onConfirm = viewModel::delete, onDismiss = viewModel::dismissDelete)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp))
        Text(value?.takeIf { it.isNotBlank() } ?: "—", style = MaterialTheme.typography.bodyLarge)
    }
}
