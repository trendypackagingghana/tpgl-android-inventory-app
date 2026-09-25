package com.example.tpglstock.ui.addstock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tpglstock.ui.appViewModel
import com.example.tpglstock.ui.components.SegmentedToggle

private enum class EntryMode { MANUAL, AI }

@Composable
fun AddStockScreen(onOpenSettings: () -> Unit) {
    val manualVm = appViewModel { c, h -> ManualEntryViewModel(c.repository, h) }
    val aiVm = appViewModel { c, _ -> AiAssistantViewModel(c.repository, c.ai, c.settings) }
    val aiState by aiVm.state.collectAsStateWithLifecycle()

    var mode by rememberSaveable {
        mutableStateOf(if (manualVm.initialMode == "ai") EntryMode.AI else EntryMode.MANUAL)
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Add stock", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (mode == EntryMode.MANUAL) "Record stock in, out or a count" else "Paste a WhatsApp update — review before saving",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (mode == EntryMode.AI && aiState.items.isNotEmpty()) {
                    IconButton(onClick = aiVm::clear, enabled = !aiState.busy) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear conversation")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SegmentedToggle(
                options = listOf(EntryMode.MANUAL to "By product", EntryMode.AI to "AI assistant"),
                selected = mode,
                onSelect = { mode = it },
                icons = mapOf(EntryMode.MANUAL to Icons.Rounded.EditNote, EntryMode.AI to Icons.Rounded.AutoAwesome),
            )
        }
        when (mode) {
            EntryMode.MANUAL -> ManualEntry(manualVm, Modifier.fillMaxWidth().weight(1f))
            EntryMode.AI -> AiAssistant(aiVm, onOpenSettings, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

internal fun SavedStateHandle.initialMode(): String = get<String>("mode").orEmpty()
