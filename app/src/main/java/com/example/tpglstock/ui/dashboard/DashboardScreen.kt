package com.example.tpglstock.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tpglstock.data.dayLabel
import com.example.tpglstock.data.formatDate
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.kind
import com.example.tpglstock.data.levelFraction
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.quantityText
import com.example.tpglstock.data.shortTitle
import com.example.tpglstock.data.status
import com.example.tpglstock.ui.appViewModel
import com.example.tpglstock.ui.components.InkButton
import com.example.tpglstock.ui.components.LevelBar
import com.example.tpglstock.ui.components.ListPanel
import com.example.tpglstock.ui.components.LocalToast
import com.example.tpglstock.ui.components.Panel
import com.example.tpglstock.ui.components.ProductLine
import com.example.tpglstock.ui.components.SectionTitle
import com.example.tpglstock.ui.components.StatusChip
import com.example.tpglstock.ui.components.Swatch
import com.example.tpglstock.ui.components.SyncPill
import com.example.tpglstock.ui.components.statusTone
import com.example.tpglstock.ui.theme.DisplayFamily
import com.example.tpglstock.ui.theme.StockTheme
import com.example.tpglstock.ui.theme.mono
import kotlinx.coroutines.launch
import java.util.Calendar

/** Home: what needs restocking, raw materials, this week's movement and the busiest products. */
@Composable
fun DashboardScreen(
    onOpenYou: () -> Unit,
    onSeeAttention: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onRestock: (Long) -> Unit,
) {
    val vm = appViewModel { c, _ -> DashboardViewModel(c.repository, c.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { vm.pullToRefresh()?.let { toast.show(it, error = true) } } },
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            val pad = Modifier.padding(horizontal = 20.dp)
            item { Header(state, onOpenYou, pad) }
            item { Greeting(state, pad) }
            if (state.attention.isNotEmpty()) {
                item { NeedsYou(state.attention, onSeeAttention, onOpenProduct, onRestock) }
            }
            item { RawMaterials(state, onOpenProduct, pad) }
            item { WeekCard(state.week, pad) }
            if (state.movers.isNotEmpty()) {
                item {
                    Column(pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Moving fast")
                        ListPanel(state.movers) { m -> ProductLine(m.product, onClick = { onOpenProduct(m.product.id) }, detail = m.detail) }
                    }
                }
            }
        }
    }
}

@Composable
fun Avatar(name: String, size: Int, onClick: (() -> Unit)? = null) {
    val c = StockTheme.colors
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(c.accent)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open profile", onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(name),
            color = c.onAccent,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = (size * 0.4f).sp),
        )
    }
}

fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "TP" }

@Composable
private fun Header(state: DashboardState, onOpenYou: () -> Unit, modifier: Modifier) {
    val c = StockTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Avatar(state.name, 40, onOpenYou)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("TrendyPackaging Ghana", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), maxLines = 1)
            Text(
                when {
                    state.loading -> "Loading…"
                    else -> state.lastUpdate?.let { "Last updated ${lastUpdatedText(it)}" } ?: "No updates yet"
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        SyncPill(state.sync)
    }
}

/** The data date of the newest movement; the time is left out because imported updates are dated by day. */
private fun lastUpdatedText(ts: Long): String = when (val d = dayLabel(ts)) {
    "Today", "Yesterday" -> d.lowercase()
    else -> formatDate(ts, "EEE d MMM")
}

@Composable
private fun Greeting(state: DashboardState, modifier: Modifier) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hello = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val n = state.attention.size
    val line = when {
        state.loading -> "Loading stock…"
        n == 0 -> "Everything is in stock. Nice work."
        else -> buildString {
            append("$n product${if (n == 1) " needs" else "s need"} you")
            if (state.outCount > 0) append(" — ${state.outCount} ${if (state.outCount == 1) "is" else "are"} out")
            append(". ${state.todayUpdates} update${if (state.todayUpdates == 1) "" else "s"} today.")
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$hello, ${state.name.ifBlank { "there" }}.", style = MaterialTheme.typography.displaySmall)
        Text(line, style = MaterialTheme.typography.bodyLarge, color = StockTheme.colors.muted)
    }
}

@Composable
private fun NeedsYou(products: List<ProductEntity>, onSeeAll: () -> Unit, onOpen: (Long) -> Unit, onRestock: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Needs you", Modifier.padding(horizontal = 20.dp), action = "See all", onAction = onSeeAll)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(products, key = { it.id }) { p -> AttentionCard(p, { onOpen(p.id) }, { onRestock(p.id) }) }
        }
    }
}

@Composable
private fun AttentionCard(p: ProductEntity, onOpen: () -> Unit, onRestock: () -> Unit) {
    val c = StockTheme.colors
    Column(
        Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Swatch(p, 28.dp)
            Spacer(Modifier.weight(1f))
            StatusChip(p.status)
        }
        Column {
            Text(p.shortTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.kind.ifBlank { " " }, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(p.quantityText(), style = mono(20.sp), maxLines = 1)
        InkButton("Restock", onRestock, Modifier.fillMaxWidth(), height = 36.dp, shape = RoundedCornerShape(12.dp), icon = Icons.Rounded.Add)
    }
}

@Composable
private fun RawMaterials(state: DashboardState, onOpen: (Long) -> Unit, modifier: Modifier) {
    val c = StockTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.hero)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Raw materials", style = MaterialTheme.typography.titleMedium, color = c.onHero, modifier = Modifier.weight(1f))
            Text("bags on hand", style = MaterialTheme.typography.bodySmall, color = c.heroMuted)
        }
        when {
            state.loading -> Text("—", style = mono(30.sp), color = c.onHero)
            state.rawMaterials.isEmpty() -> Text("No raw materials recorded", style = MaterialTheme.typography.bodyMedium, color = c.heroMuted)
            else -> state.rawMaterials.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { p ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpen(p.id) }
                                .semantics(mergeDescendants = true) { contentDescription = "${p.shortTitle}: ${p.quantityText()}" },
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(p.quantity.grouped(), style = mono(30.sp).copy(lineHeight = 30.sp), color = c.onHero, maxLines = 1)
                            LevelBar(p.levelFraction, statusTone(p.status).onHero, track = c.heroTrack)
                            Text(p.shortTitle, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp), color = c.heroMuted, maxLines = 2)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Increases above the line, decreases below it, per day. Tap a day for its figures. */
@Composable
private fun WeekCard(week: List<DayActivity>, modifier: Modifier) {
    if (week.isEmpty()) return
    val c = StockTheme.colors
    var selected by remember(week) { mutableIntStateOf(week.indexOfLast { it.updates > 0 }.takeIf { it >= 0 } ?: week.lastIndex) }
    val day = week[selected.coerceIn(week.indices)]
    val max = week.maxOf { maxOf(it.increase, it.decrease) }.coerceAtLeast(1)

    Panel(modifier, shape = RoundedCornerShape(24.dp), padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(dayLabel(day.dayStart), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (day.updates == 0) "No updates" else "${day.updates} update${if (day.updates == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = c.muted,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("+${day.increase.grouped()} pcs", style = mono(13.sp, FontWeight.Normal), color = c.inFg)
                Text("−${day.decrease.grouped()} pcs", style = mono(13.sp, FontWeight.Normal), color = c.outFg)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.forEachIndexed { i, d ->
                val on = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) c.paper else Color.Transparent)
                        .clickable { selected = i }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${dayLabel(d.dayStart)}: plus ${d.increase} pieces, minus ${d.decrease} pieces"
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(56.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        val h = 54f * d.increase / max
                        if (h > 0) Box(Modifier.width(14.dp).height(h.dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(4.dp, 4.dp, 1.dp, 1.dp)).background(c.chartUp))
                    }
                    Box(Modifier.padding(vertical = 2.dp).height(1.dp).fillMaxWidth().background(c.border))
                    Box(Modifier.height(40.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        val h = 38f * d.decrease / max
                        if (h > 0) Box(Modifier.width(14.dp).height(h.dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(1.dp, 1.dp, 4.dp, 4.dp)).background(c.chartDown))
                    }
                    Text(
                        if (i == week.lastIndex) "Today" else formatDate(d.dayStart, "EEE"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (on) c.ink else c.faint,
                        modifier = Modifier.padding(top = 6.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
