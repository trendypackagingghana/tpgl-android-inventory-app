package com.example.tpglstock.ui.history

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.tpglstock.data.StockRepository
import com.example.tpglstock.data.formatDate
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.local.MovementEntity
import com.example.tpglstock.data.local.MovementSource
import com.example.tpglstock.data.local.MovementType
import com.example.tpglstock.data.local.StockUnit
import com.example.tpglstock.ui.appViewModel
import com.example.tpglstock.ui.components.AppCard
import com.example.tpglstock.ui.components.EmptyState
import com.example.tpglstock.ui.components.MovementRow
import com.example.tpglstock.ui.components.ThinDivider
import com.example.tpglstock.ui.inventory.SearchField
import com.example.tpglstock.ui.theme.StockTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

enum class HistoryFilter(val label: String) {
    ALL("All"), IN("Stock in"), OUT("Stock out"), COUNT("Counts"), AI("AI updates"), MANUAL("Manual"), OPENING("Opening")
}

data class DayGroup(val dayStart: Long, val items: List<MovementEntity>) {
    val increase get() = items.filter { it.unit == StockUnit.PCS && it.type != MovementType.OPENING && it.delta > 0 }.sumOf { it.delta }
    val decrease get() = items.filter { it.unit == StockUnit.PCS && it.delta < 0 }.sumOf { -it.delta }
}

data class HistoryState(
    val query: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL,
    val days: List<DayGroup> = emptyList(),
    val filtered: List<MovementEntity> = emptyList(),
)

class HistoryViewModel(repo: StockRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val state: StateFlow<HistoryState> = combine(repo.movements, query, filter) { all, q, f ->
        val terms = q.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val filtered = all.filter { m ->
            val typeOk = when (f) {
                HistoryFilter.ALL -> true
                HistoryFilter.IN -> m.type == MovementType.IN
                HistoryFilter.OUT -> m.type == MovementType.OUT
                HistoryFilter.COUNT -> m.type == MovementType.COUNT
                HistoryFilter.AI -> m.source == MovementSource.AI
                HistoryFilter.MANUAL -> m.source == MovementSource.MANUAL
                HistoryFilter.OPENING -> m.type == MovementType.OPENING
            }
            val text = (m.productLabel + " " + m.note).lowercase()
            typeOk && terms.all { it in text }
        }
        HistoryState(
            query = q,
            filter = f,
            days = filtered.groupBy { startOfDay(it.timestamp) }.map { (d, items) -> DayGroup(d, items) },
            filtered = filtered,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: HistoryFilter) { filter.value = f }

    private fun startOfDay(ts: Long) = Calendar.getInstance().run {
        timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
}

private fun csv(movements: List<MovementEntity>): String = buildString {
    append("Date,Time,Product,Category,Type,Source,Previous,New,Change,Unit,Note\n")
    movements.forEach { m ->
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        append(formatDate(m.timestamp, "yyyy-MM-dd")).append(',')
        append(formatDate(m.timestamp, "HH:mm")).append(',')
        append(q(m.productLabel)).append(',')
        append(q(m.category)).append(',')
        append(m.type).append(',').append(m.source).append(',')
        append(m.previousQty).append(',').append(m.newQty).append(',').append(m.delta).append(',')
        append(m.unit).append(',')
        append(q(m.note)).append('\n')
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(onOpenProduct: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> HistoryViewModel(c.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("History", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${state.filtered.size.grouped()} records",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_SUBJECT, "TrendyPackaging Ghana stock history")
                                putExtra(Intent.EXTRA_TEXT, csv(state.filtered))
                            }
                            context.startActivity(Intent.createChooser(send, "Export history"))
                        },
                        enabled = state.filtered.isNotEmpty(),
                    ) { Icon(Icons.Rounded.IosShare, contentDescription = "Export as CSV") }
                }
                Spacer(Modifier.height(12.dp))
                SearchField(state.query, vm::setQuery, placeholder = "Search product or note")
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HistoryFilter.entries) { f ->
                    FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
                }
            }
        }
        if (state.days.isEmpty()) {
            item { EmptyState(Icons.Rounded.History, "No records", "Stock changes will be listed here by day.") }
        }
        state.days.forEach { day ->
            stickyHeader(key = "d_${day.dayStart}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatDate(day.dayStart, "EEEE d MMMM yyyy"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (day.increase > 0) {
                        Text("+${day.increase.grouped()}", style = MaterialTheme.typography.labelMedium, color = StockTheme.colors.seriesIn)
                        Spacer(Modifier.padding(4.dp))
                    }
                    if (day.decrease > 0) {
                        Text("−${day.decrease.grouped()}", style = MaterialTheme.typography.labelMedium, color = StockTheme.colors.seriesOut)
                    }
                }
            }
            item(key = "g_${day.dayStart}") {
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    Column {
                        day.items.forEachIndexed { i, m ->
                            MovementRow(m, onClick = { onOpenProduct(m.productId) })
                            if (i < day.items.lastIndex) ThinDivider()
                        }
                    }
                }
            }
        }
    }
}
