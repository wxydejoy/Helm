package cn.weiekko.dock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.weiekko.dock.data.HubConnection

@Composable
fun SettingsScreen(
    state: DockUiState,
    canGoBack: Boolean,
    onTest: (host: String, port: String, token: String) -> Unit,
    onSave: (host: String, port: String, token: String) -> Unit,
    onBack: () -> Unit,
) {
    var host by rememberSaveable { mutableStateOf(state.connection.host) }
    var port by rememberSaveable {
        mutableStateOf(state.connection.port.toString())
    }
    var token by rememberSaveable { mutableStateOf(state.connection.token) }
    val colors = MaterialTheme.colorScheme
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outline,
        focusedLabelColor = colors.primary,
        cursorColor = colors.primary,
    )

    LaunchedEffect(state.connection) {
        if (host.isEmpty()) host = state.connection.host
        if (token.isEmpty()) token = state.connection.token
        if (port == HubConnection.DEFAULT_PORT.toString() && state.connection.port != HubConnection.DEFAULT_PORT) {
            port = state.connection.port.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canGoBack) {
                IconButton(onClick = {
                    onSave(host, port, token)
                    onBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回主屏",
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
            Text(
                "连接 Hub",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "电脑上的 Python 脚本启动后会打印地址。现在可以先返回主屏看界面。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.surface,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("地址") },
                    placeholder = { Text("192.168.1.12") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("端口") },
                    placeholder = { Text("17890") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Token") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    colors = fieldColors,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onTest(host, port, token) },
            enabled = !state.testing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                disabledContainerColor = colors.primary.copy(alpha = 0.4f),
                disabledContentColor = colors.onPrimary,
            ),
        ) {
            if (state.testing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text("正在连接…")
            } else {
                Text("测试连接")
            }
        }
        state.settingsStatus?.let { status ->
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (state.settingsOk) colors.primary.copy(alpha = 0.12f) else colors.errorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (state.settingsOk) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.ErrorOutline
                        },
                        contentDescription = null,
                        tint = if (state.settingsOk) colors.secondary else colors.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        status,
                        color = if (state.settingsOk) colors.secondary else colors.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
