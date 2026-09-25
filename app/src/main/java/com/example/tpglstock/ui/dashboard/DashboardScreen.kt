package com.example.tpglstock.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tpglstock.data.SyncStatus
import com.example.tpglstock.data.formatDate
import com.example.tpglstock.data.formatTime
import com.example.tpglstock.data.grouped
import com.example.tpglstock.ui.appViewModel
import com.example.tpglstock.ui.components.AppCard
import com.example.tpglstock.ui.components.ProductRow
import com.example.tpglstock.ui.components.SectionHeader
import com.example.tpglstock.ui.components.ThinDivider
import com.example.tpglstock.ui.theme.StockTheme
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Key numbers only: stock on hand, this week's movement, stock health and what needs
 * reordering. Full lists live in the Inventory and History tabs.
 */
@Composable
fun DashboardScreen(
    onOpenSettings: () -> Unit,
    onOpenInventory: (String) -> Unit,
    onOpenProduct: (Long) -> Unit,
) {
    val vm = appViewModel { c, _ -> DashboardViewModel(c.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { vm.pullToRefresh()?.let { snackbar.showSnackbar(it) } } },
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(state, onOpenSettings) }
            item { HeroCard(state) }
            item { StatusStrip(state, onOpenInventory) }

            val attention = state.out + state.low
            if (attention.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Needs attention",
                        action = if (attention.size > ATTENTION_ROWS) "See all ${attention.size}" else null,
                        onAction = { onOpenInventory("attention") },
                    )
                }
                item {
                    AppCard {
                        Column {
                            val rows = attention.take(ATTENTION_ROWS)
                            rows.forEachIndexed { i, p ->
                                ProductRow(p, onClick = { onOpenProduct(p.id) })
                                if (i < rows.lastIndex) ThinDivider()
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Last 7 days") }
            item { WeekChart(state.week) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

private const val ATTENTION_ROWS = 5

@Composable
private fun Header(state: DashboardState, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("TrendyPackaging Ghana", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        state.loading -> "Loading…"
                        else -> state.lastUpdate?.let { "Updated ${lastUpdatedText(it)}" } ?: "No updates yet"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                SyncBadge(state.sync)
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shows whether every write reached Supabase and the local copy matches it. */
@Composable
private fun SyncBadge(sync: SyncStatus) {
    val (label, color) = when {
        sync.error != null -> "Not synced" to StockTheme.colors.critical
        sync.saving > 0 -> "Saving…" to StockTheme.colors.warn
        !sync.loaded -> "Syncing…" to StockTheme.colors.warn
        else -> "Synced" to StockTheme.colors.good
    }
    val description = when {
        sync.error != null -> "Not synced: ${sync.error}. Pull down to retry."
        sync.inSync -> "All changes saved to the database and up to date."
        else -> label
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                sync.error != null -> Icons.Rounded.CloudOff
                sync.inSync -> Icons.Rounded.CloudDone
                else -> Icons.Rounded.CloudSync
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

private fun lastUpdatedText(ts: Long): String {
    val today = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val sameDay = today.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        today.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) "today, ${formatTime(ts)}" else formatDate(ts, "EEE d MMM, HH:mm")
}

/** Stock on hand, plus this week's pieces in and out. */
@Composable
private fun HeroCard(state: DashboardState) {
    val weekIn = state.week.sumOf { it.increase }
    val weekOut = state.week.sumOf { it.decrease }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(StockTheme.colors.heroStart, StockTheme.colors.heroEnd)))
            .padding(20.dp),
    ) {
        Column {
            Text("Pieces on hand", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
            Text(if (state.loading) "—" else state.totalPieces.grouped(), color = Color.White, style = MaterialTheme.typography.displaySmall)
            Text(
                if (state.loading) " " else "${state.productCount.grouped()} products · ${state.rawMaterialBags.grouped()} bags raw material",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
            Spacer(Modifier.height(12.dp))
            Row {
                HeroStat("In · 7 days", if (state.loading) "—" else "+${weekIn.grouped()}", Modifier.weight(1f))
                HeroStat("Out · 7 days", if (state.loading) "—" else "−${weekOut.grouped()}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

/** Product count per stock status in one card. Each part opens the filtered inventory. */
@Composable
private fun StatusStrip(state: DashboardState, onOpenInventory: (String) -> Unit) {
    AppCard {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            val count = { n: Int -> if (state.loading) null else n }
            StatusCell("In stock", count(state.inStock), StockTheme.colors.good, Modifier.weight(1f)) { onOpenInventory("ok") }
            CellDivider()
            StatusCell("Low", count(state.low.size), StockTheme.colors.warn, Modifier.weight(1f)) { onOpenInventory("low") }
            CellDivider()
            StatusCell("Out", count(state.out.size), StockTheme.colors.critical, Modifier.weight(1f)) { onOpenInventory("out") }
        }
    }
}

@Composable
private fun StatusCell(label: String, count: Int?, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = if (count == null) "$label: loading" else "$count products $label" }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(count?.grouped() ?: "—", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CellDivider() {
    Box(
        Modifier
            .padding(vertical = 14.dp)
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Increases above the baseline, decreases below it, per day. Tap a day for its figures. */
@Composable
private fun WeekChart(week: List<DayActivity>) {
    var selected by remember(week) { mutableIntStateOf(week.indexOfLast { it.updates > 0 }.takeIf { it >= 0 } ?: week.lastIndex) }
    val inColor = StockTheme.colors.seriesIn
    val outColor = StockTheme.colors.seriesOut
    val axis = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh

    AppCard {
        Column(Modifier.padding(16.dp)) {
            if (week.isEmpty()) return@Column
            val day = week[selected.coerceIn(week.indices)]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatDate(day.dayStart, "EEEE d MMM"), style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (day.updates == 0) "No updates" else "${day.updates} update${if (day.updates == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("+${day.increase.grouped()} pcs", style = MaterialTheme.typography.labelLarge)
                    Text("−${day.decrease.grouped()} pcs", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(12.dp))

            val maxValue = week.maxOf { maxOf(it.increase, it.decrease) }.coerceAtLeast(1)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .pointerInput(week) {
                        detectTapGestures { pos ->
                            val slot = size.width / week.size
                            selected = (pos.x / slot).toInt().coerceIn(week.indices)
                        }
                    },
            ) {
                val slot = size.width / week.size
                val barW = (slot * 0.42f).coerceAtMost(28.dp.toPx())
                val mid = size.height / 2f
                val half = mid - 4.dp.toPx()
                val r = CornerRadius(4.dp.toPx())
                week.forEachIndexed { i, d ->
                    val x = slot * i + (slot - barW) / 2f
                    if (i == selected) {
                        drawRoundRect(highlight, topLeft = Offset(slot * i + 2.dp.toPx(), 0f), size = Size(slot - 4.dp.toPx(), size.height), cornerRadius = CornerRadius(10.dp.toPx()))
                    }
                    val up = half * d.increase / maxValue
                    val down = half * d.decrease / maxValue
                    if (up > 0) drawRoundRect(inColor, topLeft = Offset(x, mid - 1.dp.toPx() - up), size = Size(barW, up), cornerRadius = r)
                    if (down > 0) drawRoundRect(outColor, topLeft = Offset(x, mid + 1.dp.toPx()), size = Size(barW, down), cornerRadius = r)
                }
                drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
            }
            Row(Modifier.fillMaxWidth()) {
                week.forEachIndexed { i, d ->
                    Text(
                        formatDate(d.dayStart, "EEE"),
                        modifier = Modifier.weight(1f).clickable { selected = i }.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(inColor, "Stock increases")
                LegendItem(outColor, "Stock decreases")
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
