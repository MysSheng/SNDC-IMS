package icdf.sndc.ims.ui.assets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import icdf.sndc.ims.data.remote.RefDto

/** 看起來像欄位的選擇按鈕，點選後開啟選單。 */
@Composable
fun SelectField(label: String, value: RefDto?, error: String?, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(value?.name ?: "點選以選擇", modifier = Modifier.fillMaxWidth())
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 選擇既有項目，或輸入名稱新增一個（新增會立即寫入後端）。 */
@Composable
fun OptionPickerDialog(
    title: String,
    options: List<RefDto>,
    selected: RefDto?,
    addLabel: String,
    onSelect: (RefDto) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (options.isEmpty()) {
                    Text("目前沒有任何選項，請在下方新增。")
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(options, key = { it.id }) { option ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = option.id == selected?.id, onClick = { onSelect(option) })
                                Text(option.name)
                            }
                        }
                    }
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(addLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { onAdd(newName.trim()); newName = "" },
                    enabled = newName.isNotBlank(),
                ) { Text("新增並選取") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
    )
}

/** 篩選用的單選清單：第一項為「全部」（不篩選）。 */
@Composable
fun FilterPickerDialog(
    title: String,
    options: List<RefDto>,
    selected: RefDto?,
    onSelect: (RefDto?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == null, onClick = { onSelect(null) })
                        Text("全部")
                    }
                }
                items(options, key = { it.id }) { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option.id == selected?.id, onClick = { onSelect(option) })
                        Text(option.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
    )
}
