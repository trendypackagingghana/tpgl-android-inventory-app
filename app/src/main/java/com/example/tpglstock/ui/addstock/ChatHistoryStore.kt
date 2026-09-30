package com.example.tpglstock.ui.addstock

import android.content.Context
import com.example.tpglstock.ai.Activity
import com.example.tpglstock.ai.ProposedEntry
import com.example.tpglstock.ai.ProposedProduct
import com.example.tpglstock.ai.Shift
import com.example.tpglstock.data.ChangeMode
import com.example.tpglstock.data.local.ProductEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Keeps the assistant conversation on this phone so it survives leaving the screen or restarting the app. */
class ChatHistoryStore(context: Context) {
    private val file = File(context.filesDir, "assistant_history.json")

    suspend fun load(): List<ChatItem> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching emptyList()
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { readItem(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** Saves the last [KEEP_MESSAGES] messages sent, with the assistant's replies to them. */
    suspend fun save(items: List<ChatItem>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        trim(items).forEach { item -> writeItem(item)?.let(arr::put) }
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }

    companion object {
        const val KEEP_MESSAGES = 10

        /** Drops everything before the [KEEP_MESSAGES]th most recent user message. */
        fun trim(items: List<ChatItem>): List<ChatItem> {
            val userIdx = items.indices.filter { items[it] is ChatItem.User }
            if (userIdx.size <= KEEP_MESSAGES) return items
            return items.drop(userIdx[userIdx.size - KEEP_MESSAGES])
        }
    }

    private fun writeItem(item: ChatItem): JSONObject? = when (item) {
        is ChatItem.Thinking -> null
        is ChatItem.User -> JSONObject().put("type", "user").put("id", item.id).put("text", item.text).put("sentAt", item.sentAt)
        is ChatItem.Error -> JSONObject().put("type", "error").put("id", item.id).put("message", item.message).put("retryText", item.retryText)
        is ChatItem.Proposal -> JSONObject()
            .put("type", "proposal")
            .put("id", item.id)
            .put("userText", item.userText)
            .put("summary", item.summary)
            .put("lines", JSONArray().apply { item.lines.forEach { put(writeLine(it)) } })
            .put("newProducts", JSONArray().apply { item.newProducts.forEach { put(writeNewProduct(it)) } })
            .put("questions", JSONArray(item.questions))
            // A save cut short by closing the app didn't finish; show it as still pending.
            .put("status", (if (item.status == ProposalStatus.APPLYING) ProposalStatus.PENDING else item.status).name)
            .put("appliedCount", item.appliedCount)
            .putOpt("applyError", item.applyError)
    }

    private fun readItem(o: JSONObject): ChatItem? = when (o.getString("type")) {
        "user" -> ChatItem.User(o.getLong("id"), o.getString("text"), o.optLong("sentAt", 0))
        "error" -> ChatItem.Error(o.getLong("id"), o.getString("message"), o.getString("retryText"))
        "proposal" -> ChatItem.Proposal(
            id = o.getLong("id"),
            userText = o.getString("userText"),
            summary = o.getString("summary"),
            lines = o.getJSONArray("lines").objects().map(::readLine),
            newProducts = o.getJSONArray("newProducts").objects().map(::readNewProduct),
            questions = o.getJSONArray("questions").let { a -> (0 until a.length()).map(a::getString) },
            status = ProposalStatus.valueOf(o.getString("status")),
            appliedCount = o.optInt("appliedCount"),
            applyError = o.optStringOrNull("applyError"),
        )
        else -> null
    }

    private fun writeLine(l: ProposalLine) = JSONObject()
        .put("index", l.index)
        .put("entry", JSONObject()
            .putOpt("productId", l.entry.productId)
            .putOpt("newProductKey", l.entry.newProductKey)
            .putOpt("date", l.entry.date)
            .put("activity", l.entry.activity.name)
            .put("shift", l.entry.shift.name)
            .put("mode", l.entry.mode.name)
            .put("quantity", l.entry.quantity)
            .put("sourceText", l.entry.sourceText))
        .putOpt("product", l.product?.let(::writeProduct))
        .putOpt("newProduct", l.newProduct?.let(::writeNewProduct))
        .putOpt("dayStart", l.dayStart)
        .putOpt("timestamp", l.timestamp)
        .put("selected", l.selected)
        .putOpt("duplicate", l.duplicate?.name)
        .put("before", l.before)
        .put("after", l.after)

    private fun readLine(o: JSONObject): ProposalLine {
        val e = o.getJSONObject("entry")
        return ProposalLine(
            index = o.getInt("index"),
            entry = ProposedEntry(
                productId = e.optLongOrNull("productId"),
                newProductKey = e.optStringOrNull("newProductKey"),
                date = e.optStringOrNull("date"),
                activity = Activity.valueOf(e.getString("activity")),
                shift = Shift.valueOf(e.getString("shift")),
                mode = ChangeMode.valueOf(e.getString("mode")),
                quantity = e.getLong("quantity"),
                sourceText = e.optString("sourceText"),
            ),
            product = o.optJSONObject("product")?.let(::readProduct),
            newProduct = o.optJSONObject("newProduct")?.let(::readNewProduct),
            dayStart = o.optLongOrNull("dayStart"),
            timestamp = o.optLongOrNull("timestamp"),
            selected = o.getBoolean("selected"),
            duplicate = o.optStringOrNull("duplicate")?.let(DuplicateKind::valueOf),
            before = o.optLong("before"),
            after = o.optLong("after"),
        )
    }

    private fun writeProduct(p: ProductEntity) = JSONObject()
        .put("id", p.id)
        .put("category", p.category)
        .put("name", p.name)
        .put("size", p.size)
        .put("unit", p.unit)
        .put("pcsPerBag", p.pcsPerBag)
        .put("quantity", p.quantity)
        .put("reorderLevel", p.reorderLevel)

    private fun readProduct(o: JSONObject) = ProductEntity(
        id = o.getLong("id"),
        category = o.getString("category"),
        name = o.getString("name"),
        size = o.optString("size"),
        unit = o.getString("unit"),
        pcsPerBag = o.optInt("pcsPerBag"),
        quantity = o.optLong("quantity"),
        reorderLevel = o.optLong("reorderLevel"),
    )

    private fun writeNewProduct(n: ProposedProduct) = JSONObject()
        .put("key", n.key)
        .put("category", n.category)
        .put("name", n.name)
        .put("size", n.size)
        .put("unit", n.unit)
        .put("pcsPerBag", n.pcsPerBag)
        .put("sourceText", n.sourceText)

    private fun readNewProduct(o: JSONObject) = ProposedProduct(
        key = o.getString("key"),
        category = o.getString("category"),
        name = o.getString("name"),
        size = o.optString("size"),
        unit = o.getString("unit"),
        pcsPerBag = o.optInt("pcsPerBag"),
        sourceText = o.optString("sourceText"),
    )
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)
