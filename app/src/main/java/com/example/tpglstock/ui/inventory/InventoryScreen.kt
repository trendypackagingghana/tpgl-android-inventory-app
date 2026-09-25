package com.example.tpglstock.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
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
import com.example.tpglstock.ui.components.AppCard
import com.example.tpglstock.ui.components.EmptyState
import com.example.tpglstock.ui.components.ProductRow
import com.example.tpglstock.ui.components.ThinDivider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class StatusFilter(val label: String) { ALL("All"), ATTENTION("Needs attention"), OK("In stock"), LOW("Low"), OUT("Out") }

data class InventoryState(
    val query: String = "",
    val status: StatusFilter = StatusFilter.ALL,
    val category: String? = null,
    val categories: List<String> = emptyList(),
    val groups: List<Pair<String, List<ProductEntity>>> = emptyList(),
    val total: Int = 0,
)

class InventoryViewModel(repo: StockRepository, handle: SavedStateHandle) : ViewModel() {
    private val query = MutableStateFlow("")
    private val status = MutableStateFlow(
        when (handle.get<String>("filter")) {
            "low" -> StatusFilter.LOW
            "out" -> StatusFilter.OUT
            "ok" -> StatusFilter.OK
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
                StatusFilter.OK -> p.status == StockStatus.OK
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
            total = filtered.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryState(status = status.value))

    fun setQuery(q: String) { query.value = q }
    fun setStatus(s: StatusFilter) { status.value = s }
    fun setCategory(c: String?) { category.value = if (category.value == c) null else c }
}

@Composable
fun InventoryScreen(onOpenProduct: (Long) -> Unit, onNewProduct: () -> Unit) {
    val vm = appViewModel { c, h -> InventoryViewModel(c.repository, h) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewProduct,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New product") },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Inventory", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        "${state.total} products shown",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.padding(6.dp))
                    SearchField(state.query, vm::setQuery)
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(StatusFilter.entries) { f ->
                        FilterChip(selected = state.status == f, onClick = { vm.setStatus(f) }, label = { Text(f.label) })
                    }
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.categories) { c ->
                        FilterChip(
                            selected = state.category == c,
                            onClick = { vm.setCategory(c) },
                            label = { Text(c.toTitleCase()) },
                        )
                    }
                }
            }

            if (state.groups.isEmpty()) {
                item {
                    EmptyState(Icons.Rounded.SearchOff, "Nothing matches", "Try a different search or filter.")
                }
            }

            state.groups.forEach { (category, products) ->
                item(key = "h_$category") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            category.toTitleCase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        val pcs = products.filter { it.unit == StockUnit.PCS }.sumOf { it.quantity }
                        val bags = products.filter { it.unit == StockUnit.BAGS }.sumOf { it.quantity }
                        Text(
                            if (bags > 0 && pcs == 0L) "${bags.grouped()} bags" else "${pcs.grouped()} pcs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "g_$category") {
                    AppCard(Modifier.padding(horizontal = 16.dp)) {
                        Column {
                            products.forEachIndexed { i, p ->
                                ProductRow(p, onClick = { onOpenProduct(p.id) })
                                if (i < products.lastIndex) ThinDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String = "Search products, colours, sizes") {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}
