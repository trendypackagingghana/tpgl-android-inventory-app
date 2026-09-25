package com.example.tpglstock.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tpglstock.data.StockRepository
import com.example.tpglstock.data.StockStatus
import com.example.tpglstock.data.SyncStatus
import com.example.tpglstock.data.local.MovementEntity
import com.example.tpglstock.data.local.MovementType
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.local.StockUnit
import com.example.tpglstock.data.status
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

data class DayActivity(val dayStart: Long, val increase: Long, val decrease: Long, val updates: Int)

data class DashboardState(
    val loading: Boolean = true,
    /** Bag-counted raw materials (blow and injection material), largest first. */
    val rawMaterials: List<ProductEntity> = emptyList(),
    /** Piece-counted products with the most stock updates recently. */
    val popular: List<ProductEntity> = emptyList(),
    val inStock: Int = 0,
    val low: List<ProductEntity> = emptyList(),
    val out: List<ProductEntity> = emptyList(),
    val week: List<DayActivity> = emptyList(),
    /** When the newest recorded movement happened (its data date), not when it was uploaded. */
    val lastUpdate: Long? = null,
    val sync: SyncStatus = SyncStatus(),
)

class DashboardViewModel(private val repo: StockRepository) : ViewModel() {

    private val weekStart: Long = startOfDay(System.currentTimeMillis()) - 6 * DAY

    val state: StateFlow<DashboardState> = combine(
        repo.products,
        repo.movements,
        repo.status,
    ) { products, movements, status ->
        val week = movements.filter { it.timestamp >= weekStart }
        DashboardState(
            loading = !status.loaded,
            rawMaterials = products.filter { it.unit == StockUnit.BAGS }.sortedByDescending { it.quantity },
            popular = popular(products, movements),
            inStock = products.count { it.status == StockStatus.OK },
            low = products.filter { it.status == StockStatus.LOW }.sortedBy { it.quantity },
            out = products.filter { it.status == StockStatus.OUT },
            week = buildWeek(week),
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
     * Ranks piece-counted products by how often they were updated in the last
     * [POPULAR_WINDOW], then all time. Products never updated are left out.
     */
    private fun popular(products: List<ProductEntity>, movements: List<MovementEntity>): List<ProductEntity> {
        val tracked = movements.filter { it.type != MovementType.OPENING && it.unit == StockUnit.PCS }
        val since = System.currentTimeMillis() - POPULAR_WINDOW
        val recent = tracked.filter { it.timestamp >= since }.groupingBy { it.productId }.eachCount()
        val allTime = tracked.groupingBy { it.productId }.eachCount()
        return products
            .filter { it.unit == StockUnit.PCS && it.id in allTime }
            .sortedWith(compareByDescending<ProductEntity> { recent[it.id] ?: 0 }.thenByDescending { allTime[it.id] ?: 0 })
            .take(POPULAR_COUNT)
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
                updates = day.size,
            )
        }
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        const val PULL_INTERVAL = 60_000L
        const val POPULAR_WINDOW = 30 * DAY
        const val POPULAR_COUNT = 5

        fun startOfDay(ts: Long): Long = Calendar.getInstance().run {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }
    }
}
