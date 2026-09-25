package com.example.tpglstock.data.local

object StockUnit {
    const val PCS = "pcs"
    const val BAGS = "bags"
}

object MovementType {
    const val IN = "IN"
    const val OUT = "OUT"
    const val COUNT = "COUNT"
    const val OPENING = "OPENING"
}

object MovementSource {
    const val MANUAL = "MANUAL"
    const val AI = "AI"
    const val SEED = "SEED"
}

data class ProductEntity(
    val id: Long = 0,
    /** Group heading as used on WhatsApp, e.g. "SPRAY BOTTLES". */
    val category: String,
    /** Colour or variant, e.g. "Clear". "Standard" when the product has no variants. */
    val name: String,
    /** Capacity, e.g. "500ml". Empty when not applicable. */
    val size: String = "",
    val unit: String = StockUnit.PCS,
    /** Pieces in one bag. 0 when unknown or not bagged. */
    val pcsPerBag: Int = 0,
    /** Quantity on hand, expressed in [unit]. */
    val quantity: Long = 0,
    /** Low-stock alert threshold, expressed in [unit]. */
    val reorderLevel: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class MovementEntity(
    val id: Long = 0,
    val productId: Long,
    /** Snapshot of the product label so history reads well after renames. */
    val productLabel: String,
    val category: String,
    val unit: String,
    val type: String,
    val previousQty: Long,
    val newQty: Long,
    val source: String,
    val note: String = "",
    /** Changes applied together (one form submit or one AI update) share a batch id. */
    val batchId: String,
    val timestamp: Long,
) {
    val delta: Long get() = newQty - previousQty
}
