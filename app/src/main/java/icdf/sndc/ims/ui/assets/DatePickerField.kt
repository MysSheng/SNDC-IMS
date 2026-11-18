package icdf.sndc.ims.ui.assets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.LocalDate
import icdf.sndc.ims.ui.common.tr

/** 年份限制 2000–2999（購置日期格式 2xxx/xx/xx）。 */
private val PURCHASE_YEARS = 2000..2999

/**
 * 點選後跳出日曆讓使用者直接選日期；顯示格式為 YYYY/MM/DD。
 * 日曆右上角的鉛筆圖示也可以切換成手動輸入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(label: String, value: String, error: String?, enabled: Boolean, onSelect: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(value.ifBlank { tr("點選以選擇日期", "Tap to pick a date") }, modifier = Modifier.fillMaxWidth())
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (open) {
        val initial = AssetFormat.parseDisplayDate(value) ?: LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = AssetFormat.toEpochMillis(initial),
            yearRange = PURCHASE_YEARS,
            selectableDates = object : SelectableDates {
                override fun isSelectableYear(year: Int): Boolean = year in PURCHASE_YEARS
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onSelect(AssetFormat.fromEpochMillis(it)) }
                        open = false
                    },
                    enabled = state.selectedDateMillis != null,
                ) { Text(tr("確定", "OK")) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(tr("取消", "Cancel")) } },
        ) {
            DatePicker(state = state)
        }
    }
}
