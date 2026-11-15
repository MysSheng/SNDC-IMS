package icdf.sndc.ims.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icdf.sndc.ims.data.AuthRepository
import icdf.sndc.ims.data.remote.ApiResult
import icdf.sndc.ims.ui.common.AuthValidation
import icdf.sndc.ims.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val pin: String = "",
    val usernameError: String? = null,
    val pinError: String? = null,
    val message: String? = null,
    val loading: Boolean = false,
)

class LoginViewModel(private val repo: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        // 預先填入上次登入的使用者名稱
        viewModelScope.launch {
            val last = repo.lastUsername.first()
            if (!last.isNullOrBlank() && _state.value.username.isEmpty()) {
                _state.update { it.copy(username = last) }
            }
        }
    }

    fun onUsernameChange(v: String) = _state.update { it.copy(username = v, usernameError = null, message = null) }
    fun onPinChange(v: String) = _state.update { it.copy(pin = v, pinError = null, message = null) }

    fun submit() {
        val s = _state.value
        val uErr = AuthValidation.username(s.username)
        val pErr = AuthValidation.pin(s.pin)
        if (uErr != null || pErr != null) {
            _state.update { it.copy(usernameError = uErr, pinError = pErr) }
            return
        }
        _state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            when (val r = repo.login(s.username, s.pin)) {
                // 成功時 Session 會更新，導覽由 NavHost 依登入狀態處理
                is ApiResult.Success -> _state.update { it.copy(loading = false, pin = "") }
                // 失敗時保留使用者名稱，只清除 PIN
                is ApiResult.Failure -> _state.update { it.copy(loading = false, pin = "", message = r.error.toUserMessage()) }
            }
        }
    }
}
