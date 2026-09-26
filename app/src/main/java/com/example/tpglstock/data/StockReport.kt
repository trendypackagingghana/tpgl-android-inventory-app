package com.example.tpglstock.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.tpglstock.data.local.ProductEntity
import java.io.File

/** Writes every product's current stock to an A4 PDF in the share cache and returns the file. */
fun writeStockPdf(context: Context, products: List<ProductEntity>, now: Long = System.currentTimeMillis()): File {
    val pageW = 595
    val pageH = 842
    val margin = 40f
    val rowH = 20f
    // Column x positions: product, size, quantity, bags, status.
    val cols = floatArrayOf(margin, 290f, 360f, 450f, 515f)

    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = INK }
    val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = MUTED }
    val header = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; typeface = Typeface.DEFAULT_BOLD; color = MUTED }
    val group = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD; color = INK }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = INK }
    val line = Paint().apply { color = LINE; strokeWidth = 0.5f }

    val doc = PdfDocument()
    var pageNo = 0
    lateinit var page: PdfDocument.Page
    lateinit var canvas: Canvas
    var y = 0f

    fun columnHeaders() {
        listOf("PRODUCT", "SIZE", "QUANTITY", "BAGS", "STATUS").forEachIndexed { i, h -> canvas.drawText(h, cols[i], y, header) }
        y += 6f
        canvas.drawLine(margin, y, pageW - margin, y, line)
        y += 14f
    }

    fun newPage() {
        if (pageNo > 0) doc.finishPage(page)
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        canvas = page.canvas
        y = margin + 14f
        if (pageNo == 1) {
            canvas.drawText("TrendyPackaging Ghana · Current stock", margin, y, title)
            y += 16f
            canvas.drawText("As of ${formatDate(now)} ${formatTime(now)} · ${products.size} products", margin, y, muted)
            y += 24f
        }
        columnHeaders()
    }

    fun ensureSpace(needed: Float) { if (y + needed > pageH - margin) newPage() }

    fun fit(text: String, width: Float, paint: Paint): String {
        if (paint.measureText(text) <= width) return text
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > width) end--
        return text.substring(0, end) + "…"
    }

    newPage()
    products.groupBy { it.category }.toSortedMap().forEach { (category, items) ->
        ensureSpace(rowH * 2)
        y += 4f
        canvas.drawText(category.toTitleCase(), margin, y, group)
        y += rowH
        items.forEach { p ->
            ensureSpace(rowH)
            val status = when (p.status) {
                StockStatus.OK -> "OK"
                StockStatus.LOW -> "Low"
                StockStatus.OUT -> "Out"
            }
            canvas.drawText(fit(p.title, cols[1] - cols[0] - 8f, body), cols[0], y, body)
            canvas.drawText(fit(p.size, cols[2] - cols[1] - 8f, body), cols[1], y, body)
            canvas.drawText(p.quantityText(), cols[2], y, body)
            canvas.drawText(p.bagsText()?.takeIf { p.quantity > 0 } ?: "", cols[3], y, body)
            canvas.drawText(status, cols[4], y, body.takeIf { p.status == StockStatus.OK } ?: Paint(body).apply { color = ALERT })
            canvas.drawLine(margin, y + 6f, pageW - margin, y + 6f, line)
            y += rowH
        }
    }
    doc.finishPage(page)

    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "TPGL-stock-${formatDate(now, "yyyy-MM-dd")}.pdf")
    file.outputStream().use { doc.writeTo(it) }
    doc.close()
    return file
}

private val INK = Color.rgb(0x1B, 0x17, 0x12)
private val MUTED = Color.rgb(0x7A, 0x71, 0x66)
private val LINE = Color.rgb(0xE6, 0xDF, 0xD4)
private val ALERT = Color.rgb(0xC2, 0x41, 0x0C)
