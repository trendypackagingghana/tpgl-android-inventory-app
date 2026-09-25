package com.example.tpglstock.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.tpglstock.data.ChangeMode
import com.example.tpglstock.data.StockChange
import com.example.tpglstock.data.StockRepository
import com.example.tpglstock.data.remote.SupabaseException
import com.example.tpglstock.data.bagsText
import com.example.tpglstock.data.formatDate
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.local.MovementEntity
import com.example.tpglstock.data.local.MovementSource
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.local.StockUnit
import com.example.tpglstock.data.quantityText
import com.example.tpglstock.data.relativeTime
import com.example.tpglstock.data.status
import com.example.tpglstock.data.subtitle
import com.example.tpglstock.data.title
import com.example.tpglstock.data.toTitleCase
import com.example.tpglstock.data.unitLabel
import com.example.tpglstock.ui.addstock.QuantityInput
import com.example.tpglstock.ui.appViewModel
import com.example.tpglstock.ui.components.AppCard
import com.example.tpglstock.ui.components.EmptyState
import com.example.tpglstock.ui.components.MovementRow
import com.example.tpglstock.ui.components.SectionHeader
import com.example.tpglstock.ui.components.SegmentedToggle
import com.example.tpglstock.ui.components.StatusPill
import com.example.tpglstock.ui.components.ThinDivider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductDetailViewModel(private val repo: StockRepository, handle: SavedStateHandle) : ViewModel() {
    val id: Long = handle.get<Long>("id") ?: -1L

    val product: StateFlow<ProductEntity?> =
        repo.product(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val history: StateFlow<List<MovementEntity>> =
        repo.productMovements(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun apply(mode: ChangeMode, amount: Long, note: String): Int =
        repo.applyChanges(listOf(StockChange(id, mode, amount, note)), MovementSource.MANUAL)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val vm = appViewModel { c, h -> ProductDetailViewModel(c.repository, h) }
    val product by vm.product.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var sheetMode by remember { mutableStateOf<ChangeMode?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(product?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { onEdit(vm.id) }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit product") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        val p = product ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.subtitle.ifBlank { p.category.toTitleCase() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            StatusPill(p.status)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(p.quantityText(), style = MaterialTheme.typography.displaySmall)
                        p.bagsText()?.let {
                            Text("≈ $it · ${p.pcsPerBag.grouped()} pcs per bag", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Alert below ${p.reorderLevel.grouped()} ${unitLabel(p.unit, p.reorderLevel)} · updated ${relativeTime(p.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { sheetMode = ChangeMode.ADD }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("In")
                            }
                            FilledTonalButton(onClick = { sheetMode = ChangeMode.REMOVE }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Remove, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Out")
                            }
                            FilledTonalButton(onClick = { sheetMode = ChangeMode.SET }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.FactCheck, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Count")
                            }
                        }
                    }
                }
            }
            item { SectionHeader("History") }
            if (history.isEmpty()) {
                item { AppCard { EmptyState(Icons.Rounded.History, "No history", "Changes to this product will show here.") } }
            } else {
                item {
                    AppCard {
                        Column {
                            history.forEachIndexed { i, m ->
                                Column {
                                    MovementRow(m, showTime = false)
                                    Text(
                                        formatDate(m.timestamp, "EEE d MMM yyyy · HH:mm"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 68.dp, bottom = 10.dp),
                                    )
                                }
                                if (i < history.lastIndex) ThinDivider()
                            }
                        }
                    }
                }
            }
        }

        sheetMode?.let { mode ->
            QuickAdjustDialog(
                product = p,
                mode = mode,
                onDismiss = { sheetMode = null },
                onConfirm = { amount, note ->
                    sheetMode = null
                    scope.launch {
                        val message = try {
                            vm.apply(mode, amount, note)
                            "Stock updated"
                        } catch (e: java.io.IOException) {
                            "Not saved — ${e.message ?: "can't reach the server"}"
                        }
                        snackbar.showSnackbar(message)
                    }
                },
            )
        }
    }
}

@Composable
private fun QuickAdjustDialog(
    product: ProductEntity,
    mode: ChangeMode,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit,
) {
    var amount by remember { mutableStateOf<Long?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (mode) {
                    ChangeMode.ADD -> "Stock in"
                    ChangeMode.REMOVE -> "Stock out"
                    ChangeMode.SET -> "Stock count"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current: ${product.quantityText()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                QuantityInput(product = product, onAmountChange = { amount = it })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { amount?.let { onConfirm(it, note.trim()) } }, enabled = amount != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New product" else "Edit product") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (!vm.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete product")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = category,
                onValueChange = { category = it.uppercase() },
                label = { Text("Category") },
                supportingText = { Text("Heading used on WhatsApp, e.g. SPRAY BOTTLES") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (vm.isNew && categories.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { c ->
                        androidx.compose.material3.SuggestionChip(onClick = { category = c }, label = { Text(c.toTitleCase()) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Colour / variant") },
                    placeholder = { Text("Standard") },
                    modifier = Modifier.weight(1.4f), singleLine = true,
                )
                OutlinedTextField(
                    value = size, onValueChange = { size = it },
                    label = { Text("Size") }, placeholder = { Text("500ml") },
                    modifier = Modifier.weight(1f), singleLine = true,
                )
            }
            Text("Counted in", style = MaterialTheme.typography.labelLarge)
            SegmentedToggle(
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
            NumberField(reorder, { reorder = it }, "Low-stock alert level (${if (unit == StockUnit.BAGS) "bags" else "pcs"})")

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    if (category.isBlank()) { error = "Enter a category."; return@Button }
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (vm.isNew) "Add product" else "Save changes") }
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
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }) },
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}
