package com.example.tpglstock.ui.addstock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tpglstock.ai.Activity
import com.example.tpglstock.ai.AiException
import com.example.tpglstock.ai.AiTurn
import com.example.tpglstock.ai.ProposedEntry
import com.example.tpglstock.ai.ProposedProduct
import com.example.tpglstock.ai.Shift
import com.example.tpglstock.ai.StockAiService
import com.example.tpglstock.data.AiSettings
import com.example.tpglstock.data.ChangeMode
import com.example.tpglstock.data.SettingsStore
import com.example.tpglstock.data.StockChange
import com.example.tpglstock.data.StockRepository
import com.example.tpglstock.data.label
import com.example.tpglstock.data.local.MovementSource
import com.example.tpglstock.data.local.ProductEntity
import com.example.tpglstock.data.toTitleCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class DuplicateKind { IN_HISTORY, IN_MESSAGE }

data class ProposalLine(
    val index: Int,
    val entry: ProposedEntry,
    /** Existing product, or null when the line is for [newProduct]. */
    val product: ProductEntity?,
    val newProduct: ProposedProduct?,
    /** Start of the day the movement happened, or null when undated. */
    val dayStart: Long?,
    val timestamp: Long?,
    val selected: Boolean,
    val duplicate: DuplicateKind? = null,
    /** Running balance for this product before and after this line, applying selected lines in date order. */
    val before: Long = 0,
    val after: Long = 0,
) {
    val productKey: String get() = product?.id?.toString() ?: "new:${newProduct?.key}"
    val title: String get() = product?.label ?: newProduct?.let { n ->
        listOf(n.category.toTitleCase(), n.size, n.name.takeUnless { it == "Standard" } ?: "").filter { it.isNotBlank() }.joinToString(" · ")
    }.orEmpty()
    val unit: String get() = product?.unit ?: newProduct?.unit.orEmpty()
    val exceedsStock: Boolean get() = entry.mode == ChangeMode.REMOVE && entry.quantity > before
}

enum class ProposalStatus { PENDING, APPLYING, APPLIED, DISCARDED }

sealed interface ChatItem {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatItem
    data class Thinking(override val id: Long) : ChatItem
    data class Error(override val id: Long, val message: String, val retryText: String) : ChatItem
    data class Proposal(
        override val id: Long,
        val userText: String,
        val summary: String,
        val lines: List<ProposalLine>,
        val newProducts: List<ProposedProduct>,
        val questions: List<String>,
        val status: ProposalStatus = ProposalStatus.PENDING,
        val appliedCount: Int = 0,
        /** Why the last apply failed; the proposal is back to pending. */
        val applyError: String? = null,
    ) : ChatItem {
        val selectedCount get() = lines.count { it.selected }
        val duplicateCount get() = lines.count { it.duplicate != null }
    }
}

data class AiState(
    val items: List<ChatItem> = emptyList(),
    val input: String = "",
    val busy: Boolean = false,
)

class AiAssistantViewModel(
    private val repo: StockRepository,
    private val ai: StockAiService,
    settingsStore: SettingsStore,
) : ViewModel() {

    val settings: StateFlow<AiSettings> = settingsStore.ai

    private val ids = AtomicLong(0)
    private val _state = MutableStateFlow(AiState())
    val state: StateFlow<AiState> = _state.asStateFlow()

    fun setInput(text: String) = _state.update { it.copy(input = text) }

    fun send(text: String = _state.value.input) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.busy) return
        val thinkingId = ids.incrementAndGet()
        _state.update {
            it.copy(
                items = it.items.filterNot { item -> item is ChatItem.Error } +
                    ChatItem.User(ids.incrementAndGet(), message) + ChatItem.Thinking(thinkingId),
                input = "",
                busy = true,
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                val catalog = repo.allProducts()
                val proposal = ai.interpret(message, history(), catalog, settings.value)
                val byId = catalog.associateBy { it.id }
                val newByKey = proposal.newProducts.associateBy { it.key }
                val lines = proposal.entries.mapIndexed { i, e ->
                    val dayStart = e.date?.let(::parseDay)
                    ProposalLine(
                        index = i,
                        entry = e,
                        product = e.productId?.let(byId::get),
                        newProduct = e.newProductKey?.let(newByKey::get),
                        dayStart = dayStart,
                        timestamp = dayStart?.let { eventTime(it, e) },
                        selected = true,
                    )
                }.filter { it.product != null || it.newProduct != null }
                ChatItem.Proposal(
                    id = ids.incrementAndGet(),
                    userText = message,
                    summary = proposal.summary,
                    lines = recompute(markDuplicates(lines)),
                    newProducts = proposal.newProducts,
                    questions = proposal.questions,
                )
            }
            _state.update { s ->
                val item: ChatItem = result.getOrElse { e ->
                    ChatItem.Error(
                        ids.incrementAndGet(),
                        (e as? AiException)?.message ?: "Something went wrong: ${e.message}",
                        message,
                    )
                }
                s.copy(items = s.items.map { if (it.id == thinkingId) item else it }, busy = false)
            }
        }
    }

    fun retry(error: ChatItem.Error) {
        _state.update { s ->
            // Drop the failed exchange so the retry doesn't duplicate the user bubble.
            val idx = s.items.indexOf(error)
            s.copy(items = s.items.filterIndexed { i, _ -> i != idx && i != idx - 1 })
        }
        send(error.retryText)
    }

    fun toggleLine(proposalId: Long, index: Int) = updateProposal(proposalId) { p ->
        p.copy(lines = recompute(p.lines.map { if (it.index == index) it.copy(selected = !it.selected) else it }))
    }

    fun setAll(proposalId: Long, selected: Boolean) = updateProposal(proposalId) { p ->
        p.copy(lines = recompute(p.lines.map { it.copy(selected = selected) }))
    }

    fun discard(proposalId: Long) = updateProposal(proposalId) { it.copy(status = ProposalStatus.DISCARDED) }

    fun apply(proposalId: Long) {
        val proposal = _state.value.items.filterIsInstance<ChatItem.Proposal>().find { it.id == proposalId } ?: return
        if (proposal.status != ProposalStatus.PENDING) return
        updateProposal(proposalId) { it.copy(status = ProposalStatus.APPLYING, applyError = null) }
        viewModelScope.launch {
            val chosen = proposal.lines.filter { it.selected }.sortedWith(chronological)
            val newProducts = proposal.newProducts.filter { n -> chosen.any { it.newProduct?.key == n.key } }
            val count = try { repo.applyWithNewProducts(
                newProducts = newProducts.map { n ->
                    ProductEntity(
                        category = n.category,
                        name = n.name,
                        size = n.size,
                        unit = n.unit,
                        pcsPerBag = n.pcsPerBag,
                        reorderLevel = if (n.pcsPerBag > 0) n.pcsPerBag * 2L else 0,
                    )
                },
                changes = { newIds ->
                    val idByKey = newProducts.map { it.key }.zip(newIds).toMap()
                    chosen.mapNotNull { line ->
                        val id = line.product?.id ?: idByKey[line.newProduct?.key] ?: return@mapNotNull null
                        StockChange(id, line.entry.mode, line.entry.quantity, activityNote(line.entry), line.timestamp)
                    }
                },
                source = MovementSource.AI,
                batchNote = "WhatsApp",
            ) } catch (e: java.io.IOException) {
                updateProposal(proposalId) {
                    it.copy(status = ProposalStatus.PENDING, applyError = "Not saved — ${e.message ?: "can't reach the server"}")
                }
                return@launch
            }
            updateProposal(proposalId) { it.copy(status = ProposalStatus.APPLIED, appliedCount = count) }
        }
    }

    fun clear() {
        if (_state.value.busy) return
        _state.value = AiState()
    }

    /** Flags lines that look already recorded, or repeated within the pasted text, and deselects them. */
    private suspend fun markDuplicates(lines: List<ProposalLine>): List<ProposalLine> {
        val seen = mutableSetOf<String>()
        return lines.map { line ->
            val e = line.entry
            val batchKey = listOf(line.productKey, e.date, e.activity, e.shift, e.quantity).joinToString("|")
            // Two identical sales on one day can be real; repeated production/count lines usually mean a double paste.
            val inMessage = e.activity != Activity.SALE && e.date != null && !seen.add(batchKey)
            val inHistory = !inMessage && line.product != null && line.dayStart != null && e.mode != ChangeMode.SET &&
                repo.hasSimilarMovement(line.product.id, e.mode, e.quantity, line.dayStart)
            when {
                inMessage -> line.copy(duplicate = DuplicateKind.IN_MESSAGE, selected = false)
                inHistory -> line.copy(duplicate = DuplicateKind.IN_HISTORY, selected = false)
                else -> line
            }
        }
    }

    /** Recomputes running balances per product, applying selected lines in date order. */
    private fun recompute(lines: List<ProposalLine>): List<ProposalLine> {
        val balance = mutableMapOf<String, Long>()
        val updated = lines.sortedWith(chronological).associate { line ->
            val before = balance.getOrPut(line.productKey) { line.product?.quantity ?: 0L }
            val after = when (line.entry.mode) {
                ChangeMode.ADD -> before + line.entry.quantity
                ChangeMode.REMOVE -> (before - line.entry.quantity).coerceAtLeast(0)
                ChangeMode.SET -> line.entry.quantity
            }
            if (line.selected) balance[line.productKey] = after
            line.index to line.copy(before = before, after = after)
        }
        return lines.map { updated.getValue(it.index) }
    }

    private val chronological = compareBy<ProposalLine>({ it.timestamp ?: Long.MAX_VALUE }, { it.index })

    /** Earlier exchanges so the model can resolve follow-ups. */
    private fun history(): List<AiTurn> = _state.value.items.filterIsInstance<ChatItem.Proposal>().map { p ->
        AiTurn(
            userText = p.userText,
            assistantText = buildString {
                append(p.summary)
                val outcome = when (p.status) {
                    ProposalStatus.APPLIED -> "The owner saved the selected entries"
                    ProposalStatus.DISCARDED -> "The owner discarded these entries"
                    else -> "Not yet saved"
                }
                append("\n").append(outcome).append(":")
                p.lines.forEach { l ->
                    append("\n- ").append(l.entry.date ?: "undated").append(" ")
                    append(l.entry.activity.name.lowercase()).append(" ")
                    append(l.title).append(": ").append(l.entry.mode.name.lowercase()).append(" ").append(l.entry.quantity)
                    if (!l.selected) append(" (not selected)")
                }
            },
        )
    }

    private fun updateProposal(id: Long, transform: (ChatItem.Proposal) -> ChatItem.Proposal) = _state.update { s ->
        s.copy(items = s.items.map { if (it is ChatItem.Proposal && it.id == id) transform(it) else it })
    }

    private companion object {
        fun parseDay(date: String): Long? = runCatching {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(date)!!
            Calendar.getInstance().apply {
                time = parsed
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.getOrNull()

        /** Places the movement within its day so history reads in order: day shift, sales, night shift. */
        fun eventTime(dayStart: Long, e: ProposedEntry): Long {
            val hour = when {
                e.shift == Shift.DAY -> 12
                e.activity == Activity.SALE -> 16
                e.shift == Shift.NIGHT -> 22
                else -> 12
            }
            return (dayStart + hour * 60 * 60 * 1000L).coerceAtMost(System.currentTimeMillis())
        }
    }
}

fun activityNote(e: ProposedEntry): String = when (e.activity) {
    Activity.PRODUCTION -> when (e.shift) {
        Shift.DAY -> "Day production"
        Shift.NIGHT -> "Night production"
        Shift.NONE -> "Production"
    }
    Activity.SALE -> "Sale"
    Activity.RECEIVED -> "Received"
    Activity.RETURN -> "Customer return"
    Activity.COUNT -> "Stock count"
    Activity.ADJUSTMENT -> "Adjustment"
}
