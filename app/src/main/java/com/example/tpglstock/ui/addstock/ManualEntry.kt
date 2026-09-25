package com.example.tpglstock.ui.addstock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.tpglstock.data.bagsText
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.label
import com.example.tpglstock.data.local.MovementSource
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.local.StockUnit
import com.example.tpglstock.data.quantityText
import com.example.tpglstock.data.subtitle
import com.example.tpglstock.data.title
import com.example.tpglstock.ui.components.AppCard
import com.example.tpglstock.ui.components.IconBadge
import com.example.tpglstock.ui.components.ProductRow
import com.example.tpglstock.ui.components.SegmentedToggle
import com.example.tpglstock.ui.components.ThinDivider
import com.example.tpglstock.ui.inventory.SearchField
import com.example.tpglstock.ui.theme.StockTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManualEntryViewModel(private val repo: StockRepository, handle: SavedStateHandle) : ViewModel() {
    val initialMode: String = handle.initialMode()
    private val selectedId = MutableStateFlow(handle.get<Long>("productId")?.takeIf { it > 0 })

    val products: StateFlow<List<ProductEntity>> =
        repo.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selected: StateFlow<ProductEntity?> = combine(products, selectedId) { list, id ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(id: Long) { selectedId.value = id }

    suspend fun save(product: ProductEntity, mode: ChangeMode, amount: Long, note: String): Int =
        repo.applyChanges(listOf(StockChange(product.id, mode, amount, note)), MovementSource.MANUAL)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualEntry(vm: ManualEntryViewModel, modifier: Modifier = Modifier) {
    val products by vm.products.collectAsStateWithLifecycle()
    val product by vm.selected.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(ChangeMode.ADD) }
    var amount by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var formKey by remember { mutableIntStateOf(0) }
    var pickerOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Column(modifier) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SegmentedToggle(
                options = listOf(ChangeMode.ADD to "Stock in", ChangeMode.REMOVE to "Stock out", ChangeMode.SET to "Count"),
                selected = mode,
                onSelect = { mode = it },
                icons = mapOf(ChangeMode.ADD to Icons.Rounded.Add, ChangeMode.REMOVE to Icons.Rounded.Remove, ChangeMode.SET to Icons.Rounded.FactCheck),
            )

            AppCard(onClick = { pickerOpen = true }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Inventory2, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val p = product
                        if (p == null) {
                            Text("Choose product", style = MaterialTheme.typography.titleSmall)
                            Text("Tap to search ${products.size} products", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(p.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOf(p.subtitle, "On hand: ${p.quantityText()}").filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            product?.let { p ->
                key(p.id, formKey) {
                    QuantityInput(product = p, onAmountChange = { amount = it })
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. Supplied to Kumasi customer") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                amount?.let { a -> ChangePreview(p, mode, a) }
            }
            Spacer(Modifier.height(8.dp))
        }

        SnackbarHost(snackbar)
        Button(
            onClick = {
                val p = product ?: return@Button
                val a = amount ?: return@Button
                scope.launch {
                    val applied = try {
                        vm.save(p, mode, a, note.trim())
                    } catch (e: java.io.IOException) {
                        snackbar.showSnackbar("Not saved — ${e.message ?: "can't reach the server"}")
                        return@launch
                    }
                    amount = null
                    note = ""
                    formKey++
                    snackbar.showSnackbar(if (applied > 0) "Saved · ${p.label}" else "No change — quantity is the same")
                }
            },
            enabled = product != null && amount != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
                .height(54.dp),
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save")
        }
    }

    if (pickerOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { pickerOpen = false }, sheetState = sheetState) {
            ProductPicker(products) {
                vm.select(it.id)
                amount = null
                formKey++
                pickerOpen = false
            }
        }
    }
}

@Composable
private fun ProductPicker(products: List<ProductEntity>, onPick: (ProductEntity) -> Unit) {
    var query by remember { mutableStateOf("") }
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    val filtered = products.filter { p -> terms.all { it in p.label.lowercase() } }
    Column(Modifier.fillMaxHeight(0.9f).navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Choose product", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            SearchField(query, { query = it })
        }
        LazyColumn(Modifier.fillMaxWidth().imePadding()) {
            items(filtered, key = { it.id }) { p ->
                ProductRow(p, onClick = { onPick(p) })
                ThinDivider()
            }
        }
    }
}

/**
 * Quantity entry that understands bags. For bagged piece products the user can enter
 * bags and loose pieces; the total in pieces is reported via [onAmountChange].
 */
@Composable
fun QuantityInput(product: ProductEntity, onAmountChange: (Long?) -> Unit) {
    val bagged = product.unit == StockUnit.PCS && product.pcsPerBag > 0
    var bags by remember { mutableStateOf("") }
    var pcs by remember { mutableStateOf("") }

    LaunchedEffect(bags, pcs) {
        val b = bags.toLongOrNull()
        val p = pcs.toLongOrNull()
        onAmountChange(
            when {
                b == null && p == null -> null
                bagged -> (b ?: 0) * product.pcsPerBag + (p ?: 0)
                else -> p
            },
        )
    }

    if (bagged) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DigitField(bags, { bags = it }, "Bags", Modifier.weight(1f))
                DigitField(pcs, { pcs = it }, "Loose pcs", Modifier.weight(1f))
            }
            val total = (bags.toLongOrNull() ?: 0) * product.pcsPerBag + (pcs.toLongOrNull() ?: 0)
            Text(
                "1 bag = ${product.pcsPerBag.grouped()} pcs · total ${total.grouped()} pcs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        DigitField(pcs, { pcs = it }, if (product.unit == StockUnit.BAGS) "Bags" else "Pieces", Modifier.fillMaxWidth())
    }
}

@Composable
private fun DigitField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(9)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun ChangePreview(product: ProductEntity, mode: ChangeMode, amount: Long) {
    val newQty = when (mode) {
        ChangeMode.ADD -> product.quantity + amount
        ChangeMode.REMOVE -> (product.quantity - amount).coerceAtLeast(0)
        ChangeMode.SET -> amount
    }
    val delta = newQty - product.quantity
    AppCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(product.quantityText(), style = MaterialTheme.typography.titleMedium)
                product.bagsText()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("After", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(product.quantityText(newQty), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        delta > 0 -> "+${delta.grouped()}"
                        delta < 0 -> "−${(-delta).grouped()}"
                        else -> "No change"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        delta > 0 -> StockTheme.colors.seriesIn
                        delta < 0 -> StockTheme.colors.seriesOut
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
    if (mode == ChangeMode.REMOVE && amount > product.quantity) {
        Text(
            "Only ${product.quantityText()} on hand — stock will be set to 0.",
            style = MaterialTheme.typography.bodySmall,
            color = StockTheme.colors.warn,
        )
    }
}
