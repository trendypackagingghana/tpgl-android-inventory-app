package com.example.tpglstock.data

import com.example.tpglstock.data.local.MovementEntity
import com.example.tpglstock.data.local.MovementType
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.local.StockUnit
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

enum class InsightTone { CONCERN, IMPROVE, GOOD }

data class Insight(val tone: InsightTone, val title: String, val detail: String, val productId: Long? = null)

/** Totals for one Monday–Sunday week. */
data class WeekTotals(
    /** Pieces added to finished-goods stock (production and deliveries in). */
    val added: Long,
    /** Pieces taken out of finished-goods stock. */
    val removed: Long,
    /** Bags of raw material used. */
    val bagsUsed: Long,
    val bagsReceived: Long,
    val updates: Int,
    /** Days of the week with at least one update. */
    val activeDays: Int,
)

data class WeeklySummary(
    /** Monday 00:00 of the summarised week. */
    val start: Long,
    /** Sunday 23:59:59.999 of the summarised week. */
    val end: Long,
    val totals: WeekTotals,
    /** Average of earlier weeks that had any updates, or null when there is no history to compare. */
    val usual: WeekTotals?,
    val baselineWeeks: Int,
    val insights: List<Insight>,
) {
    val rangeText: String
        get() = if (formatDate(start, "MMM") == formatDate(end, "MMM")) "${formatDate(start, "d")}–${formatDate(end, "d MMM yyyy")}"
        else "${formatDate(start, "d MMM")} – ${formatDate(end, "d MMM yyyy")}"

    /** The first concern, else the first tip, else the first good sign. */
    val headline: Insight? get() = insights.firstOrNull()
}

/** Short title plus what kind of product it is, without repeating e.g. "Debbies Cover". */
private val ProductEntity.fullName: String
    get() = if (kind.isBlank() || shortTitle.contains(kind, ignoreCase = true)) shortTitle else "$shortTitle $kind"

private const val DAY = 24 * 60 * 60 * 1000L
private const val WEEK = 7 * DAY
private const val BASELINE_WEEKS = 4

/** Monday 00:00 of the week before the one containing [now]; rolls over every Monday. */
fun previousWeekStart(now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().run {
    timeInMillis = startOfDay(now)
    val sinceMonday = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    add(Calendar.DAY_OF_YEAR, -sinceMonday - 7)
    timeInMillis
}

private fun totals(moves: List<MovementEntity>, start: Long): WeekTotals {
    val pcs = moves.filter { it.unit == StockUnit.PCS }
    val bags = moves.filter { it.unit == StockUnit.BAGS }
    return WeekTotals(
        added = pcs.filter { it.delta > 0 }.sumOf { it.delta },
        removed = pcs.filter { it.delta < 0 }.sumOf { -it.delta },
        bagsUsed = bags.filter { it.delta < 0 }.sumOf { -it.delta },
        bagsReceived = bags.filter { it.delta > 0 }.sumOf { it.delta },
        updates = moves.size,
        activeDays = moves.map { ((it.timestamp - start) / DAY).toInt() }.distinct().size,
    )
}

private fun average(weeks: List<WeekTotals>) = WeekTotals(
    added = weeks.sumOf { it.added } / weeks.size,
    removed = weeks.sumOf { it.removed } / weeks.size,
    bagsUsed = weeks.sumOf { it.bagsUsed } / weeks.size,
    bagsReceived = weeks.sumOf { it.bagsReceived } / weeks.size,
    updates = weeks.sumOf { it.updates } / weeks.size,
    activeDays = weeks.sumOf { it.activeDays } / weeks.size,
)

/** Percentage change from [usual] to [now], or null when there's nothing to compare against. */
private fun change(now: Long, usual: Long): Int? = if (usual <= 0) null else ((now - usual) * 100.0 / usual).roundToInt()

private fun weeks(n: Double): String = when {
    n < 1 -> "under a week"
    n < 1.5 -> "about 1 week"
    else -> "about ${n.roundToInt()} weeks"
}

/** Summary of the last full Monday–Sunday week, compared with the weeks before it. */
fun weeklySummary(products: List<ProductEntity>, movements: List<MovementEntity>, now: Long = System.currentTimeMillis()): WeeklySummary {
    val start = previousWeekStart(now)
    val end = start + WEEK - 1
    // A product's first count from zero just records stock that was already there, not production.
    val firstCounts = movements.filter { it.type != MovementType.OPENING }.groupBy { it.productId }
        .mapNotNull { (_, list) -> list.minBy { it.timestamp }.takeIf { it.previousQty == 0L }?.id }.toSet()
    val tracked = movements.filter { it.type != MovementType.OPENING && it.id !in firstCounts }
    fun weekMoves(s: Long) = tracked.filter { it.timestamp in s until s + WEEK }

    val week = weekMoves(start)
    val t = totals(week, start)
    val earlier = (1..BASELINE_WEEKS).map { start - it * WEEK }.map { s -> weekMoves(s) to s }.filter { it.first.isNotEmpty() }
    val usual = earlier.takeIf { it.isNotEmpty() }?.let { list -> average(list.map { (m, s) -> totals(m, s) }) }
    val byId = products.associateBy { it.id }

    val concerns = mutableListOf<Insight>()
    val improve = mutableListOf<Insight>()
    val good = mutableListOf<Insight>()

    // Raw materials: pace against usual, and how long what's left will last.
    val rawUsedLastWeek = week.filter { it.unit == StockUnit.BAGS && it.delta < 0 }.groupBy { it.productId }.mapValues { e -> e.value.sumOf { -it.delta } }
    val rawUsedEarlier = earlier.flatMap { it.first }.filter { it.unit == StockUnit.BAGS && it.delta < 0 }.groupBy { it.productId }
        .mapValues { e -> e.value.sumOf { -it.delta } / earlier.size.toDouble() }
    products.filter { it.unit == StockUnit.BAGS }.forEach { p ->
        val used = rawUsedLastWeek[p.id] ?: 0L
        val usualUse = rawUsedEarlier[p.id] ?: 0.0
        val pace = listOf(used.toDouble(), usualUse).filter { it > 0 }.maxOrNull()
        if (usualUse > 0 && used > usualUse * 1.25) {
            concerns += Insight(
                InsightTone.CONCERN,
                "${p.shortTitle} is running down faster than usual",
                "$used bags used last week against a usual ${usualUse.roundToInt()} (+${((used - usualUse) * 100 / usualUse).roundToInt()}%). Check for waste or a production spike.",
                p.id,
            )
        }
        if (pace != null) {
            val cover = p.quantity / pace
            when {
                p.quantity <= 0 -> concerns += Insight(InsightTone.CONCERN, "${p.shortTitle} has run out", "Production that needs it will stop until more bags arrive.", p.id)
                cover < 2 -> concerns += Insight(
                    InsightTone.CONCERN,
                    "${p.shortTitle} will last ${weeks(cover)}",
                    "${p.quantity.grouped()} bags left at about ${pace.roundToInt()} bags a week. Order more now.",
                    p.id,
                )
                cover < 4 -> improve += Insight(
                    InsightTone.IMPROVE,
                    "Plan the next ${p.shortTitle} order",
                    "${p.quantity.grouped()} bags left — ${weeks(cover)} at the current pace of ${pace.roundToInt()} bags a week.",
                    p.id,
                )
            }
        }
    }

    // Production and dispatch against usual.
    if (usual != null) {
        change(t.added, usual.added)?.let { pct ->
            when {
                pct <= -20 -> concerns += Insight(
                    InsightTone.CONCERN,
                    "Production was ${abs(pct)}% lower than usual",
                    "${t.added.grouped()} pcs added to stock against a usual ${usual.added.grouped()}. Check machine downtime, staffing or material supply.",
                )
                pct >= 20 -> good += Insight(InsightTone.GOOD, "Production up $pct%", "${t.added.grouped()} pcs added to stock against a usual ${usual.added.grouped()}.")
            }
        }
        change(t.removed, usual.removed)?.let { pct ->
            when {
                pct <= -25 -> improve += Insight(
                    InsightTone.IMPROVE,
                    "Stock going out was ${abs(pct)}% lower than usual",
                    "${t.removed.grouped()} pcs out against a usual ${usual.removed.grouped()}. Slower sales or unrecorded dispatches?",
                )
                pct >= 25 -> good += Insight(InsightTone.GOOD, "Stock going out up $pct%", "${t.removed.grouped()} pcs out against a usual ${usual.removed.grouped()}.")
            }
        }
        if (usual.bagsUsed > 0 && t.bagsUsed > 0 && usual.added > 0 && t.added > 0) {
            // Pieces made per bag of raw material: falling yield suggests waste.
            val yieldNow = t.added.toDouble() / t.bagsUsed
            val yieldUsual = usual.added.toDouble() / usual.bagsUsed
            val pct = ((yieldNow - yieldUsual) * 100 / yieldUsual).roundToInt()
            if (pct <= -20) concerns += Insight(
                InsightTone.CONCERN,
                "Fewer pieces per bag of material",
                "${yieldNow.roundToInt().grouped()} pcs per bag last week against a usual ${yieldUsual.roundToInt().grouped()} ($pct%). Possible waste or rejects.",
            )
        }
        if (t.activeDays < usual.activeDays) improve += Insight(
            InsightTone.IMPROVE,
            "Stock was updated on ${t.activeDays} of 7 days",
            "Usually ${usual.activeDays}. Daily updates keep these numbers reliable.",
        )
    }

    // Finished goods that will run out within a week at last week's pace.
    val outLastWeek = week.filter { it.unit == StockUnit.PCS && it.delta < 0 }.groupBy { it.productId }.mapValues { e -> e.value.sumOf { -it.delta } }
    outLastWeek.forEach { (id, out) ->
        val p = byId[id] ?: return@forEach
        if (p.quantity in 1 until out) concerns += Insight(
            InsightTone.CONCERN,
            "${p.fullName} may run out this week",
            "${p.quantityText()} left; ${out.grouped()} went out last week.",
            p.id,
        )
    }
    val outNow = products.filter { it.status == StockStatus.OUT }
    if (outNow.isNotEmpty()) concerns += Insight(
        InsightTone.CONCERN,
        "${outNow.size} product${if (outNow.size == 1) " is" else "s are"} out of stock",
        outNow.take(4).joinToString { it.fullName } + if (outNow.size > 4) " and ${outNow.size - 4} more" else "",
    )

    // Busiest product.
    outLastWeek.maxByOrNull { it.value }?.let { (id, out) ->
        byId[id]?.let { p -> good += Insight(InsightTone.GOOD, "Top seller: ${p.fullName}", "${out.grouped()} pcs went out last week.", p.id) }
    }

    // Counts that have gone stale.
    val lastTouched = movements.filter { it.type != MovementType.OPENING }.groupBy { it.productId }.mapValues { e -> e.value.maxOf { it.timestamp } }
    val stale = products.filter { (lastTouched[it.id] ?: 0L) < now - 30 * DAY }
    if (stale.isNotEmpty()) improve += Insight(
        InsightTone.IMPROVE,
        "${stale.size} product${if (stale.size == 1) " hasn't" else "s haven't"} been counted in 30 days",
        "A quick count keeps stock figures trustworthy: " + stale.take(3).joinToString { it.fullName } + if (stale.size > 3) "…" else ".",
    )

    if (week.isEmpty()) improve.add(0, Insight(InsightTone.IMPROVE, "No stock updates last week", "Nothing was recorded between ${formatDate(start, "d MMM")} and ${formatDate(end, "d MMM")}."))
    if (usual == null && week.isNotEmpty()) improve += Insight(
        InsightTone.IMPROVE,
        "Not enough history to compare yet",
        "Comparisons with a usual week start once there are earlier weeks of updates.",
    )

    return WeeklySummary(start, end, t, usual, earlier.size, concerns + improve + good)
}
