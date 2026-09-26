package com.example.tpglstock.ui.product

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tpglstock.data.formatDate
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.local.MovementEntity
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.startOfDay
import com.example.tpglstock.data.unitLabel
import com.example.tpglstock.ui.components.Panel
import com.example.tpglstock.ui.components.SegmentTabs
import com.example.tpglstock.ui.components.deltaColor
import com.example.tpglstock.ui.components.deltaText
import com.example.tpglstock.ui.theme.StockTheme

enum class ChartRange(val label: String, val days: Int, val tickPattern: String) {
    WEEK("Week", 7, "EEE"),
    MONTH("Month", 30, "d MMM"),
    YEAR("Year", 365, "MMM yy"),
}

private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * Stock level over the range as (time, quantity) steps, ending at the current quantity.
 * Walks back from the current quantity using each change's delta, so records logged
 * out of order (e.g. a backfilled opening stock) don't break the line.
 */
private fun stockSeries(p: ProductEntity, history: List<MovementEntity>, start: Long, now: Long): List<Pair<Long, Long>> {
    val inRange = history.filter { it.timestamp in start..now }.sortedByDescending { it.timestamp }
    val steps = ArrayList<Pair<Long, Long>>()
    var level = p.quantity
    steps += now to level
    for (m in inRange) {
        steps += m.timestamp to level
        level = (level - m.delta).coerceAtLeast(0)
    }
    steps += start to level
    return steps.reversed()
}

/** Step chart of a product's stock level for the last week, month or year. */
@Composable
fun StockChart(p: ProductEntity, history: List<MovementEntity>) {
    val c = StockTheme.colors
    var range by rememberSaveable { mutableStateOf(ChartRange.MONTH) }
    val now = remember(history, p.quantity) { System.currentTimeMillis() }
    val start = startOfDay(now) - (range.days - 1) * DAY_MS
    val series = remember(p, history, range, now) { stockSeries(p, history, start, now) }
    val change = p.quantity - series.first().second
    val top = (maxOf(series.maxOf { it.second }, p.reorderLevel) * 1.1).toLong().coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SegmentTabs(ChartRange.entries.map { it to it.label }, range, { range = it })
        Panel(shape = RoundedCornerShape(20.dp), padding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Text("Change this ${range.label.lowercase()}", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.weight(1f))
                    Text(
                        "${deltaText(change)} ${unitLabel(p.unit, kotlin.math.abs(change))}",
                        style = MaterialTheme.typography.labelLarge,
                        color = deltaColor(change),
                    )
                }
                Text(top.grouped(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = c.faint)
                val line = c.ink
                val fill = c.accent
                val reorder = c.low.bar
                val grid = c.line
                Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                    val span = (now - start).toFloat()
                    fun x(t: Long) = (t - start) / span * size.width
                    fun y(q: Long) = size.height - q.toFloat() / top * size.height

                    drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())

                    val path = Path().apply {
                        moveTo(0f, y(series.first().second))
                        for (i in 1 until series.size) {
                            val (t, q) = series[i]
                            lineTo(x(t), y(series[i - 1].second))
                            lineTo(x(t), y(q))
                        }
                    }
                    val area = Path().apply {
                        addPath(path)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(fill.copy(alpha = 0.22f), fill.copy(alpha = 0.02f))))
                    drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                    if (p.reorderLevel > 0) {
                        val ry = y(p.reorderLevel)
                        drawLine(reorder, Offset(0f, ry), Offset(size.width, ry), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                    }
                    drawCircle(fill, 4.dp.toPx(), Offset(size.width, y(p.quantity)))
                }
                Row {
                    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                    Text(formatDate(start, range.tickPattern), style = labelStyle, color = c.faint, modifier = Modifier.weight(1f))
                    Text(formatDate(start + (now - start) / 2, range.tickPattern), style = labelStyle, color = c.faint, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("Now", style = labelStyle, color = c.faint, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                if (p.reorderLevel > 0) {
                    Text("Dashed line: reorder at ${p.reorderLevel.grouped()}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = c.muted)
                }
            }
        }
    }
}
