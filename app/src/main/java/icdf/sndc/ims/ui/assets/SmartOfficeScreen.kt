package icdf.sndc.ims.ui.assets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import icdf.sndc.ims.data.remote.AssetDto

/** 智慧辦公室管理系統主畫面：搜尋、清單、CRUD 選單（規格書第 8.3、8.4 節）。 */
@Composable
fun SmartOfficeScreen(
    viewModel: AssetListViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var crudMenu by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var filterPicker by remember { mutableStateOf<String?>(null) }

    // 每次回到此畫面（例如新增、修改後返回）都重新載入，確保顯示資料庫中的最新內容
    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.loadOptions()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 首頁") }
            Text("智慧辦公室管理系統", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Box {
                Button(onClick = { crudMenu = true }) { Text("CRUD") }
                DropdownMenu(expanded = crudMenu, onDismissRequest = { crudMenu = false }) {
                    DropdownMenuItem(text = { Text("新增資產（Create）") }, onClick = { crudMenu = false; onCreate() })
                    DropdownMenuItem(text = { Text("查詢資產（Read）") }, onClick = {
                        crudMenu = false; viewModel.setMode(PickMode.VIEW)
                        Toast.makeText(context, "在上方輸入名稱或編號後按「搜尋」", Toast.LENGTH_SHORT).show()
                    })
                    DropdownMenuItem(text = { Text("修改資產（Update）") }, onClick = { crudMenu = false; viewModel.setMode(PickMode.EDIT) })
                    DropdownMenuItem(text = { Text("刪除資產（Delete）") }, onClick = { crudMenu = false; viewModel.setMode(PickMode.DELETE) })
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                label = { Text("搜尋名稱或編號") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.refresh() }),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = viewModel::refresh) { Text("搜尋") }
        }

        val activeCount = listOf(state.location != null, state.custodian != null, state.expiry != ExpiryFilter.ALL).count { it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showFilters = !showFilters }) {
                Text((if (showFilters) "▴ " else "▾ ") + "篩選條件" + if (activeCount > 0) "（$activeCount）" else "")
            }
            if (state.hasFilters) {
                TextButton(onClick = viewModel::clearFilters) { Text("清除全部條件") }
            }
        }
        if (showFilters) {
            FilterPanel(
                state = state,
                onPickLocation = { filterPicker = "location" },
                onPickCustodian = { filterPicker = "custodian" },
                onExpiry = viewModel::setExpiry,
            )
        }

        if (state.mode != PickMode.VIEW) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.mode == PickMode.EDIT) "請點選要修改的資產" else "請點選要刪除的資產",
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.setMode(PickMode.VIEW) }) { Text("取消") }
                }
            }
        }

        Text("共 ${state.totalItems} 筆紀錄", style = MaterialTheme.typography.bodyMedium)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.items.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.items.isEmpty() ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refresh) { Text("重試") }
                    }
                state.items.isEmpty() ->
                    Text(
                        if (!state.hasFilters) "目前沒有資產紀錄。點右上角「CRUD」→「新增資產」開始建立。" else "找不到符合條件的資產。",
                        modifier = Modifier.align(Alignment.Center),
                    )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.error != null) {
                        item { Text(state.error!!, color = MaterialTheme.colorScheme.error) }
                    }
                    items(state.items, key = { it.id }) { asset ->
                        AssetCard(asset) {
                            when (state.mode) {
                                PickMode.VIEW -> onOpen(asset.id)
                                PickMode.EDIT -> { viewModel.setMode(PickMode.VIEW); onEdit(asset.id) }
                                PickMode.DELETE -> viewModel.requestDelete(asset)
                            }
                        }
                    }
                    if (state.hasMore) {
                        item {
                            TextButton(onClick = viewModel::loadMore, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.loadingMore) "載入中…" else "載入更多")
                            }
                        }
                    }
                }
            }
        }
    }

    when (filterPicker) {
        "location" -> FilterPickerDialog("依存放位置篩選", state.locationOptions, state.location,
            onSelect = { viewModel.setLocation(it); filterPicker = null }, onDismiss = { filterPicker = null })
        "custodian" -> FilterPickerDialog("依保管人篩選", state.custodianOptions, state.custodian,
            onSelect = { viewModel.setCustodian(it); filterPicker = null }, onDismiss = { filterPicker = null })
    }

    state.pendingDelete?.let { target ->
        DeleteConfirmDialog(target, state.deleting, onConfirm = viewModel::confirmDelete, onDismiss = viewModel::dismissDelete)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPanel(
    state: AssetListUiState,
    onPickLocation: () -> Unit,
    onPickCustodian: () -> Unit,
    onExpiry: (ExpiryFilter) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickLocation, modifier = Modifier.weight(1f)) {
                    Text("位置：" + (state.location?.name ?: "全部"), maxLines = 1)
                }
                OutlinedButton(onClick = onPickCustodian, modifier = Modifier.weight(1f)) {
                    Text("保管人：" + (state.custodian?.name ?: "全部"), maxLines = 1)
                }
            }
            Text("使用年限", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpiryFilter.entries.forEach { f ->
                    FilterChip(selected = state.expiry == f, onClick = { onExpiry(f) }, label = { Text(f.label) })
                }
            }
        }
    }
}

@Composable
private fun ExpiryBadge(status: String?) {
    val (text, color) = when (status) {
        "EXPIRED" -> "已過使用年限" to MaterialTheme.colorScheme.error
        "EXPIRING_SOON" -> "90 天內到期" to MaterialTheme.colorScheme.tertiary
        else -> return
    }
    Text(text, color = color, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun AssetCard(asset: AssetDto, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(asset.itemName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("× ${asset.quantity}", style = MaterialTheme.typography.titleMedium)
            }
            Text("編號：${asset.assetCode}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "保管人：${asset.manager?.name ?: "—"}　位置：${asset.location?.name ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!asset.color.isNullOrBlank()) Text("顏色：${asset.color}", style = MaterialTheme.typography.bodySmall)
            ExpiryBadge(asset.expiryStatus)
            if (asset.expectedExpiryDate != null) {
                Text("預計到期：${AssetFormat.isoToDisplay(asset.expectedExpiryDate)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun DeleteConfirmDialog(asset: AssetDto, deleting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("確認刪除") },
        text = { Text("確定要刪除「${asset.itemName}」（編號 ${asset.assetCode}，數量 ${asset.quantity}）嗎？\n刪除後將不再出現在清單與統計中。") },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !deleting) {
                Text(if (deleting) "刪除中…" else "刪除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !deleting) { Text("取消") } },
    )
}
