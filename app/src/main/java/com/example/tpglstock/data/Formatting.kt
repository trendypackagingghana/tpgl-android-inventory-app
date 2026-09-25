package com.example.tpglstock.data

import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.local.StockUnit
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

fun Long.grouped(): String = numberFormat.format(this)
fun Int.grouped(): String = numberFormat.format(this)

/** "Spray Bottles · 500ml Clear" style label used across the app. */
val ProductEntity.title: String
    get() = if (name.equals("Standard", ignoreCase = true)) category.toTitleCase() else name

val ProductEntity.subtitle: String
    get() = listOf(size, if (name.equals("Standard", true)) "" else category.toTitleCase())
        .filter { it.isNotBlank() }
        .joinToString(" · ")

val ProductEntity.label: String
    get() = listOf(category.toTitleCase(), size, name.takeUnless { it.equals("Standard", true) } ?: "")
        .filter { it.isNotBlank() }
        .joinToString(" · ")

fun String.toTitleCase(): String {
    val words = lowercase(Locale.US).split(" ").joinToString(" ") { word ->
        // Short words like "AV" are usually abbreviations; keep them upper case.
        if (word.length <= 2 && word.all { it.isLetter() }) word.uppercase(Locale.US)
        else word.replaceFirstChar { it.titlecase(Locale.US) }
    }
    // Capitalise the first letter after an opening bracket, e.g. "(small Neck)" -> "(Small Neck)".
    return Regex("\\((\\w)").replace(words) { "(" + it.groupValues[1].uppercase(Locale.US) }
}

fun unitLabel(unit: String, qty: Long): String = when (unit) {
    StockUnit.BAGS -> if (qty == 1L) "bag" else "bags"
    else -> "pcs"
}

fun ProductEntity.quantityText(qty: Long = quantity): String = "${qty.grouped()} ${unitLabel(unit, qty)}"

/** Bag equivalent for piece-counted products, e.g. "8 bags" or "7.5 bags". Null when not bagged. */
fun ProductEntity.bagsText(qty: Long = quantity): String? {
    if (unit != StockUnit.PCS || pcsPerBag <= 0) return null
    val bags = qty.toDouble() / pcsPerBag
    val text = if (bags % 1.0 == 0.0) bags.toLong().grouped() else String.format(Locale.US, "%.1f", bags)
    return "$text ${if (bags == 1.0) "bag" else "bags"}"
}

enum class StockStatus { OK, LOW, OUT }

val ProductEntity.status: StockStatus
    get() = when {
        quantity <= 0 -> StockStatus.OUT
        quantity <= reorderLevel -> StockStatus.LOW
        else -> StockStatus.OK
    }

fun formatDate(ts: Long, pattern: String = "EEE d MMM yyyy"): String =
    SimpleDateFormat(pattern, Locale.UK).format(Date(ts))

fun formatTime(ts: Long): String = SimpleDateFormat("HH:mm", Locale.UK).format(Date(ts))

fun relativeTime(ts: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - ts
    val min = diff / 60_000
    return when {
        min < 1 -> "just now"
        min < 60 -> "${min}m ago"
        min < 60 * 24 -> "${min / 60}h ago"
        min < 60 * 24 * 7 -> "${min / (60 * 24)}d ago"
        else -> formatDate(ts, "d MMM")
    }
}

/** Short name used in lists, e.g. "500ml Clear"; the category when the product has no variant. */
val ProductEntity.shortTitle: String
    get() = when {
        unit == StockUnit.BAGS -> if (name.equals("Standard", true)) category.toTitleCase() else name
        name.equals("Standard", true) -> listOf(size, category.toTitleCase()).filter { it.isNotBlank() }.joinToString(" ")
        else -> listOf(size, name).filter { it.isNotBlank() }.joinToString(" ")
    }

/** What kind of product it is, shown under [shortTitle]. */
val ProductEntity.kind: String
    get() = when {
        unit == StockUnit.BAGS -> "Raw material"
        name.equals("Standard", true) -> ""
        else -> category.toTitleCase()
    }

/** Stock level for bars: full at three times the reorder level, with a sliver while any stock is left. */
val ProductEntity.levelFraction: Float
    get() = when {
        quantity <= 0 -> 0f
        reorderLevel <= 0 -> 1f
        else -> (quantity.toFloat() / (reorderLevel * 3)).coerceIn(0.03f, 1f)
    }

fun startOfDay(ts: Long): Long = java.util.Calendar.getInstance().run {
    timeInMillis = ts
    set(java.util.Calendar.HOUR_OF_DAY, 0)
    set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0)
    set(java.util.Calendar.MILLISECOND, 0)
    timeInMillis
}

/** "Today", "Yesterday" or "Wednesday 23 Sep". */
fun dayLabel(ts: Long, now: Long = System.currentTimeMillis()): String {
    val days = Math.round((startOfDay(now) - startOfDay(ts)) / (24 * 60 * 60 * 1000.0))
    return when (days) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> formatDate(ts, "EEEE d MMM")
    }
}
