package com.example.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.ClinicStatement
import com.example.data.model.LabSettings
import com.example.data.util.MoneyUtils
import com.example.data.util.ToothFormat
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Professional multi-page A4 PDF generator for monthly statements and bills.
 *
 * Layout: navy header band with lab identity + document number/date, bill-to clinic block,
 * billing period strip, colour-headed 9-column work table (S.No | Date | Patient Name |
 * Work Type | Shade | Tooth Numbers | Units | Rate per Unit | Total Price) with zebra rows
 * and two-line wrapping for long names, totals row, payment summary with highlighted closing
 * balance, and footers with page numbers.
 *
 * Per the clinic-account payment model there is NO case-wise payment column - payments
 * appear only in the overall payment summary. "Rs." is used because PDF base-14 fonts
 * (Helvetica) do not include the rupee glyph U+20B9.
 */
object PdfExporter {

    enum class Mode { STATEMENT, BILL }

    // A4 @ 72dpi
    private const val PAGE_W = 595f
    private const val PAGE_H = 842f
    private const val MARGIN = 36f
    private const val TABLE_W = PAGE_W - 2 * MARGIN // 523f

    // Palette
    private val navy = Color.rgb(15, 23, 42)
    private val headerBlue = Color.rgb(2, 132, 199)
    private val lightBlue = Color.rgb(224, 242, 254)
    private val zebra = Color.rgb(241, 245, 249)
    private val borderGray = Color.rgb(203, 213, 225)
    private val textPrimary = Color.rgb(15, 23, 42)
    private val textSecondary = Color.rgb(100, 116, 139)
    private val redLight = Color.rgb(254, 226, 226)
    private val redDark = Color.rgb(220, 38, 38)

    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private data class Column(
        val title: String,
        val width: Float,
        val align: Align = Align.LEFT,
        val wrap: Boolean = false
    )

    private enum class Align { LEFT, RIGHT }

    // 9 columns; widths sum to exactly 523 (TABLE_W) so headings sit directly above data
    private val columns = listOf(
        Column("S.No", 24f),
        Column("Date", 56f),
        Column("Patient Name", 82f, wrap = true),
        Column("Work Type", 112f, wrap = true),
        Column("Shade", 30f),
        Column("Tooth Nos", 88f, wrap = true),
        Column("Units", 28f, align = Align.RIGHT),
        Column("Rate/Unit", 50f, align = Align.RIGHT),
        Column("Total Price", 53f, align = Align.RIGHT)
    )

    private const val ROW_H = 22f
    private const val ROW_H_WRAPPED = 30f
    private const val CELL_PAD = 4f
    private const val BODY_TEXT = 7.5f
    private const val HEADER_TEXT = 7.5f

    private val rowsPerPage = 22

    fun buildClinicDocument(
        statement: ClinicStatement,
        labSettings: LabSettings,
        mode: Mode
    ): ByteArray {
        val doc = PdfDocument()
        try {
            val pages = if (statement.orders.isEmpty()) 1 else (statement.orders.size + rowsPerPage - 1) / rowsPerPage

            var pageNum = 0
            while (pageNum < pages) {
                pageNum++
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNum).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas

                drawHeader(canvas, statement, labSettings, mode)
                var y: Float
                y = if (pageNum == 1) {
                    drawBillToAndPeriod(canvas, statement)
                } else {
                    96f
                }
                y = drawTableHeader(canvas, y)

                val from = (pageNum - 1) * rowsPerPage
                val to = minOf(from + rowsPerPage, statement.orders.size)
                for (i in from until to) {
                    y = drawOrderRow(canvas, y, statement.orders[i], i)
                }

                if (pageNum == pages) {
                    drawTotalsAndSummary(canvas, y, statement)
                }
                drawFooter(canvas, pageNum, pages, labSettings)

                doc.finishPage(page)
            }
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            return out.toByteArray()
        } finally {
            doc.close()
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun paint(color: Int, size: Float, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.DEFAULT
        }

    /** Greedy word wrap into at most [maxLines] lines that fit [maxWidth]. */
    private fun wrapText(text: String, maxWidth: Float, p: Paint, maxLines: Int): List<String> {
        if (p.measureText(text) <= maxWidth) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (p.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    current = StringBuilder(word)
                } else {
                    // single word longer than the column: hard-break it
                    var w = word
                    while (p.measureText(w) > maxWidth && w.length > 1) {
                        var cut = w.length - 1
                        while (cut > 1 && p.measureText(w.substring(0, cut)) > maxWidth) cut--
                        lines.add(w.substring(0, cut))
                        w = w.substring(cut)
                    }
                    current = StringBuilder(w)
                }
                if (lines.size >= maxLines) break
            }
        }
        if (current.isNotEmpty() && lines.size < maxLines) lines.add(current.toString())
        // ellipsize final line if content was dropped
        if (lines.size == maxLines) {
            val joined = lines.joinToString(" ")
            if (joined.length < text.length) {
                var last = lines.removeAt(lines.size - 1)
                while (last.isNotEmpty() && p.measureText("${last}...") > maxWidth) {
                    last = last.dropLast(1)
                }
                lines.add("$last...")
            }
        }
        return lines
    }

    /** Draws a cell; returns the height used (single or two lines). */
    private fun drawCell(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        text: String,
        col: Column,
        p: Paint
    ): Float {
        val inner = width - 2 * CELL_PAD
        val lines = if (col.wrap) wrapText(text, inner, p, 2) else listOf(ellipsize(text, inner, p))
        val lineH = 9f
        val startY = y + if (lines.size > 1) 11f else 15f
        lines.forEachIndexed { i, line ->
            val lx = when (col.align) {
                Align.LEFT -> x + CELL_PAD
                Align.RIGHT -> x + width - CELL_PAD - p.measureText(line)
            }
            canvas.drawText(line, lx, startY + i * lineH, p)
        }
        return if (lines.size > 1) ROW_H_WRAPPED else ROW_H
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t...") > maxWidth) t = t.dropLast(1)
        return "$t..."
    }

    private fun columnX(index: Int): Float {
        var x = MARGIN
        for (i in 0 until index) x += columns[i].width
        return x
    }

    // ------------------------------------------------------------------ header

    private fun drawHeader(canvas: Canvas, st: ClinicStatement, lab: LabSettings, mode: Mode) {
        canvas.drawRect(0f, 0f, PAGE_W, 74f, Paint().apply { color = navy })
        canvas.drawRect(0f, 74f, PAGE_W, 78f, Paint().apply { color = headerBlue })

        canvas.drawText(lab.labName, MARGIN, 30f, paint(Color.WHITE, 17f, bold = true))
        val contact = listOfNotNull(
            lab.address.takeIf { it.isNotBlank() },
            lab.city.takeIf { it.isNotBlank() },
            lab.phone.takeIf { it.isNotBlank() }
        ).joinToString(" | ")
        canvas.drawText(contact, MARGIN, 46f, paint(Color.rgb(148, 163, 184), 8f))
        canvas.drawText(
            listOfNotNull(lab.email.takeIf { it.isNotBlank() }, "Generated: ${dateFmt.format(Date())}").joinToString(" | "),
            MARGIN, 58f, paint(Color.rgb(148, 163, 184), 8f)
        )

        val title = when (mode) {
            Mode.BILL -> "MONTHLY BILL / INVOICE"
            Mode.STATEMENT -> "CLINIC STATEMENT"
        }
        val tPaint = paint(Color.WHITE, 14f, bold = true)
        canvas.drawText(title, PAGE_W - MARGIN - tPaint.measureText(title), 34f, tPaint)
        val docNumber = if (mode == Mode.BILL) st.statementNumber.replace("ST-", "BILL-") else st.statementNumber
        val nPaint = paint(Color.rgb(56, 189, 248), 9f, bold = true)
        canvas.drawText(docNumber, PAGE_W - MARGIN - nPaint.measureText(docNumber), 50f, nPaint)
        val dPaint = paint(Color.rgb(148, 163, 184), 8f)
        val dateText = "Date: ${dateFmt.format(Date(st.statementDate))}"
        canvas.drawText(dateText, PAGE_W - MARGIN - dPaint.measureText(dateText), 62f, dPaint)
    }

    private fun drawBillToAndPeriod(canvas: Canvas, st: ClinicStatement): Float {
        var y = 104f
        canvas.drawText("BILL TO", MARGIN, y, paint(textSecondary, 8f, bold = true))
        canvas.drawText(st.clinic.name, MARGIN, y + 15f, paint(textPrimary, 12f, bold = true))
        var line = y + 28f
        canvas.drawText("Dr. ${st.clinic.dentistName}", MARGIN, line, paint(textSecondary, 9f))
        line += 11f
        val contact = listOf(st.clinic.phone, st.clinic.city).filter { it.isNotBlank() }.joinToString(" | ")
        if (contact.isNotBlank()) {
            canvas.drawText(contact, MARGIN, line, paint(textSecondary, 9f))
            line += 11f
        }

        // Period strip on the right
        val periodText = "Billing Period: ${st.periodLabel}"
        val pPaint = paint(textPrimary, 10f, bold = true)
        val box = RectF(PAGE_W - MARGIN - pPaint.measureText(periodText) - 16f, y - 14f, PAGE_W - MARGIN, y + 6f)
        canvas.drawRoundRect(box, 4f, 4f, Paint().apply { color = lightBlue })
        canvas.drawText(periodText, box.left + 8f, y, pPaint)
        val rangeText = "${dateFmt.format(Date(st.periodStart))} to ${dateFmt.format(Date(st.periodEndExclusive - 1))}"
        val rPaint = paint(textSecondary, 8f)
        canvas.drawText(rangeText, PAGE_W - MARGIN - rPaint.measureText(rangeText) - 8f, y + 16f, rPaint)

        return maxOf(line, y + 26f) + 14f
    }

    // ------------------------------------------------------------------ table

    private fun drawTableHeader(canvas: Canvas, y: Float): Float {
        val headerPaint = Paint().apply { color = headerBlue }
        val rowH = 26f
        canvas.drawRect(MARGIN, y, MARGIN + TABLE_W, y + rowH, headerPaint)
        val textPaint = paint(Color.WHITE, HEADER_TEXT, bold = true)
        columns.forEachIndexed { i, col ->
            val x = columnX(i)
            val w = textPaint.measureText(col.title)
            val tx = when (col.align) {
                Align.LEFT -> x + CELL_PAD
                Align.RIGHT -> x + col.width - CELL_PAD - w
            }
            canvas.drawText(col.title, tx, y + 16.5f, textPaint)
            // light column separators inside the heading
            if (i > 0) {
                canvas.drawLine(x, y + 4f, x, y + rowH - 4f, Paint().apply {
                    color = Color.argb(60, 255, 255, 255); strokeWidth = 0.5f
                })
            }
        }
        return y + rowH
    }

    private fun drawOrderRow(canvas: Canvas, y: Float, order: com.example.data.model.WorkOrder, index: Int): Float {
        val values = listOf(
            (index + 1).toString(),
            dateFmt.format(Date(order.entryDate)),
            order.patientName,
            order.workTypeName,
            order.shade,
            ToothFormat.displayOrDash(order.selectedTeeth),
            if (order.pricingModel == "UNIT_BASED") order.units.toString() else "Fixed",
            MoneyUtils.plain(order.rate),
            MoneyUtils.plain(order.totalAmount)
        )

        // Pre-measure to know if any wrap column needs two lines (row height must match)
        val probe = paint(textPrimary, BODY_TEXT)
        val needsTwoLines = values.withIndex().any { (i, v) ->
            columns[i].wrap && probe.measureText(v) > columns[i].width - 2 * CELL_PAD
        }
        val rowH = if (needsTwoLines) ROW_H_WRAPPED else ROW_H

        if (index % 2 == 1) {
            canvas.drawRect(MARGIN, y, MARGIN + TABLE_W, y + rowH, Paint().apply { color = zebra })
        }
        val tp = if (order.status == "Cancelled") paint(textSecondary, BODY_TEXT) else paint(textPrimary, BODY_TEXT)
        values.forEachIndexed { i, value ->
            drawCell(canvas, columnX(i), y, columns[i].width, value, columns[i], tp)
        }
        // Row separator + column gridlines (subtle)
        val line = Paint().apply { color = borderGray; strokeWidth = 0.5f }
        canvas.drawLine(MARGIN, y + rowH, MARGIN + TABLE_W, y + rowH, line)
        for (i in 1 until columns.size) {
            val x = columnX(i)
            canvas.drawLine(x, y, x, y + rowH, Paint().apply {
                color = Color.argb(70, 203, 213, 225); strokeWidth = 0.4f
            })
        }
        return y + rowH
    }

    // ------------------------------------------------------------------ totals

    private fun drawTotalsAndSummary(canvas: Canvas, y: Float, st: ClinicStatement): Float {
        var yy = y + 12f

        // Totals row aligned to the SAME columns as the table
        val totalPaint = Paint().apply { color = Color.rgb(226, 232, 240) }
        canvas.drawRect(MARGIN, yy, MARGIN + TABLE_W, yy + 22f, totalPaint)
        val bold = paint(textPrimary, 8.5f, bold = true)
        // "TOTAL" spans S.No+Date+Patient (visually under the first columns)
        canvas.drawText("TOTAL (${st.totalEntries} works)", columnX(0) + CELL_PAD, yy + 15f, bold)
        // Units under Units column (right-aligned)
        val unitsText = st.totalUnits.toString()
        val unitsX = columnX(6) + columns[6].width - CELL_PAD - bold.measureText(unitsText)
        canvas.drawText(unitsText, unitsX, yy + 15f, bold)
        // Revenue under Total Price column (right-aligned)
        val revText = "Rs. ${MoneyUtils.plain(st.totalRevenue)}"
        val revX = columnX(8) + columns[8].width - CELL_PAD - bold.measureText(revText)
        canvas.drawText(revText, revX, yy + 15f, bold)
        yy += 34f

        // Payment summary card (payments appear ONLY here - never per case)
        val cardTop = yy
        val cardH = 108f
        canvas.drawRoundRect(
            RectF(MARGIN, cardTop, MARGIN + TABLE_W, cardTop + cardH), 6f, 6f,
            Paint().apply { color = Color.rgb(248, 250, 252) }
        )
        canvas.drawRoundRect(
            RectF(MARGIN, cardTop, MARGIN + TABLE_W, cardTop + cardH), 6f, 6f,
            Paint().apply { color = borderGray; strokeWidth = 1f; style = Paint.Style.STROKE }
        )

        canvas.drawText("PAYMENT SUMMARY", MARGIN + 12f, cardTop + 18f, paint(textSecondary, 8f, bold = true))

        val labelX = MARGIN + 12f
        val valueX = MARGIN + TABLE_W - 150f
        var ly = cardTop + 38f
        canvas.drawText("Previous Outstanding Balance (brought forward)", labelX, ly, paint(textPrimary, 9f))
        canvas.drawText("Rs. ${MoneyUtils.plain(st.previousBalance)}", valueX, ly, paint(textPrimary, 9f, bold = true))
        ly += 18f
        canvas.drawText("Current Month Revenue (${st.totalEntries} works, ${st.totalUnits} units)", labelX, ly, paint(textPrimary, 9f))
        canvas.drawText("+ Rs. ${MoneyUtils.plain(st.totalRevenue)}", valueX, ly, paint(textPrimary, 9f, bold = true))
        ly += 18f
        canvas.drawText("Payments Received during ${st.periodLabel}", labelX, ly, paint(textPrimary, 9f))
        canvas.drawText("- Rs. ${MoneyUtils.plain(st.paymentsReceived)}", valueX, ly, paint(textPrimary, 9f, bold = true))
        ly += 22f

        val box = RectF(valueX - 46f, ly - 14f, MARGIN + TABLE_W - 12f, ly + 6f)
        canvas.drawRoundRect(box, 4f, 4f, Paint().apply { color = redLight })
        canvas.drawText("CLOSING OUTSTANDING BALANCE DUE", labelX, ly + 2f, paint(redDark, 10f, bold = true))
        canvas.drawText("Rs. ${MoneyUtils.plain(st.remainingBalance)}", valueX, ly + 2f, paint(redDark, 11f, bold = true))

        return cardTop + cardH + 16f
    }

    private fun drawFooter(canvas: Canvas, page: Int, pages: Int, lab: LabSettings) {
        val y = PAGE_H - 26f
        canvas.drawLine(
            MARGIN, y - 10f, MARGIN + TABLE_W, y - 10f,
            Paint().apply { color = borderGray; strokeWidth = 0.5f }
        )
        canvas.drawText("${lab.labName} - computer generated document", MARGIN, y, paint(textSecondary, 7f))
        val pageText = "Page $page of $pages"
        val pp = paint(textSecondary, 7f)
        canvas.drawText(pageText, MARGIN + TABLE_W - pp.measureText(pageText), y, pp)
    }
}
