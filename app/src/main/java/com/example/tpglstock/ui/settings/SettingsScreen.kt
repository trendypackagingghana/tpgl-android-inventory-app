package com.example.tpglstock.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import com.example.tpglstock.data.AiSettings
import com.example.tpglstock.data.SettingsStore
import com.example.tpglstock.data.StockRepository
import com.example.tpglstock.data.SyncStatus
import com.example.tpglstock.data.local.MovementType
import com.example.tpglstock.data.startOfDay
import com.example.tpglstock.data.writeStockPdf
import com.example.tpglstock.ui.appViewModel
import com.example.tpglstock.ui.components.InkButton
import com.example.tpglstock.ui.components.LabeledField
import com.example.tpglstock.ui.components.LocalToast
import com.example.tpglstock.ui.components.Panel
import com.example.tpglstock.ui.components.RowDivider
import com.example.tpglstock.ui.dashboard.Avatar
import com.example.tpglstock.ui.theme.StockTheme
import com.example.tpglstock.ui.theme.mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class YouViewModel(private val repo: StockRepository, val settings: SettingsStore) : ViewModel() {
    private val weekStart = startOfDay(System.currentTimeMillis()) - 6 * 24 * 60 * 60 * 1000L

    val weekUpdates: StateFlow<Int> = repo.movements
        .map { list -> list.count { it.timestamp >= weekStart && it.type != MovementType.OPENING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val sync: StateFlow<SyncStatus> = repo.status

    /** Refreshes from Supabase, then writes all products to a PDF. */
    suspend fun stockPdf(context: Context): File {
        val products = repo.allProducts()
        return withContext(Dispatchers.IO) { writeStockPdf(context, products) }
    }
}

/** Opens WhatsApp (or WhatsApp Business) with the PDF attached; falls back to the share sheet. */
private fun shareViaWhatsApp(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        try {
            context.startActivity(Intent(send).setPackage(pkg))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
    context.startActivity(Intent.createChooser(send, "Share stock"))
}

/** Profile, assistant settings and app info. */
@Composable
fun SettingsScreen() {
    val vm = appViewModel { c, _ -> YouViewModel(c.repository, c.settings) }
    val store = vm.settings
    val name by store.userName.collectAsStateWithLifecycle()
    val weekUpdates by vm.weekUpdates.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val current = store.ai.value
    var apiKey by rememberSaveable { mutableStateOf(current.apiKey) }
    var model by rememberSaveable { mutableStateOf(current.model) }
    var sharing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toast = LocalToast.current
    val c = StockTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("You", style = MaterialTheme.typography.displaySmall)

        Panel(shape = RoundedCornerShape(24.dp), padding = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name, 56)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name.ifBlank { "Add your name" }, style = MaterialTheme.typography.titleLarge)
                    Text("$weekUpdates team update${if (weekUpdates == 1) "" else "s"} this week", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = c.muted)
                }
            }
            Spacer(Modifier.size(14.dp))
            LabeledField(
                "What should we call you?",
                name,
                store::saveUserName,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            Spacer(Modifier.size(14.dp))
            InkButton(
                if (sharing) "Preparing PDF…" else "Share current stock",
                onClick = {
                    sharing = true
                    scope.launch {
                        runCatching { vm.stockPdf(context) }
                            .onSuccess { shareViaWhatsApp(context, it) }
                            .onFailure { toast.show("Couldn't create the stock PDF", error = true) }
                        sharing = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !sharing,
                icon = Icons.Rounded.IosShare,
            )
        }

        Panel(shape = RoundedCornerShape(24.dp), padding = PaddingValues(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Assistant", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    val configured = AiSettings(apiKey = apiKey).isConfigured
                    Text(
                        if (configured) "Key active" else "No key",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (configured) c.ok.fg else c.out.fg,
                    )
                }
                Text(
                    "Reads WhatsApp updates and suggests stock changes.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = c.muted,
                )
                LabeledField("Model", model, { model = it.trim() }, textStyle = mono(14.sp, androidx.compose.ui.text.font.FontWeight.Normal))
                LabeledField(
                    "DeepSeek API key",
                    apiKey,
                    { apiKey = it.trim() },
                    placeholder = if (current.usesBuiltInKey) "Using the built-in key" else "sk-…",
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = mono(14.sp, androidx.compose.ui.text.font.FontWeight.Normal),
                    supporting = if (current.usesBuiltInKey) "Leave empty to keep the built-in key." else "Add a key here or in secrets.properties.",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Reset to defaults",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                        color = c.muted,
                        modifier = Modifier
                            .clickable { model = AiSettings.DEFAULT_MODEL }
                            .padding(vertical = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    InkButton("Save", onClick = {
                        store.save(AiSettings(apiKey, model))
                        toast.show("Assistant settings saved")
                    }, height = 40.dp, shape = RoundedCornerShape(12.dp))
                }
            }
        }

        Panel(shape = RoundedCornerShape(24.dp), padding = PaddingValues(vertical = 4.dp)) {
            val (syncIcon, syncTint, syncText) = when {
                sync.error != null -> Triple(Icons.Rounded.CloudOff, c.out.fg, "Not synced · pull down on Today to retry")
                sync.inSync -> Triple(Icons.Rounded.CloudDone, c.ok.fg, "Every change saved · shared with the team")
                !sync.loaded -> Triple(Icons.Rounded.CloudSync, c.low.fg, "Connecting…")
                else -> Triple(Icons.Rounded.CloudSync, c.low.fg, "Saving changes…")
            }
            InfoRow(syncIcon, syncTint, "Cloud sync", syncText)
            RowDivider()
            InfoRow(Icons.Rounded.Storefront, c.muted, "TrendyPackaging Ghana", "Stock keeping · version 1.0")
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, tint: Color, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = StockTheme.colors.muted)
        }
    }
}
