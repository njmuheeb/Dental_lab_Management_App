package com.example.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.WarrantyCard
import com.example.data.util.ToothFormat
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Patient warranty card PDF: EXACTLY TWO CR80 / ID-1 card-sized pages
 * (85.60 mm x 53.98 mm = 3.375 x 2.125 in = 243 x 153 points at 72 dpi, landscape).
 *
 *  - Page 1 = FRONT — green rounded-card design: brand header, patient/case details
 *    (date, case no., patient name, address, contact, dentist, type of work), a
 *    four-quadrant FDI tooth-number diagram on the right (vertical + horizontal
 *    cross through the center; single digits 1-8 per quadrant, only the teeth that
 *    were actually worked on) and a warranty-period strip at the bottom.
 *  - Page 2 = BACK — matching design with the warranty period, numbered terms &
 *    conditions, care recommendations, contact line and a policy disclaimer.
 *
 * The page size is set explicitly on every PdfDocument.PageInfo, so the exported
 * PDF is a small card-sized document (never A4/Letter) and prints correctly at
 * "Actual Size / 100%". Print front/back on CR80 card stock.
 *
 * Layout is computed by pure functions ([frontLayout] / [backLayout]) that return
 * positioned shapes; [build] only renders that geometry. This makes the layout
 * unit-testable: page count, page dimensions, quadrant placement, in-bounds
 * guarantees, text wrapping and completeness can all be verified without a device.
 *
 * "Rs."-free zone: no currency here. Base-14 fonts are used, so only ASCII glyphs.
 */
object WarrantyCardPdfExporter {

    // CR80 / ID-1 (Aadhaar-style) card: 85.60mm x 53.98mm at 72dpi, landscape.
    const val CARD_WIDTH_PT = 243f    // 3.375 in
    const val CARD_HEIGHT_PT = 153f   // 2.125 in
    const val PAGE_COUNT = 2          // page 1 = front, page 2 = back

    // Generic public branding (user-configurable lab identity is stored on each card)
    const val APP_BRAND = "Dental Lab Management"
    const val TAGLINE = "Precision | Quality | Care"
    const val PRINT_NOTE = "Print at Actual Size (100%) - do not scale to fit the paper."

    /** Disclaimer printed on the card back - never a universal legal guarantee. */
    private const val DISCLAIMER =
        "Warranty terms are subject to the laboratory's actual policy and applicable agreements. " +
            "This card is not a substitute for professional dental advice."

    /** Neutral support line shown when the lab has no contact details configured. */
    private const val NEUTRAL_SUPPORT = "For support, contact your prescribing dental clinic."

    // Safe area / margins
    private const val MARGIN = 8f

    // Palette — professional green and white
    private val greenDark = Color.rgb(20, 83, 45)     // #14532D header / strong text
    private val green = Color.rgb(22, 163, 74)        // #16A34A accents, cross, strip
    private val greenLight = Color.rgb(240, 253, 244) // #F0FDF4 panels
    private val greenBorder = Color.rgb(187, 247, 208)// #BBF7D0 borders
    private val greenTint = Color.rgb(187, 247, 208)  // light text on dark green
    private val textPrimary = Color.rgb(17, 24, 39)   // #111827
    private val textSecondary = Color.rgb(75, 85, 99) // #4B5563
    private val borderGray = Color.rgb(209, 213, 219) // #D1D5DB dividers

    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // ------------------------------------------------------------------ layout model

    /** Filled rectangle (header band, panels, strip); [radius] > 0 = rounded. */
    data class BoxShape(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val color: Int, val radius: Float = 0f
    )

    /** Stroked rounded rectangle (card border, diagram frame). */
    data class OutlineShape(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val color: Int, val strokeWidth: Float, val radius: Float
    )

    /** Stroked or plain line (diagram cross, dividers). */
    data class LineShape(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int, val width: Float)

    /** One positioned text run; [y] is the text BASELINE (Android Canvas convention). */
    data class TextShape(
        val text: String,
        val x: Float,
        val y: Float,
        val size: Float,
        val color: Int,
        val bold: Boolean
    )

    /** Stylized tooth glyph (crown with two cusps and two roots), filled with [color]. */
    data class ToothShape(val cx: Float, val cy: Float, val width: Float, val height: Float, val color: Int)

    /** Complete geometry of one card side. */
    data class CardPage(
        val width: Float,
        val height: Float,
        val boxes: List<BoxShape>,
        val outlines: List<OutlineShape>,
        val lines: List<LineShape>,
        val texts: List<TextShape>,
        val tooths: List<ToothShape> = emptyList()
    )

    /** Both pages of the warranty card PDF: index 0 = front, index 1 = back. */
    fun pageLayouts(card: WarrantyCard): List<CardPage> = listOf(frontLayout(card), backLayout(card))

    // ------------------------------------------------------------------ PDF build

    /** Builds the two-page card-sized PDF (page 1 front, page 2 back). */
    fun build(card: WarrantyCard): ByteArray {
        val doc = PdfDocument()
        try {
            pageLayouts(card).forEachIndexed { index, page ->
                val info = PdfDocument.PageInfo.Builder(
                    Math.round(page.width),
                    Math.round(page.height),
                    index + 1
                ).create()
                val pdfPage = doc.startPage(info)
                render(pdfPage.canvas, page)
                doc.finishPage(pdfPage)
            }
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            return out.toByteArray()
        } finally {
            doc.close()
        }
    }

    private fun render(canvas: Canvas, page: CardPage) {
        canvas.drawRect(0f, 0f, page.width, page.height, Paint().apply { color = Color.WHITE })
        page.boxes.forEach { b ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = b.color }
            if (b.radius > 0f) {
                canvas.drawRoundRect(RectF(b.left, b.top, b.right, b.bottom), b.radius, b.radius, paint)
            } else {
                canvas.drawRect(RectF(b.left, b.top, b.right, b.bottom), paint)
            }
        }
        page.outlines.forEach { o ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = o.color; strokeWidth = o.strokeWidth; style = Paint.Style.STROKE
            }
            canvas.drawRoundRect(RectF(o.left, o.top, o.right, o.bottom), o.radius, o.radius, paint)
        }
        page.lines.forEach { l ->
            canvas.drawLine(l.x1, l.y1, l.x2, l.y2, Paint().apply {
                color = l.color; strokeWidth = l.width; strokeCap = Paint.Cap.SQUARE
            })
        }
        page.tooths.forEach { t ->
            canvas.drawPath(toothPath(t.cx, t.cy, t.width, t.height), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = t.color
            })
        }
        page.texts.forEach { t ->
            canvas.drawText(t.text, t.x, t.y, paint(t.color, t.size, t.bold))
        }
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.DEFAULT
        }

    // ------------------------------------------------------------------ tooth glyph

    /**
     * Geometry of a stylized tooth as one start point plus a list of cubic bezier
     * segments, each `floatArrayOf(x1, y1, x2, y2, x3, y3)`. All control points stay
     * inside the box `[cx-w/2, cx+w/2] x [cy-h/2, cy+h/2]`, so the glyph can never
     * leave its bounds. Shared with the in-app Compose preview so both render the
     * same shape.
     */
    fun toothGeometry(cx: Float, cy: Float, w: Float, h: Float): Pair<FloatArray, List<FloatArray>> {
        val l = cx - w / 2f
        val t = cy - h / 2f
        fun p(nx: Float, ny: Float) = floatArrayOf(l + nx * w, t + ny * h)
        fun seg(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) =
            floatArrayOf(l + a * w, t + b * h, l + c * w, t + d * h, l + e * w, t + f * h)
        val start = p(0.00f, 0.25f)
        val segments = listOf(
            seg(0.00f, 0.05f, 0.08f, 0.00f, 0.20f, 0.04f),   // left cusp
            seg(0.30f, 0.10f, 0.44f, 0.10f, 0.50f, 0.04f),   // dip to center
            seg(0.56f, 0.10f, 0.70f, 0.10f, 0.80f, 0.04f),   // rise to right cusp
            seg(0.92f, 0.00f, 1.00f, 0.05f, 1.00f, 0.25f),   // right side of crown
            seg(1.00f, 0.45f, 0.98f, 0.55f, 0.90f, 0.66f),   // into right root
            seg(0.85f, 0.76f, 0.83f, 1.00f, 0.76f, 0.98f),   // right root tip
            seg(0.70f, 0.98f, 0.69f, 0.80f, 0.55f, 0.62f),   // notch up (right)
            seg(0.51f, 0.56f, 0.49f, 0.56f, 0.45f, 0.62f),   // notch center
            seg(0.31f, 0.80f, 0.30f, 0.98f, 0.24f, 0.98f),   // left root tip
            seg(0.17f, 1.00f, 0.15f, 0.76f, 0.10f, 0.66f),   // left root outer
            seg(0.02f, 0.55f, 0.00f, 0.45f, 0.00f, 0.25f)    // back to start
        )
        return start to segments
    }

    private fun toothPath(cx: Float, cy: Float, w: Float, h: Float): Path {
        val (start, segments) = toothGeometry(cx, cy, w, h)
        val path = Path()
        path.moveTo(start[0], start[1])
        segments.forEach { path.cubicTo(it[0], it[1], it[2], it[3], it[4], it[5]) }
        path.close()
        return path
    }

    // ------------------------------------------------------------------ text helpers

    /**
     * Greedy word wrap into at most [maxLines] lines that each fit [maxWidth].
     * Words longer than [maxWidth] are hard-broken, so NO line can ever exceed
     * the width (nothing gets clipped at the page edge). If the text needs more
     * lines than [maxLines], the last line is ellipsized so no content silently
     * disappears.
     */
    fun wrap(text: String, maxWidth: Float, size: Float, bold: Boolean, maxLines: Int): List<String> {
        if (maxLines <= 0) return emptyList()
        val p = paint(Color.BLACK, size, bold)
        val clean = text.replace('\n', ' ').trim()
        if (clean.isEmpty()) return emptyList()
        if (p.measureText(clean) <= maxWidth) return listOf(clean)

        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in clean.split(" ")) {
            if (word.isEmpty()) continue
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (p.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    current = StringBuilder()
                    if (lines.size >= maxLines) break
                }
                if (p.measureText(word) <= maxWidth) {
                    current = StringBuilder(word)
                } else {
                    // Single word wider than the column: hard-break it into fitting chunks
                    var rest = word
                    while (rest.isNotEmpty()) {
                        var cut = rest.length
                        while (cut > 1 && p.measureText(rest.substring(0, cut)) > maxWidth) cut--
                        if (current.isNotEmpty()) {
                            lines.add(current.toString())
                            current = StringBuilder()
                            if (lines.size >= maxLines) break
                        }
                        current = StringBuilder(rest.substring(0, cut))
                        rest = rest.substring(cut)
                    }
                    if (lines.size >= maxLines) break
                }
            }
        }
        if (current.isNotEmpty() && lines.size < maxLines) lines.add(current.toString())

        // Ellipsize the final line when content was dropped
        if (lines.size == maxLines) {
            val joined = lines.joinToString(" ")
            if (joined.length < clean.length) {
                var last = lines.removeAt(lines.size - 1)
                while (last.isNotEmpty() && p.measureText("$last...") > maxWidth) last = last.dropLast(1)
                lines.add("$last...")
            }
        }
        return lines
    }

    /** Truncates with "..." so the result always fits [maxWidth]. Never returns blank for non-blank input. */
    fun ellipsize(text: String, maxWidth: Float, size: Float, bold: Boolean): String {
        val p = paint(Color.BLACK, size, bold)
        val clean = text.replace('\n', ' ').trim()
        if (clean.isEmpty()) return ""
        if (p.measureText(clean) <= maxWidth) return clean
        var t = clean
        while (t.isNotEmpty() && p.measureText("$t...") > maxWidth) t = t.dropLast(1)
        return "$t..."
    }

    /**
     * Fits a block of source lines into [maxHeight] at the largest font size that
     * fits (shrinking down to [minSize]). Lines are never silently DROPPED within
     * the practical range, so long custom terms still render completely.
     */
    fun fitBlock(
        sourceLines: List<String>,
        maxWidth: Float,
        maxHeight: Float,
        maxSize: Float = 4.3f,
        minSize: Float = 2.6f
    ): Pair<Float, List<String>> {
        if (sourceLines.isEmpty()) return maxSize to emptyList()
        var size = maxSize
        while (size >= minSize) {
            val lineH = size + 1.6f
            val wrapped = sourceLines.flatMap { wrap(it, maxWidth, size, bold = false, maxLines = Int.MAX_VALUE) }
            if (wrapped.size * lineH <= maxHeight) return size to wrapped
            size -= 0.1f
        }
        // Floor: keep the minimum readable size; physically longer texts cannot fit a
        // fixed-size card, but this still renders far more content than dropping lines.
        val lineH = minSize + 1.6f
        val wrapped = sourceLines.flatMap { wrap(it, maxWidth, minSize, bold = false, maxLines = Int.MAX_VALUE) }
        return minSize to wrapped.take((maxHeight / lineH).toInt().coerceAtLeast(1))
    }

    private fun measure(text: String, size: Float, bold: Boolean): Float =
        paint(Color.BLACK, size, bold).measureText(text)

    /**
     * The FDI tooth list driving the quadrant diagram. Newer cards store the raw FDI
     * list directly; cards saved before that column existed fall back to parsing the
     * human-readable quadrant text.
     */
    fun effectiveTeeth(card: WarrantyCard): String =
        card.selectedTeeth.ifBlank { ToothFormat.fdiFromQuadrantText(card.toothNumbers) }

    // ------------------------------------------------------------------ FRONT
    //
    // Vertical plan (values are text baselines, card height = 153):
    //   2-151   rounded green card border (stroke)
    //   4-26    dark-green header band: tooth glyph + lab name + tagline,
    //           "WARRANTY CARD" + card number on the right
    //   37-124  LEFT column (x 10..132): Date + Case No., Patient Name (2 lines),
    //           Address (2 lines), Contact + Dentist, Type of Work (2 lines)
    //   37-117  RIGHT quadrant diagram (x 140..231): section label, rounded panel
    //           with a vertical + horizontal cross through the exact center, four
    //           quadrant cells (UR | UL / LR | LL) holding single digits 1-8
    //   128-149 green warranty-period strip (bottom)
    fun frontLayout(card: WarrantyCard): CardPage {
        val boxes = mutableListOf<BoxShape>()
        val outlines = mutableListOf<OutlineShape>()
        val lines = mutableListOf<LineShape>()
        val texts = mutableListOf<TextShape>()
        val tooths = mutableListOf<ToothShape>()
        val w = CARD_WIDTH_PT
        val h = CARD_HEIGHT_PT

        fun add(text: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean = false) {
            texts += TextShape(text, x, baseline, size, color, bold)
        }

        fun addCentered(text: String, left: Float, right: Float, baseline: Float, size: Float, color: Int, bold: Boolean = false) {
            val tw = measure(text, size, bold)
            add(text, left + (right - left - tw) / 2f, baseline, size, color, bold)
        }

        /** Label + wrapped value field used by the left information column. */
        fun field(label: String, value: String, x: Float, labelY: Float, width: Float, valueSize: Float, maxLines: Int) {
            add(label, x, labelY, 3.8f, textSecondary, bold = true)
            var vy = labelY + 6.5f
            wrap(value.ifBlank { "-" }, width, valueSize, bold = true, maxLines = maxLines).forEach { line ->
                add(line, x, vy, valueSize, textPrimary, bold = true)
                vy += valueSize + 1.6f
            }
        }

        // --- Rounded card border + header band
        outlines += OutlineShape(2f, 2f, w - 2f, h - 2f, green, strokeWidth = 1.6f, radius = 10f)
        boxes += BoxShape(4f, 4f, w - 4f, 26f, greenDark, radius = 7f)
        tooths += ToothShape(13f, 15f, 8f, 9.5f, Color.WHITE)
        val labAvail = (w - 8f - 60f - 22f).coerceAtLeast(60f)
        add(
            ellipsize(card.labName.ifBlank { APP_BRAND }, labAvail, 6.8f, bold = true),
            22f, 13.5f, 6.8f, Color.WHITE, bold = true
        )
        add(TAGLINE, 22f, 20.5f, 3.4f, greenTint)
        val cardTitle = "WARRANTY CARD"
        val ctW = measure(cardTitle, 4.2f, bold = true)
        add(cardTitle, w - 8f - ctW, 12.5f, 4.2f, Color.WHITE, bold = true)
        val numW = measure(card.cardNumber, 3.6f, bold = false)
        add(card.cardNumber, w - 8f - numW, 20f, 3.6f, greenTint)

        // --- LEFT information column
        val workMaterial = listOf(card.workType, card.material).filter { it.isNotBlank() }.joinToString(" - ")
        field("DATE", dateFmt.format(Date(card.deliveryDate)), 10f, 37f, 58f, 5.2f, 1)
        field("CASE NO.", card.workOrderNumber, 74f, 37f, 58f, 5.2f, 1)
        field("PATIENT NAME", card.patientName, 10f, 51f, 122f, 6.2f, 2)
        field("ADDRESS", card.patientAddress, 10f, 74f, 122f, 4.2f, 2)
        field("CONTACT NO.", card.patientPhone, 10f, 94.5f, 58f, 5.2f, 1)
        field("DENTIST NAME", card.consultantDoctor, 74f, 94.5f, 58f, 5.2f, 1)
        field("TYPE OF WORK", workMaterial, 10f, 109.5f, 122f, 5.2f, 2)

        // --- RIGHT four-quadrant tooth-number diagram
        addCentered("TOOTH NUMBER(S)", 138f, 233f, 37f, 4.3f, greenDark, bold = true)
        val dgLeft = 140f
        val dgTop = 41f
        val dgRight = 231f
        val dgBottom = 117f
        val dgCx = (dgLeft + dgRight) / 2f   // 185.5
        val dgCy = (dgTop + dgBottom) / 2f   // 79
        boxes += BoxShape(dgLeft, dgTop, dgRight, dgBottom, greenLight, radius = 6f)
        outlines += OutlineShape(dgLeft, dgTop, dgRight, dgBottom, greenBorder, strokeWidth = 0.8f, radius = 6f)
        lines += LineShape(dgCx, dgTop, dgCx, dgBottom, green, 1.0f)      // vertical cross
        lines += LineShape(dgLeft, dgCy, dgRight, dgCy, green, 1.0f)      // horizontal cross

        val quadrants = ToothFormat.quadrantDigits(effectiveTeeth(card))
        val cellW = (dgRight - dgLeft) / 2f  // 45.5
        val cellH = (dgBottom - dgTop) / 2f  // 38

        /**
         * One quadrant cell: tiny label (UR/UL/LR/LL) at the top, then the selected
         * single digits 1-8 in ascending order, up to 4 per row, centered. Empty
         * quadrants stay blank. Layout (patient front view, standard charting):
         *   top row:    UR (left) | UL (right)
         *   bottom row: LR (left) | LL (right)
         */
        fun quadrantCell(label: String, digits: List<Int>, cellLeft: Float, cellTop: Float) {
            addCentered(label, cellLeft, cellLeft + cellW, cellTop + 7f, 3f, textSecondary, bold = true)
            digits.chunked(4).forEachIndexed { rowIndex, row ->
                val baseline = cellTop + 21f + rowIndex * 11f
                val k = row.size
                row.forEachIndexed { i, digit ->
                    val ds = digit.toString()
                    val dw = measure(ds, 7.5f, bold = true)
                    val x = cellLeft + cellW / 2f + (i - (k - 1) / 2f) * 11f - dw / 2f
                    add(ds, x, baseline, 7.5f, greenDark, bold = true)
                }
            }
        }

        quadrantCell("UR", quadrants.upperRight, dgLeft, dgTop)
        quadrantCell("UL", quadrants.upperLeft, dgCx, dgTop)
        quadrantCell("LR", quadrants.lowerRight, dgLeft, dgCy)
        quadrantCell("LL", quadrants.lowerLeft, dgCx, dgCy)

        // --- BOTTOM warranty-period strip
        boxes += BoxShape(4f, 128f, w - 4f, 149f, green, radius = 7f)
        val yearsWord = if (card.warrantyYears == 1) "YEAR" else "YEARS"
        addCentered("WARRANTY PERIOD: ${card.warrantyYears} $yearsWord", 4f, w - 4f, 139f, 6.2f, Color.WHITE, bold = true)
        addCentered(
            "Valid from ${dateFmt.format(Date(card.deliveryDate))} to ${dateFmt.format(Date(card.warrantyExpiryDate))}",
            4f, w - 4f, 145f, 3.8f, Color.WHITE
        )

        return CardPage(w, h, boxes, outlines, lines, texts, tooths)
    }

    // ------------------------------------------------------------------ BACK
    //
    // Vertical plan (values are text baselines, card height = 153):
    //   2-151   rounded green card border (stroke)
    //   4-26    dark-green header band (same as front)
    //   30-44   warranty period strip (light green, rounded)
    //   53      centered "WARRANTY TERMS & CONDITIONS" heading; body auto-fits 56-98
    //   100.5   divider; 106.5 "CARE RECOMMENDATIONS"; bullets auto-fit 109.5-125.5
    //   127.5   divider; 132.5 contact line; 137.5/141.3 disclaimer footer
    fun backLayout(card: WarrantyCard): CardPage {
        val boxes = mutableListOf<BoxShape>()
        val outlines = mutableListOf<OutlineShape>()
        val lines = mutableListOf<LineShape>()
        val texts = mutableListOf<TextShape>()
        val tooths = mutableListOf<ToothShape>()
        val w = CARD_WIDTH_PT
        val h = CARD_HEIGHT_PT

        fun add(text: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean = false) {
            texts += TextShape(text, x, baseline, size, color, bold)
        }

        fun addCentered(text: String, left: Float, right: Float, baseline: Float, size: Float, color: Int, bold: Boolean = false) {
            val tw = measure(text, size, bold)
            add(text, left + (right - left - tw) / 2f, baseline, size, color, bold)
        }

        // --- Card border + header band (matches the front)
        outlines += OutlineShape(2f, 2f, w - 2f, h - 2f, green, strokeWidth = 1.6f, radius = 10f)
        boxes += BoxShape(4f, 4f, w - 4f, 26f, greenDark, radius = 7f)
        tooths += ToothShape(13f, 15f, 8f, 9.5f, Color.WHITE)
        val labAvail = (w - 8f - 60f - 22f).coerceAtLeast(60f)
        add(
            ellipsize(card.labName.ifBlank { APP_BRAND }, labAvail, 6.8f, bold = true),
            22f, 13.5f, 6.8f, Color.WHITE, bold = true
        )
        add(TAGLINE, 22f, 20.5f, 3.4f, greenTint)
        val numW = measure(card.cardNumber, 4f, bold = false)
        add(card.cardNumber, w - 8f - numW, 15f, 4f, Color.WHITE)

        // --- Warranty period strip
        boxes += BoxShape(10f, 30f, w - 10f, 44f, greenLight, radius = 5f)
        val yearsWord = if (card.warrantyYears == 1) "YEAR" else "YEARS"
        addCentered("WARRANTY PERIOD: ${card.warrantyYears} $yearsWord", 10f, w - 10f, 36.5f, 4.6f, greenDark, bold = true)
        addCentered(
            "Valid from ${dateFmt.format(Date(card.deliveryDate))} to ${dateFmt.format(Date(card.warrantyExpiryDate))}",
            10f, w - 10f, 41.5f, 3.8f, greenDark
        )

        // --- Terms & conditions (numbered, auto-fitted)
        addCentered("WARRANTY TERMS & CONDITIONS", 10f, w - 10f, 53f, 4.5f, greenDark, bold = true)
        val termsLines = card.terms.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val (termsSize, termsWrapped) = fitBlock(termsLines, 223f, 42f)
        var ty = 56f + termsSize
        termsWrapped.forEach { line ->
            add(line, 10f, ty, termsSize, textPrimary)
            ty += termsSize + 1.6f
        }

        // --- Care recommendations (bulleted, auto-fitted)
        lines += LineShape(10f, 100.5f, w - 10f, 100.5f, borderGray, 0.5f)
        addCentered("CARE RECOMMENDATIONS", 10f, w - 10f, 106.5f, 4.5f, greenDark, bold = true)
        val careLines = card.careInstructions.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith("•") || it.startsWith("-")) it else "• $it" }
        val (careSize, careWrapped) = fitBlock(careLines, 223f, 16f, maxSize = 4.0f)
        var cy = 109.5f + careSize
        careWrapped.forEach { line ->
            add(line, 10f, cy, careSize, textPrimary)
            cy += careSize + 1.4f
        }

        // --- Contact / support line (configurable, neutral placeholder otherwise)
        lines += LineShape(10f, 127.5f, w - 10f, 127.5f, borderGray, 0.5f)
        val contact =
            when {
                card.labPhone.isNotBlank() -> "Contact: ${card.labPhone}"
                card.labAddress.isNotBlank() -> ellipsize(card.labAddress, 200f, 3.6f, bold = false)
                else -> NEUTRAL_SUPPORT
            }
        addCentered(contact, 10f, w - 10f, 132.5f, 3.6f, greenDark, bold = true)

        // --- Disclaimer footer
        wrap(DISCLAIMER, 223f, 3.0f, bold = false, maxLines = 2)
            .forEachIndexed { i, line ->
                addCentered(line, 10f, w - 10f, 137.5f + i * 3.8f, 3.0f, textSecondary)
            }

        return CardPage(w, h, boxes, outlines, lines, texts, tooths)
    }
}
