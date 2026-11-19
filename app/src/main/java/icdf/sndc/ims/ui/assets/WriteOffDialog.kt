package icdf.sndc.ims.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import icdf.sndc.ims.ui.common.tr
import java.time.LocalDate

/**
 * 報銷核算（規格書第 4.6 節）：
 * 1. 檢查勾選資產的備註，詢問是否要編輯；可直接匯出（沿用目前備註）。
 * 2. 編輯：一次填入相同備註（例如「報廢原因:超過使用年限且損壞。」），或逐筆修改；可選擇同時存回資產。
 * 匯出的檔名固定為「報銷核算.xlsx」。
 */
@Composable
fun WriteOffDialog(draft: WriteOffDraft, viewModel: AssetListViewModel) {
    if (draft.editing) {
        EditRemarksDialog(draft, viewModel)
    } else {
        AskDialog(draft, viewModel)
    }
}

@Composable
private fun AskDialog(d: WriteOffDraft, vm: AssetListViewModel) {
    AlertDialog(
        onDismissRequest = { if (!d.exporting) vm.cancelWriteOff() },
        title = { Text(tr("報銷核算（${d.rows.size} 筆）", "Write-off list (${d.rows.size} assets)")) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeaderFields(d, vm)
                HorizontalDivider()
                Text(
                    if (d.missingRemarks == 0) tr("這些資產都已有備註。", "All of these assets have remarks.")
                    else tr("其中 ${d.missingRemarks} 筆沒有備註。", "${d.missingRemarks} of them have no remarks."),
                    style = MaterialTheme.typography.titleSmall,
                )
                d.rows.take(5).forEach { r ->
                    Text("・${r.assetCode} ${r.itemName}：" + (r.original ?: tr("（無備註）", "(no remarks)")),
                        style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                if (d.rows.size > 5) {
                    Text(tr("…另有 ${d.rows.size - 5} 筆", "…and ${d.rows.size - 5} more"), style = MaterialTheme.typography.bodySmall)
                }
                Text(tr("要編輯這些報銷品的備註嗎？", "Do you want to edit the remarks of these items?"))
            }
        },
        confirmButton = {
            TextButton(onClick = vm::editWriteOffRemarks, enabled = !d.exporting) { Text(tr("編輯備註", "Edit remarks")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = vm::cancelWriteOff, enabled = !d.exporting) { Text(tr("取消", "Cancel")) }
                TextButton(onClick = vm::exportWriteOff, enabled = !d.exporting) {
                    Text(if (d.exporting) tr("匯出中…", "Exporting…") else tr("直接匯出", "Export as is"))
                }
            }
        },
    )
}

@Composable
private fun EditRemarksDialog(d: WriteOffDraft, vm: AssetListViewModel) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(tr("編輯報銷備註", "Edit write-off remarks")) },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeaderFields(d, vm)
                HorizontalDivider()

                // 一次填入相同的備註
                Text(tr("一次填入相同備註", "Fill the same remark"), style = MaterialTheme.typography.titleSmall)
                AssistChip(
                    onClick = { vm.setWriteOffBatchText(WriteOffDraft.DEFAULT_REASON) },
                    label = { Text(WriteOffDraft.DEFAULT_REASON) },
                )
                OutlinedTextField(
                    value = d.batchText,
                    onValueChange = vm::setWriteOffBatchText,
                    label = { Text(tr("備註內容", "Remark")) },
                    enabled = !d.exporting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.applyWriteOffBatch(onlyEmpty = false) },
                        enabled = !d.exporting && d.batchText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text(tr("套用到全部", "Apply to all")) }
                    OutlinedButton(
                        onClick = { vm.applyWriteOffBatch(onlyEmpty = true) },
                        enabled = !d.exporting && d.batchText.isNotBlank() && d.missingRemarks > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text(tr("只填空白的", "Only empty ones")) }
                }
                HorizontalDivider()

                // 逐筆編輯
                Text(tr("逐筆編輯", "Edit one by one"), style = MaterialTheme.typography.titleSmall)
                d.rows.forEach { r ->
                    val was = r.original?.takeIf { it != r.remark }
                    OutlinedTextField(
                        value = r.remark,
                        onValueChange = { vm.setWriteOffRemark(r.id, it) },
                        label = { Text("${r.assetCode} ${r.itemName}", maxLines = 1) },
                        supportingText = if (was != null) {
                            { Text(tr("原本：$was", "Was: $was"), maxLines = 2) }
                        } else null,
                        enabled = !d.exporting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = d.saveRemarks, onCheckedChange = vm::setWriteOffSaveRemarks, enabled = !d.exporting)
                    Text(
                        tr("同時把備註存回資產（原本的備註會移到後面的括號內）", "Also save to the assets (the current remark is kept in parentheses after it)"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    tr("空白的欄位會沿用資產目前的備註。", "Empty fields keep the asset's current remarks."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = vm::exportWriteOff, enabled = !d.exporting) {
                Text(if (d.exporting) tr("匯出中…", "Exporting…") else tr("匯出報銷核算", "Export write-off list"))
            }
        },
        dismissButton = {
            TextButton(onClick = vm::cancelWriteOff, enabled = !d.exporting) { Text(tr("取消", "Cancel")) }
        },
    )
}

/** 標題列：計畫名稱＋盤點期別（例如「索馬利蘭政府資料中心與資訊安全能提升計畫 2026年Q1」）。 */
@Composable
private fun HeaderFields(d: WriteOffDraft, vm: AssetListViewModel) {
    OutlinedTextField(
        value = d.title,
        onValueChange = vm::setWriteOffTitle,
        label = { Text(tr("計畫名稱（標題）", "Project name (title)")) },
        enabled = !d.exporting,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val thisYear = LocalDate.now().year
        LabeledDropdown(
            text = d.year?.let { tr("$it 年", "$it") } ?: tr("不標示年度", "No year"),
            options = listOf<Pair<Int?, String>>(null to tr("不標示年度", "No year")) +
                ((thisYear + 1) downTo (thisYear - 10)).map { it to tr("$it 年", "$it") },
            enabled = !d.exporting,
            onSelect = vm::setWriteOffYear,
            modifier = Modifier.weight(1f),
        )
        LabeledDropdown(
            text = d.quarter?.let { "Q$it" } ?: tr("不標示季度", "No quarter"),
            options = listOf<Pair<Int?, String>>(null to tr("不標示季度", "No quarter")) + (1..4).map { it to "Q$it" },
            enabled = !d.exporting && d.year != null,
            onSelect = vm::setWriteOffQuarter,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        tr("檔名：報銷核算.xlsx", "File name: 報銷核算.xlsx"),
        style = MaterialTheme.typography.bodySmall,
    )
}
