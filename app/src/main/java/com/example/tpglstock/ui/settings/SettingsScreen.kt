package com.example.tpglstock.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.tpglstock.TPGLApp
import com.example.tpglstock.data.AiSettings
import com.example.tpglstock.ui.components.AppCard
import com.example.tpglstock.ui.components.IconBadge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val store = (androidx.compose.ui.platform.LocalContext.current.applicationContext as TPGLApp).container.settings
    val current = store.ai.value
    var apiKey by rememberSaveable { mutableStateOf(current.apiKey) }
    var model by rememberSaveable { mutableStateOf(current.model) }
    var prompt by rememberSaveable { mutableStateOf(current.customInstructions) }
    var showKey by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(Icons.Rounded.AutoAwesome, "AI assistant", "DeepSeek reads WhatsApp updates and proposes stock changes.") {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.trim() },
                    label = { Text("DeepSeek API key (optional override)") },
                    placeholder = { Text(if (current.usesBuiltInKey) "Using key from secrets.properties" else "sk-…") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Show key")
                        }
                    },
                    supportingText = { Text(if (current.usesBuiltInKey) "Built-in key active. Leave empty to keep using it." else "No built-in key. Add one to secrets.properties or paste it here.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it.trim() },
                    label = { Text("Model") },
                    singleLine = true,
                    supportingText = { Text("Default: ${AiSettings.DEFAULT_MODEL}") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Custom instructions") },
                    supportingText = { Text("Teach the assistant your product names, abbreviations and message habits.") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { prompt = AiSettings.DEFAULT_INSTRUCTIONS; model = AiSettings.DEFAULT_MODEL }) {
                        Text("Reset to defaults")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        store.save(AiSettings(apiKey, model, prompt))
                        scope.launch { snackbar.showSnackbar("Settings saved") }
                    }) { Text("Save") }
                }
            }

            SettingsSection(Icons.Rounded.Storage, "Data", "Stock is stored locally on this phone.") {
                Text(
                    "Cloud sync (Supabase) can be connected later; the app is structured so the local database can be swapped without changing screens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsSection(Icons.Rounded.Business, "TrendyPackaging Ghana", "Stock keeping · version 1.0") {}
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(icon: ImageVector, title: String, subtitle: String, content: @Composable () -> Unit) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}
