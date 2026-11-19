package icdf.sndc.ims.ui.assets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import icdf.sndc.ims.ui.agent.AgentAvatar
import icdf.sndc.ims.ui.agent.AgentChatDialog
import icdf.sndc.ims.ui.agent.AgentChatViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.rememberCoroutineScope
import icdf.sndc.ims.data.remote.AssetDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import icdf.sndc.ims.ui.common.LanguageToggle
import icdf.sndc.ims.ui.common.tr

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** 智慧辦公室管理系統主畫面：搜尋、清單、CRUD 選單（規格書第 8.3、8.4 節）。 */
@Composable
fun SmartOfficeScreen(
    viewModel: AssetListViewModel,
    agentViewModel: AgentChatViewModel,
    importViewModel: AssetImportViewModel,
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
    var showAgent by rememberSaveable { mutableStateOf(false) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    val agentState by agentViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 匯出：伺服器產生 .xlsx 後，讓使用者選擇存檔位置（Android 系統的「儲存」畫面）
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        val file = viewModel.state.value.pendingExport
        if (uri == null || file == null) {
            viewModel.exportHandled(saved = false)
        } else {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(file.bytes) } != null
                    }.getOrDefault(false)
                }
                viewModel.exportHandled(saved = ok, error = if (ok) null else tr("無法寫入檔案，請換一個位置再試一次", "Could not save the file. Please choose another location."))
            }
        }
    }
    LaunchedEffect(state.pendingExport) {
        state.pendingExport?.let { saveLauncher.launch(it.fileName) }
    }

    // AI 助理完成修改／刪除後，重新載入清單
    LaunchedEffect(agentState.dataVersion) {
        if (agentState.dataVersion > 0) viewModel.refresh()
    }

    // 每次回到此畫面（例如新增、修改後返回）都重新載入，確保顯示資料庫中的最新內容
    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.loadOptions()
        viewModel.loadPeriods()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(tr("← 首頁", "← Home")) }
            Text(tr("智慧辦公室管理系統", "Smart Office"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            LanguageToggle()
            Box {
                Button(onClick = { crudMenu = true }) { Text("CRUD") }
                DropdownMenu(expanded = crudMenu, onDismissRequest = { crudMenu = false }) {
                    DropdownMenuItem(text = { Text(tr("新增資產（Create）", "Create asset")) }, onClick = { crudMenu = false; onCreate() })
                    DropdownMenuItem(text = { Text(tr("查詢資產（Read）", "Search assets")) }, onClick = {
                        crudMenu = false; viewModel.setMode(PickMode.VIEW)
                        Toast.makeText(context, tr("在上方輸入名稱或編號後按「搜尋」", "Type a name or code above, then tap Search"), Toast.LENGTH_SHORT).show()
                    })
                    DropdownMenuItem(text = { Text(tr("修改資產（Update）", "Edit asset")) }, onClick = { crudMenu = false; viewModel.setMode(PickMode.EDIT) })
                    DropdownMenuItem(text = { Text(tr("刪除資產（Delete）", "Delete asset")) }, onClick = { crudMenu = false; viewModel.setMode(PickMode.DELETE) })
                    DropdownMenuItem(text = { Text(tr("匯入 Excel／CSV", "Import Excel/CSV")) }, onClick = { crudMenu = false; showImport = true })
                }
            }
        }

        // 盤點期別：預設「全部」；先選年度，季度選單才會出現該年度有的季度
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledDropdown(
                text = state.year?.let { tr("$it 年", "$it") } ?: tr("全部年度", "All years"),
                options = listOf<Pair<Int?, String>>(null to tr("全部年度", "All years")) + state.yearOptions.map { it to tr("$it 年", "$it") },
                enabled = true,
                onSelect = viewModel::setYear,
                modifier = Modifier.weight(1f),
            )
            LabeledDropdown(
                text = state.quarter?.let { "Q$it" } ?: tr("全部季度", "All quarters"),
                options = listOf<Pair<Int?, String>>(null to tr("全部季度", "All quarters")) + state.quarterOptions.map { it to tr("Q$it（第 $it 季）", "Q$it") },
                enabled = state.year != null,
                onSelect = viewModel::setQuarter,
                modifier = Modifier.weight(1f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                label = { Text(tr("搜尋名稱、編號或規格", "Search name, code or spec")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.refresh() }),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = viewModel::refresh) { Text(tr("搜尋", "Search")) }
        }

        val activeCount = listOf(state.location != null, state.custodian != null, state.expiry != ExpiryFilter.ALL).count { it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showFilters = !showFilters }) {
                Text((if (showFilters) "▴ " else "▾ ") + tr("篩選條件", "Filters") + if (activeCount > 0) tr("（$activeCount）", " ($activeCount)") else "")
            }
            if (state.hasFilters) {
                TextButton(onClick = viewModel::clearFilters) { Text(tr("清除全部條件", "Clear all")) }
            }
        }
        state.agentExtraLabel?.let { label ->
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("小管家設定的條件：$label", "Set by assistant: $label"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = viewModel::clearFilters) { Text(tr("清除", "Clear")) }
                }
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
                        if (state.mode == PickMode.EDIT) tr("請點選要修改的資產", "Tap the asset to edit") else tr("請點選要刪除的資產", "Tap the asset to delete"),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.setMode(PickMode.VIEW) }) { Text(tr("取消", "Cancel")) }
                }
            }
        }

        if (state.selected.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("已選 ${state.selected.size} 筆", "${state.selected.size} selected"), modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::selectAllLoaded) { Text(tr("全選", "Select all")) }
                    TextButton(onClick = viewModel::clearSelection) { Text(tr("清除", "Clear")) }
                    Box {
                        var exportMenu by remember { mutableStateOf(false) }
                        Button(onClick = { exportMenu = true }, enabled = !state.exporting) {
                            Text(if (state.exporting) tr("匯出中…", "Exporting…") else tr("匯出 ▾", "Export ▾"))
                        }
                        DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("財產清單（主管格式）", "Asset list (supervisor format)")) },
                                onClick = { exportMenu = false; viewModel.exportSelected() },
                            )
                            DropdownMenuItem(
                                text = { Text(tr("報銷核算", "Write-off list (報銷核算)")) },
                                onClick = { exportMenu = false; viewModel.startWriteOff() },
                            )
                        }
                    }
                }
            }
        }

        Text(
            (state.periodLabel?.let { tr("$it：", "$it: ") } ?: "") + tr("共 ${state.totalItems} 筆紀錄", "${state.totalItems} records"),
            style = MaterialTheme.typography.bodyMedium,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.items.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.items.isEmpty() ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refresh) { Text(tr("重試", "Retry")) }
                    }
                state.items.isEmpty() ->
                    Text(
                        if (!state.hasFilters) tr("目前沒有資產紀錄。點右上角「CRUD」→「新增資產」開始建立。", "No assets yet. Tap CRUD → Create asset, or import an Excel file.") else tr("找不到符合條件的資產。", "No assets match these filters."),
                        modifier = Modifier.align(Alignment.Center),
                    )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),   // 避免最後一筆被小管家按鈕擋住
                ) {
                    if (state.error != null) {
                        item { Text(state.error!!, color = MaterialTheme.colorScheme.error) }
                    }
                    items(state.items, key = { it.id }) { asset ->
                        AssetCard(
                            asset = asset,
                            checked = asset.id in state.selected,
                            onCheckedChange = { viewModel.toggleSelected(asset) },
                        ) {
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
                                Text(if (state.loadingMore) tr("載入中…", "Loading…") else tr("載入更多", "Load more"))
                            }
                        }
                    }
                }
            }
        }
    }

    // 小管家（AI 助理）按鈕
    Surface(
        onClick = { showAgent = true },
        shape = CircleShape,
        shadowElevation = 6.dp,
        modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(20.dp),
    ) {
        AgentAvatar(60)
    }
    }

    state.writeOff?.let { WriteOffDialog(it, viewModel) }

    if (showImport) {
        AssetImportDialog(
            viewModel = importViewModel,
            onDismiss = { showImport = false },
            onImported = { y, q ->
                showImport = false
                viewModel.loadOptions()
                viewModel.showPeriod(y, q)
            },
        )
    }

    if (showAgent) {
        AgentChatDialog(
            viewModel = agentViewModel,
            onDismiss = { showAgent = false },
            onOpenAsset = { id -> showAgent = false; onOpen(id) },
            onShowInList = { f -> showAgent = false; viewModel.applyAgentFilters(f) },
        )
    }

    when (filterPicker) {
        "location" -> FilterPickerDialog(tr("依存放位置篩選", "Filter by location"), state.locationOptions, state.location,
            onSelect = { viewModel.setLocation(it); filterPicker = null }, onDismiss = { filterPicker = null })
        "custodian" -> FilterPickerDialog(tr("依保管人篩選", "Filter by custodian"), state.custodianOptions, state.custodian,
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
                    Text(tr("位置：", "Location: ") + (state.location?.name ?: tr("全部", "All")), maxLines = 1)
                }
                OutlinedButton(onClick = onPickCustodian, modifier = Modifier.weight(1f)) {
                    Text(tr("保管人：", "Custodian: ") + (state.custodian?.name ?: tr("全部", "All")), maxLines = 1)
                }
            }
            Text(tr("使用年限", "Useful life"), style = MaterialTheme.typography.labelLarge)
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
        "EXPIRED" -> tr("已過使用年限", "Past useful life") to MaterialTheme.colorScheme.error
        "EXPIRING_SOON" -> tr("90 天內到期", "Expires within 90 days") to MaterialTheme.colorScheme.tertiary
        else -> return
    }
    Text(text, color = color, style = MaterialTheme.typography.labelLarge)
}

/** 資產卡片；左上角的方框用來勾選要匯出的資產（點方框只勾選，點卡片其他地方才開啟）。 */
@Composable
private fun AssetCard(asset: AssetDto, checked: Boolean, onCheckedChange: (Boolean) -> Unit, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 0.dp, top = 4.dp, end = 12.dp, bottom = 12.dp)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(asset.itemName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("× ${asset.quantity}" + (asset.unit?.let { " $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
            }
            Text(tr("編號：${asset.assetCode}", "Code: ${asset.assetCode}"), style = MaterialTheme.typography.bodyMedium)
            if (!asset.specification.isNullOrBlank()) {
                Text(asset.specification, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Text(
                tr("保管人", "Custodian") + "：${asset.manager?.name ?: "—"}　" + tr("位置", "Location") + "：${asset.location?.name ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!asset.color.isNullOrBlank()) Text(tr("顏色：${asset.color}", "Color: ${asset.color}"), style = MaterialTheme.typography.bodySmall)
            ExpiryBadge(asset.expiryStatus)
            if (asset.expectedExpiryDate != null) {
                Text(tr("預計到期：${AssetFormat.isoToDisplay(asset.expectedExpiryDate)}", "Expires: ${AssetFormat.isoToDisplay(asset.expectedExpiryDate)}"), style = MaterialTheme.typography.bodySmall)
            }
            if (asset.periods.isNotEmpty()) {
                Text(tr("盤點期別：", "Periods: ") + asset.periods.joinToString("、") { it.label }, style = MaterialTheme.typography.bodySmall)
            }
        }
        }
    }
}

@Composable
fun DeleteConfirmDialog(asset: AssetDto, deleting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(tr("確認刪除", "Confirm delete")) },
        text = { Text(tr("確定要刪除「${asset.itemName}」（編號 ${asset.assetCode}，數量 ${asset.quantity}）嗎？\n刪除後將不再出現在清單與統計中。", "Delete \"${asset.itemName}\" (code ${asset.assetCode}, quantity ${asset.quantity})?\nIt will no longer appear in lists or totals.")) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !deleting) {
                Text(if (deleting) tr("刪除中…", "Deleting…") else tr("刪除", "Delete"), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !deleting) { Text(tr("取消", "Cancel")) } },
    )
}
