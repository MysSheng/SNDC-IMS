package icdf.sndc.ims.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icdf.sndc.ims.ui.common.ErrorText
import icdf.sndc.ims.ui.common.PrimaryButton
import icdf.sndc.ims.ui.common.tr

/** 新增／修改資產（規格書 v1.3 第 4.2 節欄位）。標 * 者為必填。 */
@Composable
fun AssetFormScreen(viewModel: AssetFormViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<String?>(null) }
    val enabled = !state.saving

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack, enabled = enabled) { Text(tr("← 取消", "← Cancel")) }
        Text(if (state.isEdit) tr("修改資產", "Edit asset") else tr("新增資產", "New asset"), style = MaterialTheme.typography.headlineSmall)

        if (state.loading) {
            CircularProgressIndicator()
            return@Column
        }
        if (state.loadError != null) {
            ErrorText(state.loadError)
            TextButton(onClick = viewModel::load) { Text(tr("重試", "Retry")) }
            return@Column
        }

        FormField(tr("編號 *", "Asset code *"), state.assetCode, Field.CODE, state.errors, enabled, viewModel::update)
        FormField(tr("名稱 *（例如：印表機）", "Name * (e.g. printer)"), state.itemName, Field.NAME, state.errors, enabled, viewModel::update)
        FormField(tr("所屬技術團 *", "Technical mission *"), state.techMission, Field.MISSION, state.errors, enabled, viewModel::update)
        DatePickerField(tr("購置日期 *", "Purchase date *"), state.purchaseDate, state.errors[Field.DATE], enabled, viewModel::selectPurchaseDate)
        SelectField(tr("存放位置 *", "Location *"), state.location, state.errors[Field.LOCATION], enabled) { picker = Field.LOCATION }
        SelectField(tr("保管人 *", "Custodian *"), state.custodian, state.errors[Field.CUSTODIAN], enabled) { picker = Field.CUSTODIAN }
        FormField(tr("數量 *", "Quantity *"), state.quantity, Field.QUANTITY, state.errors, enabled, viewModel::update,
            keyboardType = KeyboardType.Number)
        FormField(tr("規格及摘要", "Specification"), state.specification, Field.SPEC, state.errors, enabled, viewModel::update, singleLine = false)
        FormField(tr("單位（例如：臺、具、套）", "Unit (e.g. set, piece)"), state.unit, Field.UNIT, state.errors, enabled, viewModel::update)
        FormField(tr("購置類型／經費來源（例如：當地採購）", "Acquisition type / funding (e.g. local purchase)"), state.acquisitionType, Field.ACQUISITION, state.errors, enabled, viewModel::update)
        FormField(tr("價值（美金）", "Value (USD)"), state.valueUsd, Field.USD, state.errors, enabled, viewModel::update,
            keyboardType = KeyboardType.Decimal)
        FormField(tr("價值（台幣）", "Value (TWD)"), state.valueTwd, Field.TWD, state.errors, enabled, viewModel::update,
            keyboardType = KeyboardType.Decimal)
        FormField(tr("使用年限（年，選填；未填則不計算到期日）", "Useful life (years, optional; no expiry if empty)"), state.usefulLifeYears, Field.LIFE, state.errors, enabled,
            viewModel::update, keyboardType = KeyboardType.Decimal, placeholder = "5")
        FormField(tr("顏色", "Color"), state.color, Field.COLOR, state.errors, enabled, viewModel::update)
        FormField(tr("備註", "Remarks"), state.description, Field.NOTE, state.errors, enabled, viewModel::update, singleLine = false)

        if (state.existingPeriods.isNotEmpty()) {
            Text(tr("目前所屬盤點期別：", "Current periods: ") + state.existingPeriods.joinToString("、"), style = MaterialTheme.typography.bodySmall)
        }
        Text(if (state.isEdit) tr("再加入一個盤點期別（選填）", "Add to another period (optional)") else tr("盤點期別（選填）", "Inventory period (optional)"), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val thisYear = java.time.LocalDate.now().year
            LabeledDropdown(
                text = state.periodYear?.let { tr("$it 年", "$it") } ?: tr("不加入", "None"),
                options = listOf<Pair<Int?, String>>(null to tr("不加入", "None")) +
                    ((thisYear + 1) downTo (thisYear - 10)).map { it to tr("$it 年", "$it") },
                enabled = enabled,
                onSelect = viewModel::setPeriodYear,
                modifier = Modifier.weight(1f),
            )
            LabeledDropdown(
                text = state.periodQuarter?.let { "Q$it" } ?: tr("季度", "Quarter"),
                options = (1..4).map { it to tr("Q$it（第 $it 季）", "Q$it") },
                enabled = enabled && state.periodYear != null,
                onSelect = viewModel::setPeriodQuarter,
                modifier = Modifier.weight(1f),
            )
        }

        ErrorText(state.message)
        PrimaryButton(if (state.isEdit) tr("儲存修改", "Save changes") else tr("新增", "Create"), state.saving, viewModel::submit)
    }

    if (state.missingFields != null || state.invalidFields != null) {
        ValidationDialog(state.missingFields.orEmpty(), state.invalidFields.orEmpty(), viewModel::dismissValidationDialog)
    }

    when (picker) {
        Field.LOCATION -> OptionPickerDialog(
            title = tr("選擇存放位置", "Select location"),
            options = state.locations,
            selected = state.location,
            addLabel = tr("新增存放位置名稱", "New location name"),
            onSelect = { viewModel.selectLocation(it); picker = null },
            onAdd = { viewModel.addLocation(it); picker = null },
            onDismiss = { picker = null },
        )
        Field.CUSTODIAN -> OptionPickerDialog(
            title = tr("選擇保管人", "Select custodian"),
            options = state.custodians,
            selected = state.custodian,
            addLabel = tr("新增保管人姓名", "New custodian name"),
            onSelect = { viewModel.selectCustodian(it); picker = null },
            onAdd = { viewModel.addCustodian(it); picker = null },
            onDismiss = { picker = null },
        )
    }
}

/** 按下「新增」時若有必填欄位未填，先跳出此視窗擋下，不送出到伺服器。 */
@Composable
private fun ValidationDialog(missing: List<String>, invalid: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (missing.isNotEmpty()) tr("尚有必填欄位未填寫", "Some required fields are empty") else tr("部分欄位格式不正確", "Some fields are invalid")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (missing.isNotEmpty()) {
                    Text(tr("請填寫：", "Please fill in:"))
                    missing.forEach { Text("・$it") }
                }
                if (invalid.isNotEmpty()) {
                    Text(tr("請檢查：", "Please check:"))
                    invalid.forEach { Text("・$it") }
                }
                Text(tr("欄位下方的紅字會說明需要修正的地方。", "The red text under each field explains what to fix."), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("知道了", "OK")) } },
    )
}

@Composable
private fun FormField(
    label: String,
    value: String,
    field: String,
    errors: Map<String, String>,
    enabled: Boolean,
    onChange: (String, String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    singleLine: Boolean = true,
) {
    val error = errors[field]
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(field, it) },
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        isError = error != null,
        supportingText = { if (error != null) Text(error) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}
