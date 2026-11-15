package icdf.sndc.ims.data.session

import icdf.sndc.ims.data.remote.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 登入狀態只保存在記憶體中：App 被關閉後需重新輸入 PIN。
 * 這是最簡單且安全的 Token 儲存方式（不寫入磁碟，也不保存 PIN）。
 */
class SessionManager {

    data class Session(val token: String, val user: UserDto)

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    val token: String? get() = _session.value?.token

    fun start(token: String, user: UserDto) {
        _session.value = Session(token, user)
    }

    fun clear() {
        _session.value = null
    }
}
