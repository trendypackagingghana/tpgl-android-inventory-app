package com.example.tpglstock.ui.inventory

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.tpglstock.data.StockRepository
import com.example.tpglstock.data.StockStatus
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.label
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.local.StockUnit
import com.example.tpglstock.data.status
import com.example.tpglstock.data.toTitleCase
import com.example.tpglstock.ui.appViewModel
import com.example.tpglstock.ui.components.EmptyNote
import com.example.tpglstock.ui.components.FilterPillRow
import com.example.tpglstock.ui.components.GroupHeader
import com.example.tpglstock.ui.components.ListPanel
import com.example.tpglstock.ui.components.PillButton
import com.example.tpglstock.ui.components.ProductItem
import com.example.tpglstock.ui.components.ScreenTitle
import com.example.tpglstock.ui.components.SearchBox
import com.example.tpglstock.ui.components.SegmentTabs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class StatusFilter { ALL, ATTENTION, LOW, OUT }

data class InventoryState(
    val query: String = "",
    val status: StatusFilter = StatusFilter.ALL,
    val category: String? = null,
    val categories: List<String> = emptyList(),
    val groups: List<Pair<String, List<ProductEntity>>> = emptyList(),
    val productCount: Int = 0,
    val piecesOnHand: Long = 0,
    val attentionCount: Int = 0,
)

class InventoryViewModel(repo: StockRepository, handle: SavedStateHandle) : ViewModel() {
    private val query = MutableStateFlow("")
    private val status = MutableStateFlow(
        when (handle.get<String>("filter")) {
            "low" -> StatusFilter.LOW
            "out" -> StatusFilter.OUT
            "attention" -> StatusFilter.ATTENTION
            else -> StatusFilter.ALL
        },
    )
    private val category = MutableStateFlow<String?>(null)

    val state: StateFlow<InventoryState> = combine(repo.products, query, status, category) { products, q, s, c ->
        val terms = q.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val filtered = products.filter { p ->
            val statusOk = when (s) {
                StatusFilter.ALL -> true
                StatusFilter.ATTENTION -> p.status != StockStatus.OK
                StatusFilter.LOW -> p.status == StockStatus.LOW
                StatusFilter.OUT -> p.status == StockStatus.OUT
            }
            val text = p.label.lowercase()
            statusOk && (c == null || p.category == c) && terms.all { it in text }
        }
        InventoryState(
            query = q,
            status = s,
            category = c,
            categories = products.map { it.category }.distinct(),
            groups = filtered.groupBy { it.category }.toList(),
            productCount = products.size,
            piecesOnHand = products.filter { it.unit == StockUnit.PCS }.sumOf { it.quantity },
            attentionCount = products.count { it.status != StockStatus.OK },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryState(status = status.value))

    fun setQuery(q: String) { query.value = q }
    fun setStatus(s: StatusFilter) { status.value = s }
    fun setCategory(c: String) { category.value = if (category.value == c) null else c }
}

@Composable
fun InventoryScreen(onOpenProduct: (Long) -> Unit, onNewProduct: () -> Unit) {
    val vm = appViewModel { c, h -> InventoryViewModel(c.repository, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val pad = Modifier.padding(horizontal = 20.dp)

    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ScreenTitle("Stock", "${state.productCount} products · ${state.piecesOnHand.grouped()} pcs on hand") {
                    PillButton(Icons.Rounded.Add, "New", onNewProduct)
                }
                SearchBox(state.query, vm::setQuery, "Search colour, size, product")
                SegmentTabs(
                    options = listOf(
                        StatusFilter.ALL to "All",
                        StatusFilter.ATTENTION to "Attention · ${state.attentionCount}",
                        StatusFilter.LOW to "Low",
                        StatusFilter.OUT to "Out",
                    ),
                    selected = state.status,
                    onSelect = vm::setStatus,
                )
            }
        }
        item {
            FilterPillRow(state.categories.map { it to it.toTitleCase() }, state.category, vm::setCategory)
        }
        if (state.groups.isEmpty()) {
            item { EmptyNote("Nothing matches. Try another colour or size.", pad) }
        }
        state.groups.forEach { (category, products) ->
            item(key = "g_$category") {
                Column(pad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val bags = products.all { it.unit == StockUnit.BAGS }
                    val total = products.filter { bags || it.unit == StockUnit.PCS }.sumOf { it.quantity }
                    GroupHeader(category.toTitleCase(), "${total.grouped()} ${if (bags) "bags" else "pcs"}")
                    ListPanel(products) { p -> ProductItem(p) { onOpenProduct(p.id) } }
                }
            }
        }
    }
}
