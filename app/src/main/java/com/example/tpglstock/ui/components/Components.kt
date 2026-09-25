package com.example.tpglstock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tpglstock.data.StockStatus
import com.example.tpglstock.data.bagsText
import com.example.tpglstock.data.formatTime
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.local.MovementEntity
import com.example.tpglstock.data.local.MovementSource
import com.example.tpglstock.data.local.MovementType
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.quantityText
import com.example.tpglstock.data.status
import com.example.tpglstock.data.subtitle
import com.example.tpglstock.data.title
import com.example.tpglstock.data.unitLabel
import com.example.tpglstock.ui.theme.StockTheme

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors) { content() }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors) { content() }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Int = 40) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.32f).dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun statusColor(status: StockStatus): Color = when (status) {
    StockStatus.OK -> StockTheme.colors.good
    StockStatus.LOW -> StockTheme.colors.warn
    StockStatus.OUT -> StockTheme.colors.critical
}

fun statusIcon(status: StockStatus): ImageVector = when (status) {
    StockStatus.OK -> Icons.Rounded.CheckCircle
    StockStatus.LOW -> Icons.Rounded.WarningAmber
    StockStatus.OUT -> Icons.Rounded.ErrorOutline
}

fun statusLabel(status: StockStatus): String = when (status) {
    StockStatus.OK -> "In stock"
    StockStatus.LOW -> "Low"
    StockStatus.OUT -> "Out"
}

@Composable
fun StatusPill(status: StockStatus, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(statusIcon(status), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(statusLabel(status), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun ProductRow(product: ProductEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val status = product.status
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Rounded.Inventory2, statusColor(status))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(product.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = product.subtitle
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(product.quantityText(), style = MaterialTheme.typography.titleSmall)
            val bags = product.bagsText()
            if (status != StockStatus.OK) {
                StatusPill(status, Modifier.padding(top = 2.dp))
            } else if (bags != null) {
                Text(bags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class MovementStyle(val icon: ImageVector, val color: Color, val label: String)

@Composable
private fun movementStyle(m: MovementEntity): MovementStyle = when (m.type) {
    MovementType.IN -> MovementStyle(Icons.AutoMirrored.Rounded.TrendingUp, StockTheme.colors.seriesIn, "Stock in")
    MovementType.OUT -> MovementStyle(Icons.AutoMirrored.Rounded.TrendingDown, StockTheme.colors.seriesOut, "Stock out")
    MovementType.COUNT -> MovementStyle(Icons.Rounded.FactCheck, MaterialTheme.colorScheme.tertiary, "Stock count")
    else -> MovementStyle(Icons.Rounded.Inventory2, MaterialTheme.colorScheme.onSurfaceVariant, "Opening")
}

@Composable
fun MovementRow(movement: MovementEntity, modifier: Modifier = Modifier, showTime: Boolean = true, onClick: (() -> Unit)? = null) {
    val style = movementStyle(movement)
    val delta = movement.delta
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(style.icon, style.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(movement.productLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (movement.source == MovementSource.AI) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = "AI update",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp).padding(end = 2.dp),
                    )
                }
                Text(
                    buildString {
                        append(style.label)
                        if (showTime) append(" · ").append(formatTime(movement.timestamp))
                        if (movement.note.isNotBlank()) append(" · ").append(movement.note)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            val sign = if (delta > 0) "+" else if (delta < 0) "−" else "±"
            Text(
                "$sign${kotlin.math.abs(delta).grouped()}",
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    delta > 0 -> StockTheme.colors.seriesIn
                    delta < 0 -> StockTheme.colors.seriesOut
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "→ ${movement.newQty.grouped()} ${unitLabel(movement.unit, movement.newQty)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 56)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 68.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Tonal chip-style segmented toggle used on Add Stock and filters. */
@Composable
fun <T> SegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icons: Map<T, ImageVector> = emptyMap(),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(4.dp)) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { onSelect(value) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icons[value]?.let {
                        Icon(
                            it,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

