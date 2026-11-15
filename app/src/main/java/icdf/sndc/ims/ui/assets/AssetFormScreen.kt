package icdf.sndc.ims.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
        TextButton(onClick = onBack, enabled = enabled) { Text("← 取消") }
        Text(if (state.isEdit) "修改資產" else "新增資產", style = MaterialTheme.typography.headlineSmall)

        if (state.loading) {
            CircularProgressIndicator()
            return@Column
        }
        if (state.loadError != null) {
            ErrorText(state.loadError)
            TextButton(onClick = viewModel::load) { Text("重試") }
            return@Column
        }

        FormField("編號 *", state.assetCode, Field.CODE, state.errors, enabled, viewModel::update)
        FormField("名稱 *（例如：印表機）", state.itemName, Field.NAME, state.errors, enabled, viewModel::update)
        FormField("所屬技術團 *", state.techMission, Field.MISSION, state.errors, enabled, viewModel::update)
        DatePickerField("購置日期 *", state.purchaseDate, state.errors[Field.DATE], enabled, viewModel::selectPurchaseDate)
        SelectField("存放位置 *", state.location, state.errors[Field.LOCATION], enabled) { picker = Field.LOCATION }
        FormField("使用年限 *（年）", state.usefulLifeYears, Field.LIFE, state.errors, enabled, viewModel::update,
            keyboardType = KeyboardType.Decimal, placeholder = "5")
        SelectField("保管人 *", state.custodian, state.errors[Field.CUSTODIAN], enabled) { picker = Field.CUSTODIAN }
        FormField("數量 *", state.quantity, Field.QUANTITY, state.errors, enabled, viewModel::update,
            keyboardType = KeyboardType.Number)
        FormField("顏色", state.color, Field.COLOR, state.errors, enabled, viewModel::update)
        FormField("備註", state.description, Field.NOTE, state.errors, enabled, viewModel::update, singleLine = false)

        ErrorText(state.message)
        PrimaryButton(if (state.isEdit) "儲存修改" else "新增", state.saving, viewModel::submit)
    }

    if (state.missingFields != null || state.invalidFields != null) {
        ValidationDialog(state.missingFields.orEmpty(), state.invalidFields.orEmpty(), viewModel::dismissValidationDialog)
    }

    when (picker) {
        Field.LOCATION -> OptionPickerDialog(
            title = "選擇存放位置",
            options = state.locations,
            selected = state.location,
            addLabel = "新增存放位置名稱",
            onSelect = { viewModel.selectLocation(it); picker = null },
            onAdd = { viewModel.addLocation(it); picker = null },
            onDismiss = { picker = null },
        )
        Field.CUSTODIAN -> OptionPickerDialog(
            title = "選擇保管人",
            options = state.custodians,
            selected = state.custodian,
            addLabel = "新增保管人姓名",
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
        title = { Text(if (missing.isNotEmpty()) "尚有必填欄位未填寫" else "部分欄位格式不正確") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (missing.isNotEmpty()) {
                    Text("請填寫：")
                    missing.forEach { Text("・$it") }
                }
                if (invalid.isNotEmpty()) {
                    Text("請檢查：")
                    invalid.forEach { Text("・$it") }
                }
                Text("欄位下方的紅字會說明需要修正的地方。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
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
