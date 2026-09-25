package com.example.tpglstock.ui.addstock

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tpglstock.TPGLApp
import com.example.tpglstock.data.ChangeMode
import com.example.tpglstock.data.formatDate
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.toTitleCase
import com.example.tpglstock.data.unitLabel
import com.example.tpglstock.ui.appViewModel
import com.example.tpglstock.ui.components.BackBar
import com.example.tpglstock.ui.components.InkButton
import com.example.tpglstock.ui.components.RowDivider
import com.example.tpglstock.ui.theme.StockTheme
import com.example.tpglstock.ui.theme.mono
import kotlinx.coroutines.launch

/** Paste a WhatsApp update, review the suggested changes, save the ones that look right. */
@Composable
fun AssistantScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val vm = appViewModel { c, _ -> AiAssistantViewModel(c.repository, c.ai, c.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val name by (LocalContext.current.applicationContext as TPGLApp).container.settings.userName.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val c = StockTheme.colors

    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) listState.animateScrollToItem(state.items.size)
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        BackBar(onBack, Modifier.padding(horizontal = 10.dp).padding(top = 4.dp), title = "Assistant") {
            if (state.items.isNotEmpty() && !state.busy) {
                Text(
                    "Clear",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    color = c.muted,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = vm::clear).padding(8.dp),
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "intro") {
                if (!settings.isConfigured) SetupBubble(onOpenSettings) else IntroBubble(name)
            }
            items(state.items, key = { it.id }) { item ->
                when (item) {
                    is ChatItem.User -> UserBubble(item.text)
                    is ChatItem.Thinking -> ThinkingRow()
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

private val BotShape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
private val UserShape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp)

@Composable
private fun AssistantAvatar() {
    val c = StockTheme.colors
    Box(Modifier.size(30.dp).clip(CircleShape).background(c.hero), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AssistantRow(content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        AssistantAvatar()
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun BotBubble(content: @Composable () -> Unit) {
    Box(Modifier.widthIn(max = 300.dp).clip(BotShape).background(StockTheme.colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
        content()
    }
}

@Composable
private fun IntroBubble(name: String) {
    AssistantRow {
        BotBubble {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Hi ${name.ifBlank { "there" }}. Paste an update from the team chat and I'll match it to your products. Nothing is saved until you say so.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Several days at once is fine: productions are added, sales deducted, each on its own date. New products and possible duplicates are flagged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StockTheme.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun SetupBubble(onOpenSettings: () -> Unit) {
    AssistantRow {
        BotBubble {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("I need a DeepSeek API key before I can read updates.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Add it to secrets.properties and rebuild, or paste it on the You tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StockTheme.colors.muted,
                )
                InkButton("Open You", onOpenSettings, height = 40.dp, shape = RoundedCornerShape(12.dp))
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    var expanded by remember { mutableStateOf(false) }
    val c = StockTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            color = c.onInk,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 8,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(UserShape)
                .background(c.ink)
                .clickable(onClickLabel = if (expanded) "Collapse" else "Expand") { expanded = !expanded }
                .animateContentSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ThinkingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistantAvatar()
        Spacer(Modifier.width(10.dp))
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = StockTheme.colors.muted)
        Spacer(Modifier.width(8.dp))
        Text("Reading the message…", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = StockTheme.colors.muted)
    }
}

@Composable
private fun ErrorBubble(item: ChatItem.Error, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    val c = StockTheme.colors
    AssistantRow {
        Column(
            Modifier.widthIn(max = 300.dp).clip(BotShape).background(c.out.bg).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.out.fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(item.message, style = MaterialTheme.typography.bodyMedium, color = c.ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextAction("Try again", onRetry)
                if (item.message.contains("Settings")) TextAction("Open You", onOpenSettings)
            }
        }
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = StockTheme.colors.ink,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
    )
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
    val c = StockTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (p.summary.isNotBlank()) AssistantRow { BotBubble { Text(p.summary, style = MaterialTheme.typography.bodyMedium) } }

        Column(Modifier.padding(start = 40.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)) {
            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append("${p.lines.size} entr${if (p.lines.size == 1) "y" else "ies"}")
                        if (p.duplicateCount > 0) append(" · ${p.duplicateCount} possible duplicate${if (p.duplicateCount == 1) "" else "s"}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = c.muted,
                    modifier = Modifier.weight(1f),
                )
                if (pending && p.lines.size > 1) {
                    val allOn = p.lines.all { it.selected }
                    Text(
                        if (allOn) "Select none" else "Select all",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                        color = c.ink,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSelectAll(!allOn) }.padding(6.dp),
                    )
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
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
                )
                lines.forEach { line ->
                    RowDivider()
                    ProposalLineRow(line, enabled = pending, onToggle = { onToggle(line.index) })
                }
            }

            if (p.questions.isNotEmpty()) {
                RowDivider()
                SectionLabel(Icons.AutoMirrored.Rounded.HelpOutline, "Please check")
                p.questions.forEach { q ->
                    Text("• $q", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
                }
                Text(
                    "Reply below to clarify, e.g. “the 250ml jar is Clear”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }

            p.applyError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.out.fg, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            }

            Box(Modifier.padding(12.dp)) {
                when (p.status) {
                    ProposalStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Discard",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.muted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, c.border, RoundedCornerShape(14.dp))
                                .clickable(role = Role.Button, onClick = onDiscard)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        InkButton(
                            if (p.selectedCount == 0) "Nothing selected" else "Save ${p.selectedCount} change${if (p.selectedCount == 1) "" else "s"}",
                            onApply,
                            Modifier.weight(1f),
                            enabled = p.selectedCount > 0,
                        )
                    }
                    ProposalStatus.APPLYING -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.ink)
                        Spacer(Modifier.width(8.dp))
                        Text("Saving…", style = MaterialTheme.typography.labelLarge)
                    }
                    ProposalStatus.APPLIED -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.ok.fg, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Saved ${p.appliedCount} change${if (p.appliedCount == 1) "" else "s"}. You can find them in Activity.",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    ProposalStatus.DISCARDED -> Text("Discarded. Nothing was saved.", style = MaterialTheme.typography.labelLarge, color = c.muted)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(icon: ImageVector, text: String) {
    val c = StockTheme.colors
    Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = c.low.fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.low.fg)
    }
}

@Composable
private fun ProposalLineRow(line: ProposalLine, enabled: Boolean, onToggle: () -> Unit) {
    val e = line.entry
    val delta = line.after - line.before
    val c = StockTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (line.selected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = if (line.selected) "Selected" else "Not selected",
            tint = if (line.selected) c.ink else c.faint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(line.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${line.before.grouped()} → ${line.after.grouped()} ${unitLabel(line.unit, line.after)}",
                style = mono(12.sp, FontWeight.Normal),
                color = c.muted,
            )
            Text(
                activityNote(e) + if (line.newProduct != null) " · new product" else "",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
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
        Text(
            when (e.mode) {
                ChangeMode.ADD -> "+${e.quantity.grouped()}"
                ChangeMode.REMOVE -> "−${e.quantity.grouped()}"
                ChangeMode.SET -> "= ${e.quantity.grouped()}"
            },
            style = mono(13.sp),
            color = when {
                !line.selected -> c.faint
                e.mode == ChangeMode.ADD || delta > 0 -> c.inFg
                else -> c.outFg
            },
        )
    }
}

@Composable
private fun Flag(text: String) {
    val c = StockTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = c.low.fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = c.low.fg)
    }
}

@Composable
private fun Composer(value: String, onChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val c = StockTheme.colors
    Column(Modifier.fillMaxWidth().background(c.paper).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                minLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.weight(1f).heightIn(max = 180.dp),
                decorationBox = { inner ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(Modifier.weight(1f).padding(bottom = 6.dp)) {
                            if (value.isEmpty()) Text("Paste a WhatsApp update", style = MaterialTheme.typography.bodyMedium, color = c.faint)
                            inner()
                        }
                        if (value.isEmpty()) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = enabled, onClickLabel = "Paste from clipboard") {
                                        scope.launch {
                                            val clip = clipboard.getClipEntry()?.clipData
                                            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                                            if (!text.isNullOrBlank()) onChange(text)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste from clipboard", tint = c.muted, modifier = Modifier.size(20.dp)) }
                        }
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            val canSend = enabled && value.isNotBlank()
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (canSend) c.accent else c.disabledBg)
                    .clickable(enabled = canSend, role = Role.Button, onClickLabel = "Send", onClick = onSend),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "Send", tint = if (canSend) c.onAccent else c.disabledFg, modifier = Modifier.size(22.dp)) }
        }
    }
}
