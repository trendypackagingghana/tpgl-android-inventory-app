package com.example.tpglstock.data

import com.example.tpglstock.data.local.MovementEntity
import com.example.tpglstock.data.local.MovementSource
import com.example.tpglstock.data.local.MovementType
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.remote.SupabaseApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

enum class ChangeMode { ADD, REMOVE, SET }

data class StockChange(
    val productId: Long,
    val mode: ChangeMode,
    /** Amount in the product's own unit. */
    val amount: Long,
    val note: String = "",
    /** When the movement happened. Null means now. */
    val timestamp: Long? = null,
)

data class SyncStatus(
    /** At least one full load from Supabase has succeeded. */
    val loaded: Boolean = false,
    /** Writes sent to Supabase and not yet confirmed. */
    val saving: Int = 0,
    /** Last read or write error; cleared by the next successful refresh. */
    val error: String? = null,
    /** When the in-memory copy last matched the database. */
    val lastSynced: Long? = null,
) {
    /** Every write is confirmed and the copy matches the database. */
    val inSync: Boolean get() = loaded && saving == 0 && error == null
}

/**
 * Single entry point for stock data, stored in Supabase.
 *
 * Reads come from an in-memory copy that loads on start and refreshes after every
 * write or on [refresh] (pull-to-refresh). Writes go through Postgres functions
 * so multi-step changes stay atomic.
 */
class StockRepository(private val api: SupabaseApi, scope: CoroutineScope) {
    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    private val _movements = MutableStateFlow<List<MovementEntity>>(emptyList())
    private val _status = MutableStateFlow(SyncStatus())
    private val refreshLock = Mutex()

    val products: Flow<List<ProductEntity>> = _products
    val categories: Flow<List<String>> = _products.map { list -> list.map { it.category }.distinct().sorted() }
    val movements: Flow<List<MovementEntity>> = _movements
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    init {
        scope.launch { runCatching { refresh() } }
    }

    fun product(id: Long): Flow<ProductEntity?> = _products.map { list -> list.find { it.id == id } }
    fun productMovements(id: Long): Flow<List<MovementEntity>> = _movements.map { list -> list.filter { it.productId == id } }
    fun movementsSince(since: Long): Flow<List<MovementEntity>> =
        _movements.map { list -> list.filter { it.timestamp >= since }.sortedBy { it.timestamp } }

    suspend fun allProducts(): List<ProductEntity> {
        runCatching { refresh() }
        return _products.value
    }

    /** Reloads everything from Supabase. Throws on network or server errors. */
    suspend fun refresh() = refreshLock.withLock {
        try {
            val products = api.selectAll("products", "category,size,name").map(::toProduct)
            val movements = api.selectAll("movements", "occurred_at.desc,id.desc").map(::toMovement)
            _products.value = products
            _movements.value = movements
            _status.update { it.copy(loaded = true, error = null, lastSynced = System.currentTimeMillis()) }
        } catch (e: Exception) {
            _status.update { it.copy(error = e.message ?: "Can't reach the server") }
            throw e
        }
    }

    /**
     * Applies changes atomically, in the order given, and records history.
     * Returns how many changes altered stock.
     */
    suspend fun applyChanges(changes: List<StockChange>, source: String, batchNote: String = ""): Int {
        if (changes.isEmpty()) return 0
        return applyBatch(emptyList(), changes, source, batchNote).applied
    }

    /**
     * Creates [newProducts] (at zero stock) and then applies [changes] in one transaction.
     * [changes] receives the ids of the created products, in the same order as [newProducts].
     */
    suspend fun applyWithNewProducts(
        newProducts: List<ProductEntity>,
        changes: (newIds: List<Long>) -> List<StockChange>,
        source: String,
        batchNote: String,
    ): Int {
        // Placeholder ids -1, -2, ... are resolved to the real ids by the database function.
        val placeholders = newProducts.indices.map { -(it + 1L) }
        val created = newProducts.map { it.copy(quantity = 0) }
        return applyBatch(created, changes(placeholders), source, batchNote, newNote = "Added from $batchNote").applied
    }

    /** True when history already has the same movement for that product on that day. */
    fun hasSimilarMovement(productId: Long, mode: ChangeMode, amount: Long, dayStart: Long): Boolean {
        val type = mode.movementType
        val dayEnd = dayStart + 24 * 60 * 60 * 1000L
        return _movements.value.any {
            it.productId == productId && it.type == type && it.timestamp in dayStart until dayEnd &&
                kotlin.math.abs(it.delta) == amount
        }
    }

    /** Creates a product and logs its opening quantity. */
    suspend fun addProduct(product: ProductEntity, source: String = MovementSource.MANUAL, note: String = ""): Long =
        applyBatch(listOf(product), emptyList(), source, "", newNote = note).newIds.first()

    /** Updates product details (not quantity; use [applyChanges] for that). */
    suspend fun updateDetails(product: ProductEntity) = write {
        api.rpc(
            "update_product",
            JSONObject()
                .put("p_id", product.id)
                .put("p_category", product.category)
                .put("p_name", product.name)
                .put("p_size", product.size)
                .put("p_unit", product.unit)
                .put("p_pcs_per_bag", product.pcsPerBag)
                .put("p_reorder_level", product.reorderLevel),
        )
        refreshQuietly()
    }

    suspend fun deleteProduct(product: ProductEntity) = write {
        api.delete("products", product.id)
        refreshQuietly()
    }

    /** Tracks a write in [status] so the UI can show it is pending or failed. */
    private suspend fun <T> write(block: suspend () -> T): T {
        _status.update { it.copy(saving = it.saving + 1) }
        return try {
            block()
        } catch (e: Exception) {
            _status.update { it.copy(error = e.message ?: "Can't reach the server") }
            throw e
        } finally {
            _status.update { it.copy(saving = it.saving - 1) }
        }
    }

    private class BatchResult(val applied: Int, val newIds: List<Long>)

    private suspend fun applyBatch(
        newProducts: List<ProductEntity>,
        changes: List<StockChange>,
        source: String,
        batchNote: String,
        newNote: String = "",
    ): BatchResult = write {
        val labels = _products.value.associate { it.id to it.label }
        val newJson = JSONArray(
            newProducts.map { p ->
                JSONObject()
                    .put("category", p.category)
                    .put("name", p.name)
                    .put("size", p.size)
                    .put("unit", p.unit)
                    .put("pcs_per_bag", p.pcsPerBag)
                    .put("quantity", p.quantity)
                    .put("reorder_level", p.reorderLevel)
                    .put("label", p.label)
                    .put("note", newNote)
            },
        )
        val changeJson = JSONArray(
            changes.map { c ->
                val label = if (c.productId < 0) newProducts[(-c.productId - 1).toInt()].label else labels[c.productId]
                JSONObject()
                    .put("product_id", c.productId)
                    .put("mode", c.mode.name)
                    .put("amount", c.amount)
                    .put("note", c.note)
                    .put("label", label.orEmpty())
                    .apply { c.timestamp?.let { put("timestamp", it) } }
            },
        )
        val result = JSONObject(
            api.rpc(
                "apply_batch",
                JSONObject()
                    .put("p_new_products", newJson)
                    .put("p_changes", changeJson)
                    .put("p_source", source)
                    .put("p_batch_note", batchNote),
            ),
        )
        refreshQuietly()
        val ids = result.getJSONArray("new_ids")
        BatchResult(result.getInt("applied"), List(ids.length()) { ids.getLong(it) })
    }

    /** The write already succeeded; a failed reload shows as "Not synced" until the next refresh. */
    private suspend fun refreshQuietly() {
        runCatching { refresh() }
    }

    private fun toProduct(o: JSONObject) = ProductEntity(
        id = o.getLong("id"),
        category = o.getString("category"),
        name = o.getString("name"),
        size = o.getString("size"),
        unit = o.getString("unit"),
        pcsPerBag = o.getInt("pcs_per_bag"),
        quantity = o.getLong("quantity"),
        reorderLevel = o.getLong("reorder_level"),
        updatedAt = parseTime(o.getString("updated_at")),
    )

    private fun toMovement(o: JSONObject) = MovementEntity(
        id = o.getLong("id"),
        productId = o.getLong("product_id"),
        productLabel = o.getString("product_label"),
        category = o.getString("category"),
        unit = o.getString("unit"),
        type = o.getString("type"),
        previousQty = o.getLong("previous_qty"),
        newQty = o.getLong("new_qty"),
        source = o.getString("source"),
        note = o.getString("note"),
        batchId = o.getString("batch_id"),
        timestamp = parseTime(o.getString("occurred_at")),
    )

    private fun parseTime(s: String): Long = OffsetDateTime.parse(s).toInstant().toEpochMilli()

    private val ChangeMode.movementType: String
        get() = when (this) {
            ChangeMode.ADD -> MovementType.IN
            ChangeMode.REMOVE -> MovementType.OUT
            ChangeMode.SET -> MovementType.COUNT
        }
}
