package icdf.sndc.ims.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icdf.sndc.ims.ui.common.ErrorText
import icdf.sndc.ims.ui.common.LabeledField
import icdf.sndc.ims.ui.common.PinField
import icdf.sndc.ims.ui.common.PrimaryButton

@Composable
fun RegisterScreen(viewModel: RegisterViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val enabled = !state.loading
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("註冊帳號", style = MaterialTheme.typography.headlineMedium)
        Text("需要團部通行碼才能註冊", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        LabeledField(state.username, viewModel::onUsernameChange, "使用者名稱", state.usernameError, enabled)
        PinField(state.pin, viewModel::onPinChange, "設定 4 位數 PIN 碼", state.pinError, enabled)
        PinField(state.pinConfirm, viewModel::onPinConfirmChange, "再次輸入 PIN 碼", state.pinConfirmError, enabled)
        LabeledField(state.code, viewModel::onCodeChange, "團部通行碼", state.codeError, enabled, isSecret = true)
        ErrorText(state.message)
        PrimaryButton("註冊", state.loading, viewModel::submit)
        TextButton(onClick = onBack, enabled = enabled) { Text("已有帳號？返回登入") }
    }
}
