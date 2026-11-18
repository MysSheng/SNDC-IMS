package icdf.sndc.ims.ui.assets

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icdf.sndc.ims.data.remote.ImportPreviewDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import icdf.sndc.ims.ui.common.tr

/** 可匯入的檔案類型（部分檔案管理員會把 CSV 標成 text/plain 或 octet-stream）。 */
private val IMPORT_MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/octet-stream",
)

private const val MAX_IMPORT_BYTES = 10L * 1024 * 1024

/** 匯入 Excel／CSV 的對話框（規格書第 4.6 節）。匯入成功後 onImported 帶回期別，讓清單切換過去。 */
@Composable
fun AssetImportDialog(
    viewModel: AssetImportViewModel,
    onDismiss: () -> Unit,
    onImported: (year: Int, quarter: Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy = state.checking || state.confirming

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) { readFile(context, uri) }
                when {
                    loaded == null -> viewModel.fileUnreadable()
                    loaded.second == null -> viewModel.fileTooLarge()
                    else -> viewModel.fileSelected(loaded.first, loaded.second!!)
                }
            }
        }
    }

    fun close() {
        viewModel.reset()
        onDismiss()
    }

    state.result?.let { result ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(tr("匯入完成", "Import complete")) },
            text = {
                Text(tr("已匯入 ${result.period} 的清單：新增 ${result.created} 筆、更新 ${result.updated} 筆", "Imported the ${result.period} list: ${result.created} created, ${result.updated} updated") +
                    (if (result.restored > 0) tr("、恢復 ${result.restored} 筆", ", ${result.restored} restored") else "") + tr("。", "."))
            },
            confirmButton = {
                TextButton(onClick = {
                    val y = state.year
                    val q = state.quarter
                    viewModel.reset()
                    onImported(y, q)
                }) { Text(tr("查看 ${result.period} 清單", "View ${result.period}")) }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) close() },
        title = { Text(tr("匯入 Excel／CSV", "Import Excel/CSV")) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    tr("可直接上傳主管的財產清單（.xlsx），或 CSV（第一列為表頭，需有「財產編號」「財產名稱」「放置地點」「保管人」等欄位）。", "Upload the supervisor's asset list (.xlsx) as is, or a CSV whose first row is the header (columns such as 財產編號, 財產名稱, 放置地點, 保管人)."),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { picker.launch(IMPORT_MIME_TYPES) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(state.fileName ?: tr("選擇檔案", "Choose file")) }

                Text(tr("盤點期別", "Inventory period"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val thisYear = LocalDate.now().year
                    val years = ((thisYear + 1) downTo (thisYear - 10)).toList().let {
                        if (state.year in it) it else listOf(state.year) + it
                    }
                    LabeledDropdown(
                        text = tr("${state.year} 年", "${state.year}"),
                        options = years.map { it to tr("$it 年", "$it") },
                        enabled = !busy,
                        onSelect = viewModel::setYear,
                        modifier = Modifier.weight(1f),
                    )
                    LabeledDropdown(
                        text = "Q${state.quarter}",
                        options = (1..4).map { it to tr("Q$it（第 $it 季）", "Q$it") },
                        enabled = !busy,
                        onSelect = viewModel::setQuarter,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = state.techMission,
                    onValueChange = viewModel::setTechMission,
                    label = { Text(if (state.isCsv) tr("所屬技術團 *", "Technical mission *") else tr("所屬技術團（留空則讀取 Excel 標題）", "Technical mission (empty = read from the Excel title)")) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (busy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.heightIn(max = 20.dp))
                        Text(if (state.checking) tr("檢查中…", "Checking…") else tr("匯入中…", "Importing…"))
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.preview?.let { PreviewSummary(it) }
            }
        },
        confirmButton = {
            val preview = state.preview
            if (preview?.token != null) {
                TextButton(onClick = viewModel::confirm, enabled = !busy) { Text(tr("確認匯入 ${preview.totalRows} 筆", "Import ${preview.totalRows} rows")) }
            } else {
                TextButton(onClick = viewModel::check, enabled = !busy && state.bytes != null) { Text(tr("檢查檔案", "Check file")) }
            }
        },
        dismissButton = { TextButton(onClick = ::close, enabled = !busy) { Text(tr("取消", "Cancel")) } },
    )
}

@Composable
private fun PreviewSummary(p: ImportPreviewDto) {
    HorizontalDivider()
    if (p.errorCount > 0) {
        Text(tr("發現 ${p.errorCount} 個問題，整份檔案都不會匯入。請修正後重新選擇檔案：", "Found ${p.errorCount} problems, so nothing will be imported. Fix them and choose the file again:"),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
        p.errors.forEach { e ->
            val where = listOfNotNull(
                e.sheet?.let { "「$it」" },
                e.row?.let { tr("第 $it 列", " row $it") },
                e.assetCode?.let { tr("（$it）", " ($it)") },
            ).joinToString("")
            Text("・" + (if (where.isEmpty()) "" else tr("$where：", "$where: ")) + e.message, style = MaterialTheme.typography.bodySmall)
        }
        if (p.errorCount > p.errors.size) {
            Text(tr("…另有 ${p.errorCount - p.errors.size} 個問題未列出", "…and ${p.errorCount - p.errors.size} more"), style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    Text(tr("檢查通過：${p.period}，共 ${p.totalRows} 筆", "Check passed: ${p.period}, ${p.totalRows} rows"), style = MaterialTheme.typography.titleSmall)
    Text(tr("・新增 ${p.createCount} 筆、更新 ${p.updateCount} 筆", "・${p.createCount} new, ${p.updateCount} updated") +
        if (p.restoreCount > 0) tr("、恢復已刪除的 ${p.restoreCount} 筆", ", ${p.restoreCount} restored") else "")
    p.techMission?.let { Text(tr("・所屬技術團：$it", "・Technical mission: $it")) }
    if (p.newLocations.isNotEmpty()) Text(tr("・將新增存放位置：", "・New locations: ") + p.newLocations.joinToString("、"))
    if (p.newCustodians.isNotEmpty()) Text(tr("・將新增保管人：", "・New custodians: ") + p.newCustodians.joinToString("、"))
    Text(tr("Excel 沒有的欄位（使用年限、顏色）不會被改動；Excel 空白的欄位保留原值。", "Fields not in the Excel file (useful life, color) are not changed; blank cells keep the current value."),
        style = MaterialTheme.typography.bodySmall)
}

/** 以按鈕＋下拉選單選擇一個值。 */
@Composable
fun <T> LabeledDropdown(
    text: String,
    options: List<Pair<T, String>>,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$text ▾", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onSelect(value) })
            }
        }
    }
}

/** 讀取使用者選的檔案：回傳（檔名, 內容）；超過 10 MB 時內容為 null；無法讀取時回傳 null。 */
private fun readFile(context: Context, uri: Uri): Pair<String, ByteArray?>? = runCatching<Pair<String, ByteArray?>?> {
    val resolver = context.contentResolver
    var name = "import.xlsx"
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getString(0)?.let { name = it }
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    if (size > MAX_IMPORT_BYTES) return@runCatching name to null
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
    if (bytes.size > MAX_IMPORT_BYTES) name to null else name to bytes
}.getOrNull()
