package com.example.tpglstock.ui.addstock

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tpglstock.data.ChangeMode
import com.example.tpglstock.data.formatDate
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.toTitleCase
import com.example.tpglstock.data.unitLabel
import com.example.tpglstock.ui.components.AppCard
import com.example.tpglstock.ui.components.IconBadge
import com.example.tpglstock.ui.theme.StockTheme
import kotlinx.coroutines.launch

@Composable
internal fun AiAssistant(vm: AiAssistantViewModel, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) listState.animateScrollToItem(state.items.size)
    }

    Column(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                if (!settings.isConfigured) SetupCard(onOpenSettings) else IntroBubble()
            }
            items(state.items, key = { it.id }) { item ->
                when (item) {
                    is ChatItem.User -> UserBubble(item.text)
                    is ChatItem.Thinking -> ThinkingBubble()
                    is ChatItem.Error -> ErrorBubble(item, onRetry = { vm.retry(item) }, onOpenSettings = onOpenSettings)
                    is ChatItem.Proposal -> ProposalCard(
                        item,
                        onToggle = { vm.toggleLine(item.id, it) },
                        onSelectAll = { vm.setAll(item.id, it) },
                        onApply = { vm.apply(item.id) },
                        onDiscard = { vm.discard(item.id) },
                    )
                }
            }
        }
        Composer(
            value = state.input,
            onChange = vm::setInput,
            onSend = { vm.send() },
            enabled = settings.isConfigured && !state.busy,
        )
    }
}

@Composable
private fun AssistantAvatar() {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AssistantRow(content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        AssistantAvatar()
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun IntroBubble() {
    AssistantRow {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Paste your stock update from WhatsApp.", style = MaterialTheme.typography.titleSmall)
                Text(
                    "I'll match each line to a product and show the changes day by day. Nothing is saved until you tap Apply.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Paste several days at once: day and night productions are added, sales are deducted, each on its own date. New products and possible duplicates are flagged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Key, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Connect the AI assistant", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Add your DeepSeek API key to secrets.properties and rebuild, or paste it in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenSettings) { Text("Open Settings") }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 320.dp).clickable { expanded = !expanded }.animateContentSize(),
        ) {
            Text(
                text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun ThinkingBubble() {
    AssistantRow {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Reading your update…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ErrorBubble(item: ChatItem.Error, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    AssistantRow {
        Surface(shape = RoundedCornerShape(20.dp), color = StockTheme.colors.critical.copy(alpha = 0.1f)) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = StockTheme.colors.critical)
                    Spacer(Modifier.width(8.dp))
                    Text(item.message, style = MaterialTheme.typography.bodyMedium)
                }
                Row {
                    TextButton(onClick = onRetry) { Text("Try again") }
                    if (item.message.contains("Settings")) TextButton(onClick = onOpenSettings) { Text("Settings") }
                }
            }
        }
    }
}

@Composable
private fun ProposalCard(
    p: ChatItem.Proposal,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
) {
    val pending = p.status == ProposalStatus.PENDING
    AssistantRow {
        AppCard {
            Column(Modifier.padding(vertical = 14.dp)) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    if (p.summary.isNotBlank()) Text(p.summary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildString {
                                append("${p.lines.size} entr${if (p.lines.size == 1) "y" else "ies"}")
                                if (p.duplicateCount > 0) append(" · ${p.duplicateCount} possible duplicate${if (p.duplicateCount == 1) "" else "s"}")
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (pending && p.lines.size > 1) {
                            val allOn = p.lines.all { it.selected }
                            TextButton(onClick = { onSelectAll(!allOn) }) { Text(if (allOn) "Select none" else "Select all") }
                        }
                    }
                }

                val usedNew = p.newProducts.filter { n -> p.lines.any { it.selected && it.newProduct?.key == n.key } }
                if (usedNew.isNotEmpty()) {
                    SectionLabel(Icons.Rounded.NewReleases, "New products to create")
                    usedNew.forEach { n ->
                        Text(
                            "• " + listOf(n.category.toTitleCase(), n.size, n.name.takeUnless { it == "Standard" } ?: "")
                                .filter { it.isNotBlank() }.joinToString(" · ") +
                                if (n.pcsPerBag > 0) " (${n.pcsPerBag.grouped()} pcs/bag)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                        )
                    }
                }

                // Group by day, in the order things happened.
                val byDay = p.lines.sortedWith(compareBy({ it.timestamp ?: Long.MAX_VALUE }, { it.index })).groupBy { it.dayStart }
                byDay.forEach { (day, lines) ->
                    Text(
                        day?.let { formatDate(it, "EEEE d MMM") } ?: "Undated · saved as now",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 2.dp),
                    )
                    lines.forEach { line ->
                        ProposalLineRow(line, enabled = pending, onToggle = { onToggle(line.index) })
                    }
                }

                if (p.questions.isNotEmpty()) {
                    SectionLabel(Icons.Rounded.HelpOutline, "Please check")
                    p.questions.forEach { q ->
                        Text(
                            "• $q",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        "Reply below to clarify — e.g. “the 250ml jar is Clear”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }

                p.applyError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Box(Modifier.padding(horizontal = 14.dp)) {
                    when (p.status) {
                        ProposalStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onDiscard) { Text("Discard") }
                            Button(onClick = onApply, enabled = p.selectedCount > 0, modifier = Modifier.weight(1f)) {
                                Text(if (p.selectedCount == 0) "Nothing selected" else "Apply ${p.selectedCount} entr${if (p.selectedCount == 1) "y" else "ies"}")
                            }
                        }
                        ProposalStatus.APPLYING -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Saving…")
                        }
                        ProposalStatus.APPLIED -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = StockTheme.colors.good)
                            Spacer(Modifier.width(8.dp))
                            Text("Saved ${p.appliedCount} update${if (p.appliedCount == 1) "" else "s"} to stock", style = MaterialTheme.typography.labelLarge)
                        }
                        ProposalStatus.DISCARDED -> Text(
                            "Discarded — no changes saved",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = StockTheme.colors.warn, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = StockTheme.colors.warn)
    }
}

@Composable
private fun ProposalLineRow(line: ProposalLine, enabled: Boolean, onToggle: () -> Unit) {
    val e = line.entry
    val delta = line.after - line.before
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(start = 4.dp, end = 14.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = line.selected, onCheckedChange = { onToggle() }, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(line.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                activityNote(e) + if (line.newProduct != null) " · new product" else "",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                line.duplicate == DuplicateKind.IN_HISTORY -> Flag("Already recorded for this day?")
                line.duplicate == DuplicateKind.IN_MESSAGE -> Flag("Repeated in the message?")
                line.selected && line.exceedsStock -> Flag("More than the ${line.before.grouped()} in stock")
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                when (e.mode) {
                    ChangeMode.ADD -> "+${e.quantity.grouped()}"
                    ChangeMode.REMOVE -> "−${e.quantity.grouped()}"
                    ChangeMode.SET -> "= ${e.quantity.grouped()}"
                },
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    !line.selected -> muted
                    e.mode == ChangeMode.ADD || delta > 0 -> StockTheme.colors.seriesIn
                    else -> StockTheme.colors.seriesOut
                },
            )
            Text(
                "→ ${line.after.grouped()} ${unitLabel(line.unit, line.after)}",
                style = MaterialTheme.typography.labelMedium,
                color = muted,
            )
        }
    }
}

@Composable
private fun Flag(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = StockTheme.colors.warn, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = StockTheme.colors.warn)
    }
}

@Composable
private fun Composer(value: String, onChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().imePadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(
                onClick = {
                    scope.launch {
                        val clip = clipboard.getClipEntry()?.clipData
                        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                        if (!text.isNullOrBlank()) onChange(if (value.isBlank()) text else value + "\n" + text)
                    }
                },
                enabled = enabled,
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste from clipboard")
            }
            TextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 180.dp),
                placeholder = { Text("Paste or type a stock update…") },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                enabled = enabled,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = onSend, enabled = enabled && value.isNotBlank(), modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
            }
        }
    }
}
