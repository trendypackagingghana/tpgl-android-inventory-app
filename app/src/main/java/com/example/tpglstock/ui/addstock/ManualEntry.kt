package com.example.tpglstock.ui.addstock

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.tpglstock.data.ChangeMode
import com.example.tpglstock.data.StockChange
import com.example.tpglstock.data.StockRepository
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.kind
import com.example.tpglstock.data.label
import com.example.tpglstock.data.local.MovementSource
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.local.StockUnit
import com.example.tpglstock.data.quantityText
import com.example.tpglstock.data.shortTitle
import com.example.tpglstock.data.unitLabel
import com.example.tpglstock.ui.appViewModel
import com.example.tpglstock.ui.components.BackBar
import com.example.tpglstock.ui.components.FilterPill
import com.example.tpglstock.ui.components.LocalToast
import com.example.tpglstock.ui.components.ProductLine
import com.example.tpglstock.ui.components.RowDivider
import com.example.tpglstock.ui.components.SearchBox
import com.example.tpglstock.ui.components.SegmentTabs
import com.example.tpglstock.ui.components.SheetHandle
import com.example.tpglstock.ui.components.Swatch
import com.example.tpglstock.ui.theme.StockTheme
import com.example.tpglstock.ui.theme.mono
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManualEntryViewModel(private val repo: StockRepository, handle: SavedStateHandle) : ViewModel() {
    val initialMode: ChangeMode = handle.get<String>("mode")?.let { m -> ChangeMode.entries.find { it.name == m } } ?: ChangeMode.ADD
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

private fun newQuantity(p: ProductEntity, mode: ChangeMode, amount: Long) = when (mode) {
    ChangeMode.ADD -> p.quantity + amount
    ChangeMode.REMOVE -> (p.quantity - amount).coerceAtLeast(0)
    ChangeMode.SET -> amount
}

/** Log stock in, out or a count for one product with a large keypad. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogStockScreen(onClose: () -> Unit, onSaved: (Long) -> Unit) {
    val vm = appViewModel { c, h -> ManualEntryViewModel(c.repository, h) }
    val products by vm.products.collectAsStateWithLifecycle()
    val product by vm.selected.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(vm.initialMode) }
    var digits by rememberSaveable { mutableStateOf("") }
    var inBags by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val c = StockTheme.colors

    val p = product
    val bagged = p != null && p.unit == StockUnit.PCS && p.pcsPerBag > 0
    val typed = digits.toLongOrNull() ?: 0L
    val amount = if (bagged && inBags) typed * p!!.pcsPerBag else typed
    val next = p?.let { newQuantity(it, mode, amount) }
    val canSave = p != null && digits.isNotEmpty() && next != p.quantity && !saving
    val unitText = when {
        p == null -> "pcs"
        bagged && inBags -> if (typed == 1L) "bag" else "bags"
        else -> unitLabel(p.unit, amount)
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BackBar(onClose, Modifier.padding(start = 0.dp), title = "Log stock", icon = Icons.Rounded.Close, iconDescription = "Close")

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.surface)
                .clickable(onClickLabel = "Choose product") { pickerOpen = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (p != null) Swatch(p, 36.dp) else Swatch("", 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p?.shortTitle ?: "Choose a product", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (p == null) "${products.size} products" else listOf(p.kind, "${p.quantityText()} now").filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = c.muted, modifier = Modifier.size(22.dp))
        }

        SegmentTabs(
            options = listOf(ChangeMode.ADD to "Stock in", ChangeMode.REMOVE to "Stock out", ChangeMode.SET to "Count"),
            selected = mode,
            onSelect = { mode = it },
            textStyle = MaterialTheme.typography.labelLarge,
            verticalPadding = 9.dp,
        )

        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when (mode) {
                    ChangeMode.ADD -> "How many came in?"
                    ChangeMode.REMOVE -> "How many went out?"
                    ChangeMode.SET -> "How many are on the shelf?"
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = c.muted,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (digits.isEmpty()) "0" else typed.grouped(),
                    style = mono(56.sp, letterSpacing = (-1.7).sp).copy(lineHeight = 62.sp),
                    color = if (digits.isEmpty()) c.placeholder else c.ink,
                )
                Spacer(Modifier.width(8.dp))
                Text(unitText, style = MaterialTheme.typography.bodyLarge, color = c.muted, modifier = Modifier.padding(bottom = 10.dp))
            }
            if (p != null && next != null) {
                Text(
                    buildString {
                        append("${p.quantity.grouped()} → ${next.grouped()} ${unitLabel(p.unit, next)}")
                        if (bagged && inBags && typed > 0) append(" · ${amount.grouped()} pcs")
                    },
                    style = mono(13.sp, FontWeight.Normal),
                    color = c.muted,
                )
                if (mode == ChangeMode.REMOVE && amount > p.quantity) {
                    Text("Only ${p.quantityText()} on hand. Stock will be set to 0.", style = MaterialTheme.typography.bodySmall, color = c.low.fg)
                }
            }
            if (bagged) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill("Pieces", !inBags) { inBags = false }
                    FilterPill("Bags of ${p!!.pcsPerBag.grouped()}", inBags) { inBags = true }
                }
            }
        }

        Keypad(onKey = { k ->
            digits = when (k) {
                "del" -> digits.dropLast(1)
                // Keep a single "0" so a count of zero can be saved.
                else -> (digits + k).trimStart('0').ifEmpty { "0" }.take(7)
            }
        })

        NoteField(note, { note = it })

        val label = when {
            p == null -> "Choose a product"
            digits.isEmpty() -> "Enter an amount"
            next == p.quantity -> "No change"
            saving -> "Saving…"
            mode == ChangeMode.SET -> "Save count · ${next!!.grouped()} ${unitLabel(p.unit, next)}"
            else -> "Save · ${if (mode == ChangeMode.ADD) "+" else "−"}${amount.grouped()} ${unitLabel(p.unit, amount)}"
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (canSave) c.ink else c.disabledBg)
                .clickable(enabled = canSave, role = Role.Button) {
                    val prod = p ?: return@clickable
                    saving = true
                    scope.launch {
                        try {
                            vm.save(prod, mode, amount, note.trim())
                            toast.show("Saved. ${prod.shortTitle} is now ${prod.quantityText(next!!)}")
                            onSaved(prod.id)
                        } catch (e: java.io.IOException) {
                            toast.show("Not saved: ${e.message ?: "can't reach the server"}", error = true)
                        } finally {
                            saving = false
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = if (canSave) c.onInk else c.disabledFg)
        }
    }

    if (pickerOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { pickerOpen = false },
            sheetState = sheetState,
            containerColor = c.paper,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { SheetHandle() },
        ) {
            ProductPicker(products) {
                vm.select(it.id)
                digits = ""
                inBags = false
                pickerOpen = false
            }
        }
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val c = StockTheme.colors
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "00", "0", "del")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.surface)
                            .clickable(role = Role.Button, onClickLabel = if (k == "del") "Delete digit" else k) { onKey(k) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "del") Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Delete digit", modifier = Modifier.size(24.dp))
                        else Text(k, style = mono(22.sp, FontWeight.Normal))
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteField(value: String, onChange: (String) -> Unit) {
    val c = StockTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(horizontal = 14.dp, vertical = 13.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text("Add a note (optional)", style = MaterialTheme.typography.bodyMedium, color = c.faint)
                inner()
            }
        },
    )
}

@Composable
private fun ProductPicker(products: List<ProductEntity>, onPick: (ProductEntity) -> Unit) {
    var query by remember { mutableStateOf("") }
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    val filtered = products.filter { p -> terms.all { it in p.label.lowercase() } }
    val c = StockTheme.colors
    Column(
        Modifier.fillMaxHeight(0.85f).navigationBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Choose a product", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        SearchBox(query, { query = it }, "Search colour, size, product")
        LazyColumn(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface),
            contentPadding = PaddingValues(0.dp),
        ) {
            itemsIndexed(filtered, key = { _, p -> p.id }) { i, p ->
                if (i > 0) RowDivider()
                ProductLine(p, onClick = { onPick(p) }, swatchSize = 28.dp)
            }
        }
    }
}
