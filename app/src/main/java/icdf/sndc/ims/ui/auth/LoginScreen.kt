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
import icdf.sndc.ims.ui.common.LanguageToggle
import icdf.sndc.ims.ui.common.LabeledField
import icdf.sndc.ims.ui.common.PinField
import icdf.sndc.ims.ui.common.PrimaryButton
import icdf.sndc.ims.ui.common.tr

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
        LanguageToggle(Modifier.align(Alignment.End))
        Spacer(Modifier.height(24.dp))
        Text(tr("SNDC 智慧辦公室", "SNDC Smart Office"), style = MaterialTheme.typography.headlineMedium)
        Text(tr("請登入", "Please log in"), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        LabeledField(state.username, viewModel::onUsernameChange, tr("使用者名稱", "Username"), state.usernameError, !state.loading)
        PinField(state.pin, viewModel::onPinChange, tr("4 位數 PIN 碼", "4-digit PIN"), state.pinError, !state.loading)
        ErrorText(state.message)
        PrimaryButton(tr("登入", "Log in"), state.loading, viewModel::submit)
        TextButton(onClick = onGoRegister, enabled = !state.loading) { Text(tr("還沒有帳號？註冊", "No account yet? Register")) }
    }
}
