package com.example.tpglstock.ai

import com.example.tpglstock.data.AiSettings
import com.example.tpglstock.data.ChangeMode
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.local.StockUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Activity { PRODUCTION, SALE, RECEIVED, RETURN, COUNT, ADJUSTMENT }

enum class Shift { DAY, NIGHT, NONE }

/** One stock movement read from the message, e.g. "Night production, Mikesh Brown, +2,000 pcs on 14/09". */
data class ProposedEntry(
    /** Catalog product id, or null when [newProductKey] points at a product to create. */
    val productId: Long?,
    val newProductKey: String?,
    /** Local date the movement happened, "yyyy-MM-dd", or null if the message doesn't say. */
    val date: String?,
    val activity: Activity,
    val shift: Shift,
    val mode: ChangeMode,
    val quantity: Long,
    val sourceText: String,
)

data class ProposedProduct(
    val key: String,
    val category: String,
    val name: String,
    val size: String,
    val unit: String,
    val pcsPerBag: Int,
    val sourceText: String,
)

data class AiProposal(
    val summary: String,
    val entries: List<ProposedEntry>,
    val newProducts: List<ProposedProduct>,
    val questions: List<String>,
)

/** One earlier exchange, replayed so follow-ups like "actually make that 3 bags" work. */
data class AiTurn(val userText: String, val assistantText: String)

class AiException(message: String) : Exception(message)

/** Turns WhatsApp stock updates into structured stock movements using DeepSeek (OpenAI-compatible API). */
class StockAiService {

    suspend fun interpret(
        message: String,
        history: List<AiTurn>,
        catalog: List<ProductEntity>,
        settings: AiSettings,
    ): AiProposal = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) {
            throw AiException("Add your DeepSeek API key to secrets.properties and rebuild, or paste it in Settings.")
        }
        val body = requestBody(message, history, catalog, settings)

        // JSON mode can occasionally return empty content; one retry covers it.
        var text = ""
        for (attempt in 1..2) {
            text = post(settings.effectiveApiKey, body)
            if (text.isNotBlank()) break
        }
        if (text.isBlank()) throw AiException("The AI returned an empty response. Try again.")
        parse(text, catalog)
    }

    private fun post(apiKey: String, body: JSONObject): String {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            // Thinking mode on a multi-day paste can take a few minutes.
            readTimeout = 300_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw AiException(errorMessage(code, raw))

            val choice = JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)
                ?: throw AiException("Couldn't read the AI response. Try again.")
            if (choice.optString("finish_reason") == "length") {
                throw AiException("The message was too long to process in one go. Send it a few days at a time.")
            }
            return choice.optJSONObject("message")?.optString("content").orEmpty()
        } catch (e: AiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AiException("The AI took too long to reply. Try again, or send fewer days at a time.")
        } catch (e: IOException) {
            throw AiException("No connection to the AI service. Check your internet and try again.")
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(code: Int, raw: String): String {
        val detail = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        return when (code) {
            401 -> "The DeepSeek API key was rejected. Check secrets.properties or Settings."
            402 -> "Your DeepSeek account has run out of balance. Top up at platform.deepseek.com."
            422, 400 -> "DeepSeek rejected the request${if (detail.isNotBlank()) ": $detail" else "."} Check the model name in Settings."
            429 -> "Too many requests right now. Wait a moment and try again."
            in 500..599 -> "DeepSeek is busy or unavailable ($code). Try again shortly."
            else -> "AI service error ($code)${if (detail.isNotBlank()) ": $detail" else ""}"
        }
    }

    private fun requestBody(
        message: String,
        history: List<AiTurn>,
        catalog: List<ProductEntity>,
        settings: AiSettings,
    ): JSONObject {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt(settings.customInstructions)))
        history.takeLast(MAX_HISTORY_TURNS).forEach { turn ->
            messages.put(JSONObject().put("role", "user").put("content", turn.userText))
            messages.put(JSONObject().put("role", "assistant").put("content", turn.assistantText))
        }
        val today = SimpleDateFormat("EEEE yyyy-MM-dd", Locale.UK).format(Date())
        messages.put(
            JSONObject().put("role", "user").put(
                "content",
                buildString {
                    append("Today is ").append(today).append(".\n\n")
                    append("Current product catalog (JSON):\n")
                    append(catalogJson(catalog))
                    append("\n\nWhatsApp message:\n<<<\n")
                    append(message.trim())
                    append("\n>>>\n\nReply with the JSON object only.")
                },
            ),
        )
        return JSONObject()
            .put("model", settings.model)
            .put("messages", messages)
            .put("response_format", JSONObject().put("type", "json_object"))
            // Reasoning tokens count toward this limit, so leave generous headroom.
            .put("max_tokens", 64_000)
            .put("stream", false)
    }

    private fun catalogJson(catalog: List<ProductEntity>): String = JSONArray().apply {
        catalog.forEach { p ->
            put(
                JSONObject()
                    .put("id", p.id)
                    .put("category", p.category)
                    .put("size", p.size)
                    .put("variant", p.name)
                    .put("unit", p.unit)
                    .put("pcs_per_bag", p.pcsPerBag)
                    .put("current_quantity", p.quantity),
            )
        }
    }.toString()

    private fun parse(text: String, catalog: List<ProductEntity>): AiProposal {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw AiException("Couldn't read the AI response. Try again.")
        }
        val ids = catalog.map { it.id }.toSet()

        val newProducts = json.optJSONArray("new_products").objects().mapNotNull { n ->
            val key = n.optString("key").trim()
            val category = n.optString("category").uppercase().trim()
            if (key.isEmpty() || category.isEmpty()) return@mapNotNull null
            ProposedProduct(
                key = key,
                category = category,
                name = n.optString("name").trim().ifBlank { "Standard" },
                size = n.optString("size").trim(),
                unit = if (n.optString("unit") == StockUnit.BAGS) StockUnit.BAGS else StockUnit.PCS,
                pcsPerBag = n.optInt("pcs_per_bag", 0).coerceAtLeast(0),
                sourceText = n.optString("source_text"),
            )
        }.distinctBy { it.key }
        val newKeys = newProducts.map { it.key }.toSet()

        val entries = json.optJSONArray("entries").objects().mapNotNull { e ->
            val id = e.optLong("product_id", 0).takeIf { it in ids }
            val newKey = e.optString("new_product_key").trim().takeIf { it in newKeys }
            if (id == null && newKey == null) return@mapNotNull null
            val quantity = e.optLong("quantity", 0).coerceAtLeast(0)
            val activity = when (e.optString("activity")) {
                "production" -> Activity.PRODUCTION
                "sale" -> Activity.SALE
                "received" -> Activity.RECEIVED
                "return" -> Activity.RETURN
                "count" -> Activity.COUNT
                else -> Activity.ADJUSTMENT
            }
            ProposedEntry(
                productId = id,
                newProductKey = if (id == null) newKey else null,
                date = e.optString("date").trim().takeIf { DATE_REGEX.matches(it) },
                activity = activity,
                shift = when (e.optString("shift")) {
                    "day" -> Shift.DAY
                    "night" -> Shift.NIGHT
                    else -> Shift.NONE
                },
                // Derive direction from the activity so a stray "mode" can't flip a sale into stock-in.
                mode = when (activity) {
                    Activity.PRODUCTION, Activity.RECEIVED, Activity.RETURN -> ChangeMode.ADD
                    Activity.SALE -> ChangeMode.REMOVE
                    Activity.COUNT -> ChangeMode.SET
                    Activity.ADJUSTMENT -> if (e.optString("mode") == "remove") ChangeMode.REMOVE else ChangeMode.ADD
                },
                quantity = quantity,
                sourceText = e.optString("source_text"),
            )
        }.filter { it.quantity > 0 || it.mode == ChangeMode.SET }

        val questions = json.optJSONArray("questions")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        }.orEmpty()

        return AiProposal(
            summary = json.optString("summary"),
            entries = entries,
            newProducts = newProducts.filter { p -> entries.any { it.newProductKey == p.key } },
            questions = questions,
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun systemPrompt(customInstructions: String): String = buildString {
        append(BASE_PROMPT)
        if (customInstructions.isNotBlank()) {
            append("\n\n<owner_instructions>\n")
            append(customInstructions.trim())
            append("\n</owner_instructions>")
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val MAX_HISTORY_TURNS = 6
        val DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")

        val BASE_PROMPT = """
            You keep the stock records for TrendyPackaging Ghana, a plastic packaging manufacturer. The factory supervisor posts updates in a WhatsApp group; the owner pastes them here, often several days at once. Turn every stock movement in the message into one entry, matched to the product catalog supplied with the message. The owner reviews your entries before anything is saved, so when something is ambiguous, ask rather than guess.

            How the messages are laid out:
            - Lines like "[16/09/2026, 10:37:57] ~Winnard Asare:" are WhatsApp headers showing when the message was sent. They are not stock and not the activity date.
            - A day heading such as "Monday 14/09/26" or "Tuesday 22/09/26" sets the date for the lines below it until the next heading. Dates are DD/MM/YY. If a year is plainly a typo (e.g. "Friday 18/09/16" in a run of 2026 dates), use the year that fits the surrounding dates and the weekday.
            - "Day Productions" and "Night Productions" are shift sections. Every line under them is stock produced by the factory: activity "production", with shift "day" or "night".
            - "Sales", "Sales 16/09/26", "Sales today, 19/09/26", "Sales on Tuesday 22/09/26" start a sales section: activity "sale". Use the date in the heading; "today" means the date written next to it, otherwise the date of the WhatsApp header. A sales section without its own date uses the most recent day heading.
            - Each item is a product name followed by "= quantity", sometimes on the next line: "Mikesh Green" then "= 8 bags (1,600 pcs)".
            - Other wording: "received", "brought in", "supplied to us" → "received"; "returned by customer" → "return"; "count", "stock taking", "balance", or a full dated list of stock on hand → "count"; anything else that changes stock → "adjustment".

            Quantities:
            - Give quantity in the product's catalog unit. For "pcs" products use the pieces figure, which is the one in brackets: "= 3 bags (240pcs)" means 240. The bracketed figure is the total, not extra.
            - If only bags are given ("= 10 (2,000pcs)" counts as pieces given; "= 2 bags" alone does not), multiply by the catalog pcs_per_bag. If pcs_per_bag is 0, don't guess: skip the line and ask in questions.
            - For "bags" products (raw materials) use the number of bags.
            - Ignore thousands separators, stray spaces and brackets. Quantities are whole numbers.

            Matching products:
            - Match on category, size and colour/variant. Products with no variants use the variant "Standard".
            - Supervisors use short names; the owner instructions list the known ones. Use them.
            - If a name matches more than one catalog product and nothing in the line settles it (for example a size that exists in both Clear and White), don't pick one: skip the line and ask which one in questions.
            - If an item is clearly a product that isn't in the catalog yet (a new colour or size of a known range, or a new range), add it once to new_products with a short key like "new1", and point every entry for it at that key via new_product_key. Put the category in capitals, matching existing catalog categories where it belongs to one (a new Spray Bottle colour goes in "SPRAY BOTTLES"). Work out pcs_per_bag from lines that give both bags and pieces; use 0 if unknown.

            Entries:
            - One entry per line item. Don't merge lines, even for the same product on the same day, and don't drop repeats: day and night shifts, or two sales of the same product, are separate movements.
            - Keep entries in the order they appear in the message.
            - date: "YYYY-MM-DD". Use "" only if the message has no date at all.
            - mode: "add" for production, received and return; "remove" for sale; "set" for count; "add" or "remove" for adjustment.
            - source_text: the original item text on one line, e.g. "Mikesh Green = 8 bags (1,600 pcs)".
            - product_id: the catalog id, or 0 when new_product_key is used.

            summary: one or two plain sentences, e.g. "Production and sales for 14–23 Sep: 42 entries across 12 products; 1 line needs checking."
            questions: short questions about lines you skipped or weren't sure of; [] if none.

            Respond with a single json object in exactly this shape (example values):
            {
              "summary": "Production for Monday 14 Sep: 5 entries.",
              "entries": [
                {"product_id": 26, "new_product_key": "", "date": "2026-09-14", "activity": "production", "shift": "day", "mode": "add", "quantity": 1600, "source_text": "Mikesh Green = 8 bags (1,600 pcs)"},
                {"product_id": 0, "new_product_key": "new1", "date": "2026-09-14", "activity": "production", "shift": "night", "mode": "add", "quantity": 2000, "source_text": "Mikesh Brown = 10 bags (2,000 pcs)"},
                {"product_id": 30, "new_product_key": "", "date": "2026-09-16", "activity": "sale", "shift": "", "mode": "remove", "quantity": 400, "source_text": "Red cap = 400pcs"}
              ],
              "new_products": [
                {"key": "new1", "category": "MIKESH", "name": "Brown", "size": "", "unit": "pcs", "pcs_per_bag": 200, "source_text": "Mikesh Brown = 10 bags (2,000 pcs)"}
              ],
              "questions": []
            }
            activity is one of "production", "sale", "received", "return", "count", "adjustment". shift is "day", "night" or "". unit is "pcs" or "bags". Use [] for empty lists.
        """.trimIndent()
    }
}
