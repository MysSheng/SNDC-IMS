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
fun LoginScreen(viewModel: LoginViewModel, onGoRegister: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("SNDC 智慧辦公室", style = MaterialTheme.typography.headlineMedium)
        Text("請登入", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        LabeledField(state.username, viewModel::onUsernameChange, "使用者名稱", state.usernameError, !state.loading)
        PinField(state.pin, viewModel::onPinChange, "4 位數 PIN 碼", state.pinError, !state.loading)
        ErrorText(state.message)
        PrimaryButton("登入", state.loading, viewModel::submit)
        TextButton(onClick = onGoRegister, enabled = !state.loading) { Text("還沒有帳號？註冊") }
    }
}
