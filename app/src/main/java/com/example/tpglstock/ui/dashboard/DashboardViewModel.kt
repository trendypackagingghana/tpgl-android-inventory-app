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
    val totalPieces: Long = 0,
    val rawMaterialBags: Long = 0,
    val productCount: Int = 0,
    val inStock: Int = 0,
    val low: List<ProductEntity> = emptyList(),
    val out: List<ProductEntity> = emptyList(),
    val week: List<DayActivity> = emptyList(),
    val lastUpdate: Long? = null,
    val sync: SyncStatus = SyncStatus(),
)

class DashboardViewModel(private val repo: StockRepository) : ViewModel() {

    private val weekStart: Long = startOfDay(System.currentTimeMillis()) - 6 * DAY

    val state: StateFlow<DashboardState> = combine(
        repo.products,
        repo.movementsSince(weekStart),
        repo.status,
    ) { products, week, status ->
        val pcsProducts = products.filter { it.unit == StockUnit.PCS }
        DashboardState(
            loading = !status.loaded,
            totalPieces = pcsProducts.sumOf { it.quantity },
            rawMaterialBags = products.filter { it.unit == StockUnit.BAGS }.sumOf { it.quantity },
            productCount = products.size,
            inStock = products.count { it.status == StockStatus.OK },
            low = products.filter { it.status == StockStatus.LOW }.sortedBy { it.quantity },
            out = products.filter { it.status == StockStatus.OUT },
            week = buildWeek(week),
            lastUpdate = products.maxOfOrNull { it.updatedAt },
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
