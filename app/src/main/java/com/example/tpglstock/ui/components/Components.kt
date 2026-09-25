package com.example.tpglstock.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tpglstock.data.StockStatus
import com.example.tpglstock.data.SyncStatus
import com.example.tpglstock.data.formatTime
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.kind
import com.example.tpglstock.data.levelFraction
import com.example.tpglstock.data.local.MovementEntity
import com.example.tpglstock.data.local.MovementSource
import com.example.tpglstock.data.local.MovementType
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.quantityText
import com.example.tpglstock.data.shortTitle
import com.example.tpglstock.data.status
import com.example.tpglstock.data.unitLabel
import com.example.tpglstock.ui.theme.StatusTone
import com.example.tpglstock.ui.theme.StockTheme
import com.example.tpglstock.ui.theme.mono

// ---------------------------------------------------------------------------
// Toast
// ---------------------------------------------------------------------------

data class ToastMessage(val text: String, val error: Boolean, val id: Long = System.nanoTime())

/** Short confirmation shown above the bottom bar by the app shell. */
class ToastState {
    var current by mutableStateOf<ToastMessage?>(null)
        private set

    fun show(text: String, error: Boolean = false) { current = ToastMessage(text, error) }
    fun dismiss(message: ToastMessage) { if (current == message) current = null }
}

val LocalToast = staticCompositionLocalOf { ToastState() }

@Composable
fun Toast(message: ToastMessage, modifier: Modifier = Modifier) {
    val c = StockTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(c.hero)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (message.error) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (message.error) c.out.onHero else c.ok.onHero,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(message.text, color = c.onHero, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------
// Surfaces and headings
// ---------------------------------------------------------------------------

/** White rounded card. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    color: Color = StockTheme.colors.surface,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().clip(shape).background(color).padding(padding), content = content)
}

/** Divider between rows inside a [Panel]. */
@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(StockTheme.colors.line))
}

/** Rows in a [Panel] separated by [RowDivider]s. */
@Composable
fun <T> ListPanel(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    Panel(modifier) {
        items.forEachIndexed { i, item ->
            if (i > 0) RowDivider()
            row(item)
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = StockTheme.colors.muted)
        }
        trailing()
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = StockTheme.colors.muted,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/** Group heading above a [ListPanel], with a mono total on the right. */
@Composable
fun GroupHeader(title: String, trailing: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
        Text(trailing, style = mono(12.sp, androidx.compose.ui.text.font.FontWeight.Normal), color = StockTheme.colors.muted)
    }
}

/** Top bar for pushed screens: round back button, optional title and actions. */
@Composable
fun BackBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
    iconDescription: String = "Back",
    center: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.padding(end = 2.dp).size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = iconDescription, modifier = Modifier.size(24.dp)) }
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        else if (center != null) Text(center, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = StockTheme.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        else Spacer(Modifier.weight(1f))
        Row(Modifier.defaultMinSize(minWidth = 44.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.padding(top = 10.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(StockTheme.colors.handle))
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
        color = StockTheme.colors.muted,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 40.dp),
    )
}

// ---------------------------------------------------------------------------
// Buttons, chips and inputs
// ---------------------------------------------------------------------------

/** Dark filled button. */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    shape: Shape = RoundedCornerShape(14.dp),
    icon: ImageVector? = null,
) {
    val c = StockTheme.colors
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(if (enabled) c.ink else c.disabledBg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) c.onInk else c.disabledFg
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
    }
}

/** White rounded pill with an icon, e.g. Export. */
@Composable
fun PillButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = StockTheme.colors
    Row(
        modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(c.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) c.ink else c.faint
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp))
    }
}

/** Outlined chip; filled ink when selected. */
@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = StockTheme.colors
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        color = if (selected) c.onInk else c.ink,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) c.ink else Color.Transparent)
            .border(1.dp, if (selected) c.ink else c.chipBorder, CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
fun <T> FilterPillRow(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it.first.toString() }) { (value, label) -> FilterPill(label, value == selected) { onSelect(value) } }
    }
}

/** Segmented control on a beige track; the selected segment is white. */
@Composable
fun <T> SegmentTabs(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
    verticalPadding: Dp = 8.dp,
) {
    val c = StockTheme.colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.track).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                label,
                style = textStyle,
                color = if (on) c.ink else c.muted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) c.surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .padding(vertical = verticalPadding),
            )
        }
    }
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = StockTheme.colors
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = c.faint, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = c.ink),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Clear search",
                tint = c.muted,
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onChange("") }.padding(6.dp),
            )
        }
    }
}

/** Labelled input on a soft background, as on the You screen. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    supporting: String? = null,
) {
    val c = StockTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surfaceSoft)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = textStyle, color = c.faint)
                    inner()
                }
            },
        )
        if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

// ---------------------------------------------------------------------------
// Product visuals
// ---------------------------------------------------------------------------

private val swatchColors = mapOf(
    "white" to Color(0xFFFFFFFF),
    "black" to Color(0xFF1B1712),
    "amber" to Color(0xFFB8651B),
    "yellow" to Color(0xFFF2C230),
    "blue" to Color(0xFF2E5BD8),
    "red" to Color(0xFFC8322B),
    "green" to Color(0xFF2F8F5A),
    "lemon green" to Color(0xFFB5D334),
    "brown" to Color(0xFF7A4A2A),
    "beach" to Color(0xFFE6D2A8),
    "pearl" to Color(0xFFF1ECE2),
    "pink" to Color(0xFFE88BB0),
    "orange" to Color(0xFFE2772B),
    "purple" to Color(0xFF7A4DB8),
    "grey" to Color(0xFF9A9A9A),
    "gray" to Color(0xFF9A9A9A),
    "gold" to Color(0xFFC9A23A),
    "silver" to Color(0xFFC4C4C4),
)

/** Round colour dot for a product's colour name; striped for clear, neutral when unknown. */
@Composable
fun Swatch(product: ProductEntity, size: Dp) = Swatch(product.name, size)

@Composable
fun Swatch(colourName: String, size: Dp) {
    val c = StockTheme.colors
    val key = colourName.trim().lowercase()
    val shape = CircleShape
    val base = Modifier.size(size).clip(shape).border(1.dp, c.border, shape)
    if (key == "clear") {
        Canvas(base.background(Color.White)) {
            val step = 8.dp.toPx()
            val stripe = Color(0xFFE3ECEF)
            var x = -this.size.height
            while (x < this.size.width) {
                drawLine(stripe, Offset(x, this.size.height), Offset(x + this.size.height, 0f), strokeWidth = step / 2)
                x += step
            }
        }
    } else {
        Box(base.background(swatchColors[key] ?: c.track))
    }
}

@Composable
fun statusTone(status: StockStatus): StatusTone = when (status) {
    StockStatus.OK -> StockTheme.colors.ok
    StockStatus.LOW -> StockTheme.colors.low
    StockStatus.OUT -> StockTheme.colors.out
}

fun statusLabel(status: StockStatus): String = when (status) {
    StockStatus.OK -> "In stock"
    StockStatus.LOW -> "Low"
    StockStatus.OUT -> "Out"
}

@Composable
fun StatusChip(status: StockStatus, modifier: Modifier = Modifier, large: Boolean = false) {
    val tone = statusTone(status)
    Text(
        statusLabel(status),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = if (large) 12.sp else 11.sp),
        color = tone.fg,
        modifier = modifier
            .clip(CircleShape)
            .background(tone.bg)
            .padding(horizontal = if (large) 10.dp else 8.dp, vertical = if (large) 5.dp else 3.dp),
    )
}

@Composable
fun LevelBar(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = StockTheme.colors.track, height: Dp = 4.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(track)) {
        if (fraction > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(height / 2)).background(color))
    }
}

/** Stock list row: swatch, name, quantity, level bar and status. */
@Composable
fun ProductItem(product: ProductEntity, onClick: () -> Unit) {
    val c = StockTheme.colors
    val tone = statusTone(product.status)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatch(product, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(product.shortTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(product.quantityText(), style = mono(14.sp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBar(product.levelFraction, tone.bar, Modifier.weight(1f))
                Text(
                    statusLabel(product.status),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = tone.fg,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(52.dp),
                )
            }
        }
    }
}

/** Compact row: swatch, name and kind, quantity. Used for pickers and short lists. */
@Composable
fun ProductLine(product: ProductEntity, onClick: () -> Unit, detail: String? = null, swatchSize: Dp = 32.dp) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatch(product, swatchSize)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(product.shortTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(product.kind.ifBlank { null }, detail).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = StockTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(product.quantityText(), style = mono(14.sp))
    }
}

// ---------------------------------------------------------------------------
// Movements
// ---------------------------------------------------------------------------

data class MovementLook(val icon: ImageVector, val bg: Color, val fg: Color)

@Composable
fun movementLook(type: String): MovementLook {
    val c = StockTheme.colors
    return when (type) {
        MovementType.IN -> MovementLook(Icons.Rounded.SouthWest, c.inBg, c.inFg)
        MovementType.OUT -> MovementLook(Icons.Rounded.NorthEast, c.outBg, c.outFg)
        MovementType.COUNT -> MovementLook(Icons.Rounded.Tag, c.track, c.ink)
        else -> MovementLook(Icons.Rounded.Add, c.track, c.ink)
    }
}

@Composable
fun IconTile(icon: ImageVector, bg: Color, fg: Color, size: Dp = 32.dp, iconSize: Dp = 18.dp, radius: Dp = 10.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(iconSize))
    }
}

fun deltaText(delta: Long): String = (if (delta > 0) "+" else if (delta < 0) "−" else "±") + kotlin.math.abs(delta).grouped()

@Composable
fun deltaColor(delta: Long): Color = when {
    delta > 0 -> StockTheme.colors.inFg
    delta < 0 -> StockTheme.colors.outFg
    else -> StockTheme.colors.muted
}

/** "1,200 → 800 pcs · Kumasi order". */
fun MovementEntity.fromTo(): String =
    "${previousQty.grouped()} → ${newQty.grouped()} ${unitLabel(unit, newQty)}" + if (note.isNotBlank()) " · $note" else ""

/** "Via assistant · 14:20". */
fun MovementEntity.byline(): String {
    val how = when {
        type == MovementType.OPENING -> "Opening stock"
        source == MovementSource.AI -> "Via assistant"
        type == MovementType.COUNT -> "Counted"
        else -> "Logged"
    }
    return "$how · ${formatTime(timestamp)}"
}

/** Activity row: type tile, product, delta, before → after and how it was recorded. */
@Composable
fun MovementItem(movement: MovementEntity, title: String, onClick: (() -> Unit)?, extra: String? = null) {
    val c = StockTheme.colors
    val look = movementLook(movement.type)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        IconTile(look.icon, look.bg, look.fg)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(deltaText(movement.delta), style = mono(14.sp), color = deltaColor(movement.delta))
            }
            Text(movement.fromTo(), style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                movement.byline() + (extra?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Sync
// ---------------------------------------------------------------------------

/** Shows whether every write reached Supabase and the local copy matches it. */
@Composable
fun SyncPill(sync: SyncStatus, modifier: Modifier = Modifier) {
    val c = StockTheme.colors
    val tone = when {
        sync.error != null -> c.out
        sync.saving > 0 || !sync.loaded -> c.low
        else -> c.ok
    }
    val label = when {
        sync.error != null -> "Not synced"
        sync.saving > 0 -> "Saving…"
        !sync.loaded -> "Syncing…"
        else -> "Synced"
    }
    val description = when {
        sync.error != null -> "Not synced: ${sync.error}. Pull down to retry."
        sync.inSync -> "All changes saved to the database and up to date."
        else -> label
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(tone.bg)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tone.fg))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tone.fg, maxLines = 1)
    }
}
