package icdf.sndc.ims.ui.agent

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icdf.sndc.ims.R
import icdf.sndc.ims.data.remote.AgentFiltersDto
import icdf.sndc.ims.data.remote.AgentResponseDto
import icdf.sndc.ims.data.remote.PendingActionDto
import icdf.sndc.ims.ui.assets.AssetFormat

/** 小管家頭像（可重複使用於按鈕與對話中）。 */
@Composable
fun AgentAvatar(size: Int, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_agent_avatar),
        contentDescription = "AI 助理小管家",
        modifier = modifier.size(size.dp).clip(CircleShape),
    )
}

/**
 * AI 助理對話視窗。查詢直接顯示結果；修改／刪除會跳出確認視窗，使用者確認後才執行。
 */
@Composable
fun AgentChatDialog(
    viewModel: AgentChatViewModel,
    onDismiss: () -> Unit,
    onOpenAsset: (String) -> Unit,
    onShowInList: (AgentFiltersDto) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.sending) {
        listState.animateScrollToItem(maxOf(0, state.messages.size - 1 + if (state.sending) 1 else 0))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f).imePadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                // 標題列
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AgentAvatar(40)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("小管家", style = MaterialTheme.typography.titleMedium)
                        Text("AI 資產助理", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = viewModel::reset, enabled = !state.sending) { Text("重新開始") }
                    TextButton(onClick = onDismiss) { Text("關閉") }
                }
                HorizontalDivider()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Box(Modifier.padding(top = 4.dp)) }
                    items(state.messages, key = { it.id }) { m ->
                        MessageBubble(m, viewModel::review, onOpenAsset, onShowInList)
                    }
                    if (state.messages.size == 1) {
                        item { ExampleChips(enabled = !state.sending, onPick = { viewModel.send(it) }) }
                    }
                    if (state.sending) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("  小管家思考中…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = viewModel::onInput,
                        placeholder = { Text("輸入你的需求…") },
                        enabled = !state.sending,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { viewModel.send() },
                        enabled = !state.sending && state.input.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("送出") }
                }
            }
        }
    }

    state.confirming?.let { action ->
        ConfirmActionDialog(action, state.executing, onConfirm = viewModel::confirm,
            onCancel = viewModel::cancelAction, onDismiss = viewModel::dismissConfirm)
    }
}

@Composable
private fun MessageBubble(
    m: ChatMessage,
    onReview: (PendingActionDto) -> Unit,
    onOpenAsset: (String) -> Unit,
    onShowInList: (AgentFiltersDto) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!m.fromUser) {
            AgentAvatar(28, Modifier.padding(end = 6.dp))
        }
        val bg = when {
            m.fromUser -> MaterialTheme.colorScheme.primaryContainer
            m.isError -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        Column(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp)).background(bg).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(m.text, style = MaterialTheme.typography.bodyMedium)
            m.response?.let { ResponseDetails(it, onReview, onOpenAsset, onShowInList) }
        }
    }
}

@Composable
private fun ResponseDetails(
    r: AgentResponseDto,
    onReview: (PendingActionDto) -> Unit,
    onOpenAsset: (String) -> Unit,
    onShowInList: (AgentFiltersDto) -> Unit,
) {
    val pending = r.pendingAction
    // 查詢結果：可點選開啟詳情（待確認動作的目標清單在確認視窗中顯示）
    if (pending == null) {
        r.results?.take(20)?.forEach { a ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().clickable { onOpenAsset(a.id) },
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text("${a.itemName} × ${a.quantity}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${a.assetCode}　${a.manager?.name ?: "—"}　${a.location?.name ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    a.expectedExpiryDate?.let {
                        Text("到期：${AssetFormat.isoToDisplay(it)}" + when (a.expiryStatus) {
                            "EXPIRED" -> "（已過使用年限）"
                            "EXPIRING_SOON" -> "（90 天內到期）"
                            else -> ""
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (r.intent == "SEARCH" && r.filters != null && (r.totalResults ?: 0) > 0) {
            OutlinedButton(onClick = { onShowInList(r.filters) }) { Text("在清單中顯示") }
        }
    } else {
        Button(onClick = { onReview(pending) }) {
            Text(if (pending.type == "DELETE") "檢視並確認刪除" else "檢視並確認修改")
        }
    }
    r.summary?.let {
        Text("合計 ${it.totalQuantity} 個／${it.recordCount} 筆紀錄", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ExampleChips(enabled: Boolean, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("可以試試：", style = MaterialTheme.typography.labelLarge)
        AgentChatViewModel.EXAMPLES.forEach { ex ->
            OutlinedButton(onClick = { onPick(ex) }, enabled = enabled) { Text(ex) }
        }
    }
}

/** 防呆：修改／刪除前列出所有受影響的資產，使用者按「確認」才執行。 */
@Composable
private fun ConfirmActionDialog(
    action: PendingActionDto,
    executing: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDelete = action.type == "DELETE"
    AlertDialog(
        onDismissRequest = { if (!executing) onDismiss() },
        title = { Text(if (isDelete) "確認刪除 ${action.targets.size} 筆資產？" else "確認修改 ${action.targets.size} 筆資產？") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!isDelete && action.changes.isNotEmpty()) {
                    Text("將修改為：", style = MaterialTheme.typography.labelLarge)
                    action.changes.forEach { (k, v) -> Text("・$k → $v") }
                    HorizontalDivider()
                }
                Text(if (isDelete) "以下資產將被刪除：" else "受影響的資產：", style = MaterialTheme.typography.labelLarge)
                action.targets.forEach { t ->
                    Text("・${t.assetCode}　${t.itemName} × ${t.quantity}（${t.custodian ?: "—"}，${t.location ?: "—"}）",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (isDelete) {
                    Text("刪除後將不再出現在清單與統計中。", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !executing) {
                Text(
                    if (executing) "執行中…" else if (isDelete) "確認刪除" else "確認修改",
                    color = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = !executing) { Text("取消") } },
    )
}
