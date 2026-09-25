package com.example.tpglstock.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.tpglstock.data.ChangeMode
import com.example.tpglstock.data.StockRepository
import com.example.tpglstock.data.dayLabel
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.kind
import com.example.tpglstock.data.levelFraction
import com.example.tpglstock.data.local.MovementEntity
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.local.StockUnit
import com.example.tpglstock.data.remote.SupabaseException
import com.example.tpglstock.data.shortTitle
import com.example.tpglstock.data.startOfDay
import com.example.tpglstock.data.status
import com.example.tpglstock.data.toTitleCase
import com.example.tpglstock.data.unitLabel
import com.example.tpglstock.ui.appViewModel
import com.example.tpglstock.ui.components.BackBar
import com.example.tpglstock.ui.components.FilterPill
import com.example.tpglstock.ui.components.IconTile
import com.example.tpglstock.ui.components.InkButton
import com.example.tpglstock.ui.components.LabeledField
import com.example.tpglstock.ui.components.ListPanel
import com.example.tpglstock.ui.components.Panel
import com.example.tpglstock.ui.components.SectionTitle
import com.example.tpglstock.ui.components.SegmentTabs
import com.example.tpglstock.ui.components.StatusChip
import com.example.tpglstock.ui.components.Swatch
import com.example.tpglstock.ui.components.byline
import com.example.tpglstock.ui.components.deltaColor
import com.example.tpglstock.ui.components.deltaText
import com.example.tpglstock.ui.components.fromTo
import com.example.tpglstock.ui.components.movementLook
import com.example.tpglstock.ui.components.statusTone
import com.example.tpglstock.ui.theme.StockTheme
import com.example.tpglstock.ui.theme.mono
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductDetailViewModel(repo: StockRepository, handle: SavedStateHandle) : ViewModel() {
    val id: Long = handle.get<Long>("id") ?: -1L

    val product: StateFlow<ProductEntity?> =
        repo.product(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val history: StateFlow<List<MovementEntity>> =
        repo.productMovements(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun ProductDetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, onLog: (Long, ChangeMode) -> Unit) {
    val vm = appViewModel { c, h -> ProductDetailViewModel(c.repository, h) }
    val product by vm.product.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val c = StockTheme.colors

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).navigationBarsPadding()) {
        val p = product
        BackBar(
            onBack,
            Modifier.padding(horizontal = 10.dp).padding(top = 4.dp),
            center = p?.kind?.ifBlank { p.category.toTitleCase() },
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = "Edit product") { onEdit(vm.id) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Edit, contentDescription = "Edit product", tint = c.muted, modifier = Modifier.size(22.dp)) }
        }
        if (p == null) return@Column

        val weekStart = startOfDay(System.currentTimeMillis()) - 6 * 24 * 60 * 60 * 1000L
        val week = history.filter { it.timestamp >= weekStart }
        val weekIn = week.filter { it.delta > 0 }.sumOf { it.delta }
        val weekOut = week.filter { it.delta < 0 }.sumOf { -it.delta }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Swatch(p, 64.dp)
                    Text(p.shortTitle, style = MaterialTheme.typography.headlineMedium)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(p.quantity.grouped(), style = mono(60.sp, letterSpacing = (-1.8).sp).copy(lineHeight = 60.sp))
                        Spacer(Modifier.width(10.dp))
                        Text(unitLabel(p.unit, p.quantity), style = MaterialTheme.typography.bodyLarge, color = c.muted, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(p.status, large = true)
                        if (p.unit == StockUnit.PCS && p.pcsPerBag > 0) {
                            Spacer(Modifier.width(8.dp))
                            val bags = p.quantity.toDouble() / p.pcsPerBag
                            val text = if (bags % 1.0 == 0.0) bags.toLong().grouped() else String.format(java.util.Locale.US, "%.1f", bags)
                            Text("≈ $text bags of ${p.pcsPerBag.grouped()}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = c.muted)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReorderBar(p)
                    Row {
                        Text("Reorder at ${p.reorderLevel.grouped()}", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.weight(1f))
                        Text("This week +${weekIn.grouped()} / −${weekOut.grouped()}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionTile(Icons.Rounded.SouthWest, "Stock in", dark = true, Modifier.weight(1f)) { onLog(p.id, ChangeMode.ADD) }
                    ActionTile(Icons.Rounded.NorthEast, "Stock out", dark = false, Modifier.weight(1f)) { onLog(p.id, ChangeMode.REMOVE) }
                    ActionTile(Icons.Rounded.Tag, "Count", dark = false, Modifier.weight(1f)) { onLog(p.id, ChangeMode.SET) }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("History")
                    if (history.isEmpty()) {
                        Panel { Text("No changes recorded yet.", style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(16.dp)) }
                    } else {
                        ListPanel(history) { m -> HistoryRow(m) }
                    }
                }
            }
        }
    }
}

/** Level bar with a tick at the reorder level, which sits at a third of the bar. */
@Composable
private fun ReorderBar(p: ProductEntity) {
    val c = StockTheme.colors
    Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.track)) {
            if (p.levelFraction > 0f) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(p.levelFraction).clip(RoundedCornerShape(5.dp)).background(statusTone(p.status).bar))
            }
        }
        if (p.reorderLevel > 0) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
                Box(Modifier.offset(x = maxWidth / 3 - 1.dp).width(2.dp).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(c.ink))
            }
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, dark: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = StockTheme.colors
    val fg = if (dark) c.onInk else c.ink
    Column(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) c.ink else c.surface)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = fg)
    }
}

@Composable
private fun HistoryRow(m: MovementEntity) {
    val look = movementLook(m.type)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(look.icon, look.bg, look.fg)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.fromTo(), style = MaterialTheme.typography.labelLarge)
            Text("${m.byline()} · ${dayLabel(m.timestamp)}", style = MaterialTheme.typography.bodySmall, color = StockTheme.colors.muted)
        }
        Spacer(Modifier.width(8.dp))
        Text(deltaText(m.delta), style = mono(14.sp), color = deltaColor(m.delta))
    }
}

// ---------------------------------------------------------------------------
// Add / edit product
// ---------------------------------------------------------------------------

class ProductEditViewModel(private val repo: StockRepository, handle: SavedStateHandle) : ViewModel() {
    val id: Long = handle.get<Long>("id") ?: -1L
    val isNew get() = id <= 0

    suspend fun load(): ProductEntity? = if (isNew) null else repo.product(id).first()

    suspend fun categories(): List<String> = repo.allProducts().map { it.category }.distinct()

    suspend fun save(p: ProductEntity): String? = try {
        if (isNew) repo.addProduct(p) else repo.updateDetails(p.copy(id = id))
        null
    } catch (e: SupabaseException) {
        if (e.code == "23505") "A product with this category, size and variant already exists."
        else "Not saved — ${e.message}"
    } catch (e: java.io.IOException) {
        "Not saved — ${e.message ?: "can't reach the server"}"
    }

    suspend fun delete(): String? = try {
        repo.product(id).first()?.let { repo.deleteProduct(it) }
        null
    } catch (e: java.io.IOException) {
        "Not deleted — ${e.message ?: "can't reach the server"}"
    }
}

@Composable
fun ProductEditScreen(onDone: () -> Unit, onDeleted: () -> Unit) {
    val vm = appViewModel { c, h -> ProductEditViewModel(c.repository, h) }
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(StockUnit.PCS) }
    var pcsPerBag by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var reorder by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf(emptyList<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        categories = vm.categories()
        if (!loaded) {
            vm.load()?.let { p ->
                category = p.category; name = p.name; size = p.size; unit = p.unit
                pcsPerBag = if (p.pcsPerBag > 0) p.pcsPerBag.toString() else ""
                quantity = p.quantity.toString(); reorder = p.reorderLevel.toString()
            }
            loaded = true
        }
    }

    val c = StockTheme.colors
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).navigationBarsPadding().imePadding()) {
        BackBar(onDone, Modifier.padding(horizontal = 10.dp).padding(top = 4.dp), title = if (vm.isNew) "New product" else "Edit product") {
            if (!vm.isNew) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = "Delete product") { confirmDelete = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete product", tint = c.out.fg) }
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Panel(shape = RoundedCornerShape(24.dp), padding = PaddingValues(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LabeledField(
                        "Category",
                        category,
                        { category = it.uppercase() },
                        placeholder = "SPRAY BOTTLES",
                        supporting = "Heading used on WhatsApp",
                    )
                    if (vm.isNew && categories.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories) { cat -> FilterPill(cat.toTitleCase(), cat == category) { category = cat } }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabeledField("Colour / variant", name, { name = it }, Modifier.weight(1.4f), placeholder = "Standard")
                        LabeledField("Size", size, { size = it }, Modifier.weight(1f), placeholder = "500ml")
                    }
                }
            }
            Panel(shape = RoundedCornerShape(24.dp), padding = PaddingValues(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Counted in", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    SegmentTabs(
                        options = listOf(StockUnit.PCS to "Pieces", StockUnit.BAGS to "Bags"),
                        selected = unit,
                        onSelect = { unit = it },
                    )
                    if (unit == StockUnit.PCS) {
                        NumberField(pcsPerBag, { pcsPerBag = it }, "Pieces per bag", "Leave empty if not sold in bags")
                    }
                    if (vm.isNew) {
                        NumberField(quantity, { quantity = it }, "Opening quantity (${if (unit == StockUnit.BAGS) "bags" else "pcs"})")
                    }
                    NumberField(reorder, { reorder = it }, "Reorder at (${if (unit == StockUnit.BAGS) "bags" else "pcs"})")
                }
            }

            error?.let { Text(it, color = c.out.fg, style = MaterialTheme.typography.bodyMedium) }

            InkButton(
                if (vm.isNew) "Add product" else "Save changes",
                onClick = {
                    if (category.isBlank()) { error = "Enter a category."; return@InkButton }
                    val product = ProductEntity(
                        category = category.trim().uppercase(),
                        name = name.trim().ifBlank { "Standard" },
                        size = size.trim(),
                        unit = unit,
                        pcsPerBag = if (unit == StockUnit.PCS) pcsPerBag.toIntOrNull() ?: 0 else 0,
                        quantity = quantity.toLongOrNull() ?: 0,
                        reorderLevel = reorder.toLongOrNull() ?: 0,
                    )
                    scope.launch {
                        error = vm.save(product)
                        if (error == null) onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp,
                shape = RoundedCornerShape(18.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete product?") },
            text = { Text("This removes the product and its entire history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        error = vm.delete()
                        if (error == null) onDeleted()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, supporting: String? = null) {
    LabeledField(
        label,
        value,
        { v -> onChange(v.filter { it.isDigit() }) },
        supporting = supporting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = mono(15.sp),
    )
}
