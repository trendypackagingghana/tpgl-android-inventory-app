package com.example.tpglstock.ui.history

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.tpglstock.data.StockRepository
import com.example.tpglstock.data.dayLabel
import com.example.tpglstock.data.formatDate
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.kind
import com.example.tpglstock.data.local.MovementEntity
import com.example.tpglstock.data.local.MovementSource
import com.example.tpglstock.data.local.MovementType
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.local.StockUnit
import com.example.tpglstock.data.shortTitle
import com.example.tpglstock.data.startOfDay
import com.example.tpglstock.ui.appViewModel
import com.example.tpglstock.ui.components.EmptyNote
import com.example.tpglstock.ui.components.FilterPillRow
import com.example.tpglstock.ui.components.GroupHeader
import com.example.tpglstock.ui.components.ListPanel
import com.example.tpglstock.ui.components.MovementItem
import com.example.tpglstock.ui.components.PillButton
import com.example.tpglstock.ui.components.ScreenTitle
import com.example.tpglstock.ui.components.SearchBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class HistoryFilter(val label: String) {
    ALL("All"), IN("Stock in"), OUT("Stock out"), COUNT("Counts"), AI("Assistant"), MANUAL("Manual"), OPENING("Opening")
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
    val products: Map<Long, ProductEntity> = emptyMap(),
)

class HistoryViewModel(repo: StockRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val state: StateFlow<HistoryState> = combine(repo.movements, repo.products, query, filter) { all, products, q, f ->
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
            products = products.associateBy { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: HistoryFilter) { filter.value = f }
}

/** "500ml Clear · Spray Bottles", falling back to the label saved with the movement. */
fun movementTitle(m: MovementEntity, product: ProductEntity?): String =
    product?.let { listOf(it.shortTitle, it.kind).filter { s -> s.isNotBlank() }.joinToString(" · ") } ?: m.productLabel

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

@Composable
fun HistoryScreen(onOpenProduct: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> HistoryViewModel(c.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pad = Modifier.padding(horizontal = 20.dp)

    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val n = state.filtered.size
                ScreenTitle("Activity", "${n.grouped()} change${if (n == 1) "" else "s"}") {
                    PillButton(
                        Icons.Rounded.IosShare,
                        "Export",
                        enabled = state.filtered.isNotEmpty(),
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_SUBJECT, "TrendyPackaging Ghana stock history")
                                putExtra(Intent.EXTRA_TEXT, csv(state.filtered))
                            }
                            context.startActivity(Intent.createChooser(send, "Export history"))
                        },
                    )
                }
                SearchBox(state.query, vm::setQuery, "Search product or note")
            }
        }
        item {
            FilterPillRow(HistoryFilter.entries.map { it to it.label }, state.filter, vm::setFilter)
        }
        if (state.days.isEmpty()) {
            item { EmptyNote("No changes yet. Stock updates will be listed here by day.", pad) }
        }
        state.days.forEach { day ->
            item(key = "d_${day.dayStart}") {
                Column(pad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupHeader(dayLabel(day.dayStart), "+${day.increase.grouped()} · −${day.decrease.grouped()}")
                    ListPanel(day.items) { m ->
                        MovementItem(m, movementTitle(m, state.products[m.productId]), onClick = { onOpenProduct(m.productId) })
                    }
                }
            }
        }
    }
}
