package icdf.sndc.ims.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icdf.sndc.ims.data.AuthRepository
import icdf.sndc.ims.data.remote.ApiResult
import icdf.sndc.ims.data.remote.AppError
import icdf.sndc.ims.ui.common.AuthValidation
import icdf.sndc.ims.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val username: String = "",
    val pin: String = "",
    val pinConfirm: String = "",
    val code: String = "",
    val usernameError: String? = null,
    val pinError: String? = null,
    val pinConfirmError: String? = null,
    val codeError: String? = null,
    val message: String? = null,
    val loading: Boolean = false,
)

class RegisterViewModel(private val repo: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onUsernameChange(v: String) = _state.update { it.copy(username = v, usernameError = null, message = null) }
    fun onPinChange(v: String) = _state.update { it.copy(pin = v, pinError = null, message = null) }
    fun onPinConfirmChange(v: String) = _state.update { it.copy(pinConfirm = v, pinConfirmError = null, message = null) }
    fun onCodeChange(v: String) = _state.update { it.copy(code = v, codeError = null, message = null) }

    fun submit() {
        val s = _state.value
        val errors = s.copy(
            usernameError = AuthValidation.username(s.username),
            pinError = AuthValidation.pin(s.pin),
            pinConfirmError = AuthValidation.pinConfirm(s.pin, s.pinConfirm),
            codeError = AuthValidation.registrationCode(s.code),
        )
        if (listOf(errors.usernameError, errors.pinError, errors.pinConfirmError, errors.codeError).any { it != null }) {
            _state.value = errors
            return
        }
        _state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            when (val r = repo.register(s.username, s.pin, s.pinConfirm, s.code)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false) }
                is ApiResult.Failure -> {
                    val err = r.error
                    // 表單內容在失敗時保留（規格書第 11.3 節），錯誤顯示在對應欄位附近
                    _state.update {
                        it.copy(
                            loading = false,
                            usernameError = if (err is AppError.Validation && err.code == "USERNAME_TAKEN") err.toUserMessage() else null,
                            codeError = if (err is AppError.Auth && err.code == "INVALID_REGISTRATION_CODE") err.toUserMessage() else null,
                            message = err.toUserMessage(),
                        )
                    }
                }
            }
        }
    }
}
