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
import icdf.sndc.ims.ui.common.tr

@Composable
fun AssetDetailScreen(
    viewModel: AssetDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onGuide: (locationName: String?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 每次進入（包含修改後返回）都重新讀取
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text(tr("← 返回清單", "← Back to list")) }
        val asset = state.asset
        when {
            state.loading && asset == null -> CircularProgressIndicator()
            asset == null -> {
                Text(state.error ?: tr("找不到此資產。", "Asset not found."), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::load) { Text(tr("重試", "Retry")) }
            }
            else -> {
                Text(asset.itemName, style = MaterialTheme.typography.headlineSmall)
                if (state.error != null) Text(state.error!!, color = MaterialTheme.colorScheme.error)
                HorizontalDivider()
                InfoRow(tr("編號", "Asset code"), asset.assetCode)
                InfoRow(tr("名稱", "Name"), asset.itemName)
                InfoRow(tr("所屬技術團", "Mission"), asset.techMission)
                InfoRow(tr("購置日期", "Purchased"), AssetFormat.isoToDisplay(asset.purchaseDate))
                InfoRow(tr("存放位置", "Location"), asset.location?.name)
                InfoRow(tr("使用年限", "Useful life"), AssetFormat.years(asset.usefulLifeYears).let { if (it.isEmpty()) null else tr("$it 年", "$it years") })
                InfoRow(tr("預計到期日", "Expires"), AssetFormat.isoToDisplay(asset.expectedExpiryDate).ifEmpty { null }?.let {
                    when (asset.expiryStatus) {
                        "EXPIRED" -> tr("$it（已過使用年限）", "$it (past useful life)")
                        "EXPIRING_SOON" -> tr("$it（90 天內到期）", "$it (within 90 days)")
                        else -> it
                    }
                })
                InfoRow(tr("保管人", "Custodian"), asset.manager?.name)
                InfoRow(tr("數量", "Quantity"), asset.quantity.toString() + (asset.unit?.let { " $it" } ?: ""))
                InfoRow(tr("規格及摘要", "Specification"), asset.specification)
                InfoRow(tr("購置類型", "Acquisition"), asset.acquisitionType)
                InfoRow(tr("價值（美金）", "Value (USD)"), AssetFormat.money(asset.valueUsd).ifEmpty { null })
                InfoRow(tr("價值（台幣）", "Value (TWD)"), AssetFormat.money(asset.valueTwd).ifEmpty { null })
                InfoRow(tr("盤點期別", "Periods"), asset.periods.joinToString("、") { it.label }.ifEmpty { null })
                InfoRow(tr("顏色", "Color"), asset.color)
                InfoRow(tr("備註", "Remarks"), asset.description)
                HorizontalDivider()
                // 室內導引：預留入口，未來從目前位置導引到資產的存放位置
                Button(onClick = { onGuide(asset.location?.name) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("導引", "Guide me"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onEdit) { Text(tr("修改", "Edit")) }
                    OutlinedButton(
                        onClick = viewModel::askDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(tr("刪除", "Delete")) }
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
