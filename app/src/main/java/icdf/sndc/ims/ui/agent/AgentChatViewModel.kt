package icdf.sndc.ims.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icdf.sndc.ims.data.AgentRepository
import icdf.sndc.ims.data.remote.AgentResponseDto
import icdf.sndc.ims.data.remote.ApiResult
import icdf.sndc.ims.data.remote.ChatTurnDto
import icdf.sndc.ims.data.remote.PendingActionDto
import icdf.sndc.ims.ui.common.AppLanguage
import icdf.sndc.ims.ui.common.I18n
import icdf.sndc.ims.ui.common.toUserMessage
import icdf.sndc.ims.ui.common.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val response: AgentResponseDto? = null,
    val isError: Boolean = false,
)

data class AgentChatUiState(
    val messages: List<ChatMessage> = listOf(ChatMessage(0, false, AgentChatViewModel.greeting())),
    val input: String = "",
    val sending: Boolean = false,
    /** 非 null 時顯示確認視窗（修改／刪除）。 */
    val confirming: PendingActionDto? = null,
    val executing: Boolean = false,
    /** 每次修改／刪除完成時遞增，讓清單知道要重新載入。 */
    val dataVersion: Int = 0,
)

class AgentChatViewModel(private val repo: AgentRepository) : ViewModel() {

    private val _state = MutableStateFlow(AgentChatUiState())
    val state: StateFlow<AgentChatUiState> = _state.asStateFlow()

    /** 送給後端的對話紀錄（不含開場白與錯誤訊息）。 */
    private val history = mutableListOf<ChatTurnDto>()
    private var nextId = 1L

    fun onInput(v: String) = _state.update { it.copy(input = v.take(AgentRepository.MAX_MESSAGE)) }

    fun send(text: String = _state.value.input) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.sending) return
        append(ChatMessage(nextId++, true, message))
        _state.update { it.copy(input = "", sending = true) }
        viewModelScope.launch {
            when (val r = repo.ask(message, history.toList())) {
                is ApiResult.Success -> {
                    rememberTurn(message, r.data.historyText ?: r.data.reply)
                    append(ChatMessage(nextId++, false, r.data.reply, r.data))
                    // 修改／刪除：立即跳出確認視窗（防呆），確認前不會變更任何資料
                    _state.update { it.copy(sending = false, confirming = r.data.pendingAction) }
                }
                is ApiResult.Failure -> {
                    append(ChatMessage(nextId++, false, r.error.toUserMessage(), isError = true))
                    _state.update { it.copy(sending = false) }
                }
            }
        }
    }

    /** 從對話中的動作卡片再次開啟確認視窗。 */
    fun review(action: PendingActionDto) = _state.update { it.copy(confirming = action) }

    fun confirm() {
        val action = _state.value.confirming ?: return
        _state.update { it.copy(executing = true) }
        viewModelScope.launch {
            when (val r = repo.confirm(action.id)) {
                is ApiResult.Success -> {
                    history.add(ChatTurnDto("assistant", r.data.reply))
                    append(ChatMessage(nextId++, false, r.data.reply))
                    markHandled(action.id)
                    _state.update { it.copy(executing = false, confirming = null, dataVersion = it.dataVersion + 1) }
                }
                is ApiResult.Failure -> {
                    append(ChatMessage(nextId++, false, r.error.toUserMessage(), isError = true))
                    _state.update { it.copy(executing = false, confirming = null) }
                }
            }
        }
    }

    fun cancelAction() {
        val action = _state.value.confirming ?: return
        _state.update { it.copy(confirming = null) }
        markHandled(action.id)
        append(ChatMessage(nextId++, false, tr("已取消，沒有變更任何資料。", "Cancelled. Nothing was changed.")))
        history.add(ChatTurnDto("assistant", "使用者取消了此動作，沒有變更任何資料。"))
        viewModelScope.launch { repo.cancel(action.id) }
    }

    /** 確認視窗關閉但未決定（例如按返回鍵）：保留動作，可從卡片再次開啟。 */
    fun dismissConfirm() = _state.update { it.copy(confirming = null) }

    fun reset() {
        history.clear()
        _state.update { AgentChatUiState(dataVersion = it.dataVersion) }
    }

    private fun rememberTurn(user: String, assistant: String) {
        history.add(ChatTurnDto("user", user))
        history.add(ChatTurnDto("assistant", assistant.take(1000)))
        while (history.size > AgentRepository.MAX_HISTORY) history.removeAt(0)
    }

    private fun append(m: ChatMessage) = _state.update { it.copy(messages = it.messages + m) }

    /** 動作已執行或取消後，卡片上的按鈕不再顯示。 */
    private fun markHandled(actionId: String) = _state.update { s ->
        s.copy(messages = s.messages.map { m ->
            val r = m.response
            if (r != null && r.pendingAction?.id == actionId) m.copy(response = r.copy(pendingAction = null)) else m
        })
    }

    companion object {
        fun greeting() = tr(
            "我可以怎麼協助你？\n你可以用一般的說法告訴我想找什麼、想統計什麼，或要修改、刪除哪些資產。",
            "How can I help?\nTell me in plain words what to find or count, or which assets to edit or delete.",
        )

        /** 範例問題（點選即送出）；英文介面時顯示英文範例，後端可理解中英文。 */
        val EXAMPLES: List<String> get() = if (I18n.language == AppLanguage.EN) listOf(
            "Assets kept by 李大維 that are about to expire",
            "How many air conditioners do we have?",
            "Which items are duplicated?",
            "Rename asset 60109-0003 to faucet",
        ) else listOf(
            "找李大維管理且快要過期的資產",
            "我們有多少台冷氣？",
            "有哪些物品有重複？",
            "編號 60109-0003 的名稱改成水龍頭",
        )
    }
}
