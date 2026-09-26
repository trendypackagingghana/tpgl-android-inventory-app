package com.example.tpglstock.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tpglstock.data.SettingsStore
import com.example.tpglstock.data.StockRepository
import com.example.tpglstock.data.StockStatus
import com.example.tpglstock.data.SyncStatus
import com.example.tpglstock.data.WeeklySummary
import com.example.tpglstock.data.weeklySummary
import com.example.tpglstock.data.local.MovementEntity
import com.example.tpglstock.data.local.MovementType
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.local.StockUnit
import com.example.tpglstock.data.startOfDay
import com.example.tpglstock.data.status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DayActivity(val dayStart: Long, val increase: Long, val decrease: Long, val updates: Int)

/** A frequently updated product and how often it changed. */
data class Mover(val product: ProductEntity, val detail: String)

data class DashboardState(
    val loading: Boolean = true,
    val name: String = "",
    /** Out-of-stock products first, then low ones. */
    val attention: List<ProductEntity> = emptyList(),
    val outCount: Int = 0,
    val lowCount: Int = 0,
    /** Last full Monday–Sunday week; changes over every Monday. */
    val summary: WeeklySummary? = null,
    /** Bag-counted raw materials (blow and injection material), largest first. */
    val rawMaterials: List<ProductEntity> = emptyList(),
    val movers: List<Mover> = emptyList(),
    val week: List<DayActivity> = emptyList(),
    val todayUpdates: Int = 0,
    /** When the newest recorded movement happened (its data date), not when it was uploaded. */
    val lastUpdate: Long? = null,
    val sync: SyncStatus = SyncStatus(),
)

class DashboardViewModel(private val repo: StockRepository, settings: SettingsStore) : ViewModel() {

    private val today: Long = startOfDay(System.currentTimeMillis())
    private val weekStart: Long = today - 6 * DAY

    val state: StateFlow<DashboardState> = combine(
        repo.products,
        repo.movements,
        repo.status,
        settings.userName,
    ) { products, movements, status, name ->
        val out = products.filter { it.status == StockStatus.OUT }
        val low = products.filter { it.status == StockStatus.LOW }.sortedBy { it.quantity }
        DashboardState(
            loading = !status.loaded,
            name = name,
            attention = out + low,
            outCount = out.size,
            lowCount = low.size,
            summary = if (status.loaded) weeklySummary(products, movements) else null,
            rawMaterials = products.filter { it.unit == StockUnit.BAGS }.sortedByDescending { it.quantity },
            movers = movers(products, movements),
            week = buildWeek(movements.filter { it.timestamp >= weekStart }),
            todayUpdates = movements.count { it.timestamp >= today && it.type != MovementType.OPENING },
            lastUpdate = movements.maxOfOrNull { it.timestamp },
            sync = status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private var lastPull = 0L

    /** Pull-to-refresh, at most once per [PULL_INTERVAL]. Returns a message to show, or null on success. */
    suspend fun pullToRefresh(): String? {
        val now = System.currentTimeMillis()
        val wait = lastPull + PULL_INTERVAL - now
        if (wait > 0) return "Refreshed recently. Try again in ${(wait + 999) / 1000}s."
        lastPull = now
        _refreshing.value = true
        return try {
            repo.refresh()
            null
        } catch (e: Exception) {
            "Couldn't refresh: ${e.message ?: "can't reach the server"}"
        } finally {
            _refreshing.value = false
        }
    }

    /**
     * Piece-counted products ranked by updates this week, then the last [RECENT_WINDOW], then all time.
     * Products never updated are left out.
     */
    private fun movers(products: List<ProductEntity>, movements: List<MovementEntity>): List<Mover> {
        val tracked = movements.filter { it.type != MovementType.OPENING && it.unit == StockUnit.PCS }
        val since = System.currentTimeMillis() - RECENT_WINDOW
        val week = tracked.filter { it.timestamp >= weekStart }.groupingBy { it.productId }.eachCount()
        val recent = tracked.filter { it.timestamp >= since }.groupingBy { it.productId }.eachCount()
        val allTime = tracked.groupingBy { it.productId }.eachCount()
        return products
            .filter { it.unit == StockUnit.PCS && it.id in allTime }
            .sortedWith(
                compareByDescending<ProductEntity> { week[it.id] ?: 0 }
                    .thenByDescending { recent[it.id] ?: 0 }
                    .thenByDescending { allTime[it.id] ?: 0 },
            )
            .take(MOVER_COUNT)
            .map { p ->
                val w = week[p.id] ?: 0
                val r = recent[p.id] ?: 0
                val detail = when {
                    w > 0 -> "${plural(w, "update")} this week"
                    r > 0 -> "${plural(r, "update")} in 30 days"
                    else -> plural(allTime[p.id] ?: 0, "update")
                }
                Mover(p, detail)
            }
    }

    private fun buildWeek(movements: List<MovementEntity>): List<DayActivity> {
        val tracked = movements.filter { it.type != MovementType.OPENING && it.unit == StockUnit.PCS }
        return (0 until 7).map { i ->
            val start = weekStart + i * DAY
            val day = tracked.filter { it.timestamp in start until start + DAY }
            DayActivity(
                dayStart = start,
                increase = day.filter { it.delta > 0 }.sumOf { it.delta },
                decrease = day.filter { it.delta < 0 }.sumOf { -it.delta },
                updates = movements.count { it.timestamp in start until start + DAY && it.type != MovementType.OPENING },
            )
        }
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        const val PULL_INTERVAL = 60_000L
        const val RECENT_WINDOW = 30 * DAY
        const val MOVER_COUNT = 3

        fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"
    }
}
