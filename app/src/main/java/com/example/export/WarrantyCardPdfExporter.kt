package com.example.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.WarrantyCard
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Patient warranty card PDF: EXACTLY TWO CR80 / ID-1 card-sized pages
 * (85.60 mm x 53.98 mm = 3.375 x 2.125 in = 243 x 153 points at 72 dpi, landscape).
 *
 *  - Page 1 = FRONT — brand header (lab identity + tagline + card number), work-type
 *    title ("{WORK TYPE} WARRANTY CARD"), warranty duration badge, patient panel
 *    (name / address / contact) and clinical details (teeth, consultant doctor,
 *    delivery date, work order number).
 *  - Page 2 = BACK — header, card title, warranty period strip, numbered terms &
 *    conditions, care recommendations and a policy disclaimer footer.
 *
 * The page size is set explicitly on every PdfDocument.PageInfo, so the exported
 * PDF is a small card-sized document (never A4/Letter) and prints correctly at
 * "Actual Size / 100%". Print front/back on CR80 card stock.
 *
 * Layout is computed by pure functions ([frontLayout] / [backLayout]) that return
 * positioned shapes; [build] only renders that geometry. This makes the layout
 * unit-testable: page count, page dimensions, in-bounds guarantees, text wrapping
 * and completeness can all be verified without a device.
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
    const val KEEP_NOTE = "Keep this card safe for warranty claims"
    const val PRINT_NOTE = "Print at Actual Size (100%) - do not scale to fit the paper."

    /** Disclaimer printed on the card back - never a universal legal guarantee. */
    private const val DISCLAIMER =
        "Warranty terms are subject to the laboratory's actual policy and applicable agreements. " +
            "This card is not a substitute for professional dental advice."

    // Safe area / margins
    private const val MARGIN = 8f

    // Palette — professional blue / navy / white
    private val navy = Color.rgb(15, 23, 42)
    private val blue = Color.rgb(2, 132, 199)
    private val cyan = Color.rgb(56, 189, 248)
    private val lightBlue = Color.rgb(224, 242, 254)
    private val textPrimary = Color.rgb(15, 23, 42)
    private val textSecondary = Color.rgb(100, 116, 139)
    private val borderGray = Color.rgb(203, 213, 225)
    private val watermarkBlue = Color.argb(16, 2, 132, 199) // ~6% alpha

    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // ------------------------------------------------------------------ layout model

    /** Filled rectangle (header bands, panels, badge, footer strip); [radius] > 0 = rounded. */
    data class BoxShape(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val color: Int, val radius: Float = 0f
    )

    /** Stroked or plain line (dividers, footer rule). */
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

    /** Upper-cased work-type title; falls back to "DENTAL" when no work type is set. */
    fun workTitle(workType: String): String {
        val t = workType.trim()
        return if (t.isBlank()) "DENTAL" else t.uppercase(Locale.getDefault())
    }

    private fun measure(text: String, size: Float, bold: Boolean): Float =
        paint(Color.BLACK, size, bold).measureText(text)

    // ------------------------------------------------------------------ FRONT
    //
    // Vertical plan (values are text baselines, card height = 153):
    //   0-26    navy header: tooth icon + lab name (11) + tagline (19.5) + card no (11)
    //   26-28.5 blue accent strip
    //   33.5-51.5 work-type title (41) + "WARRANTY CARD" (49.5); blue badge on the right
    //   54-100  patient panel (light blue): label 61, name 69/78, address 86/91.5, phone 98
    //   61-114.5 right column: teeth (2-line capable), consultant, delivery, work order no
    //   137-153 footer strip: generic brand (+phone if configured) + keep-safe note
    fun frontLayout(card: WarrantyCard): CardPage {
        val boxes = mutableListOf<BoxShape>()
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

        // --- Subtle tooth watermark behind the details (drawn first, ~6% alpha)
        tooths += ToothShape(121.5f, 88f, 66f, 70f, watermarkBlue)

        // --- Brand header band
        boxes += BoxShape(0f, 0f, w, 26f, navy)
        boxes += BoxShape(0f, 26f, w, 28.5f, blue)
        tooths += ToothShape(15f, 13f, 10f, 12f, Color.WHITE)
        val numText = card.cardNumber
        val numW = measure(numText, 4.5f, bold = true)
        add(numText, w - MARGIN - numW, 11f, 4.5f, Color.WHITE, bold = true)
        val labAvail = (w - MARGIN - numW - 6f - 23f).coerceAtLeast(60f)
        add(
            ellipsize(card.labName.ifBlank { APP_BRAND }, labAvail, 6.8f, bold = true),
            23f, 11f, 6.8f, Color.WHITE, bold = true
        )
        add(TAGLINE, 23f, 19.5f, 3.8f, cyan)

        // --- Work-type title + warranty badge
        add(ellipsize(workTitle(card.workType), 169f, 6.3f, bold = true), MARGIN, 41f, 6.3f, navy, bold = true)
        add("WARRANTY CARD", MARGIN, 49.5f, 6.3f, blue, bold = true)

        val badgeLeft = w - MARGIN - 56f
        boxes += BoxShape(badgeLeft, 33.5f, w - MARGIN, 51.5f, blue, radius = 4f)
        val yearsLabel = "${card.warrantyYears} YEAR${if (card.warrantyYears == 1) "" else "S"}"
        addCentered(yearsLabel, badgeLeft, w - MARGIN, 43f, 6.8f, Color.WHITE, bold = true)
        addCentered("WARRANTY", badgeLeft, w - MARGIN, 49f, 3.6f, Color.WHITE)

        // --- Patient panel (light blue, rounded)
        boxes += BoxShape(6f, 54f, 128f, 100f, lightBlue, radius = 5f)
        add("PATIENT", 11f, 61f, 4.2f, textSecondary, bold = true)
        var py = 69f
        wrap(card.patientName.ifBlank { "-" }, 115f, 7.3f, bold = true, maxLines = 2).forEach { line ->
            add(line, 11f, py, 7.3f, navy, bold = true)
            py += 9f
        }
        if (card.patientAddress.isNotBlank()) {
            wrap(card.patientAddress, 115f, 4.3f, bold = false, maxLines = 2).forEach { line ->
                add(line, 11f, py, 4.3f, textSecondary)
                py += 5.5f
            }
        }
        if (card.patientPhone.isNotBlank()) {
            add(ellipsize(card.patientPhone, 115f, 4.6f, bold = false), 11f, 98f, 4.6f, textSecondary)
        }

        // --- Clinical details column (right)
        val colX = 136f
        fun field(label: String, value: String, labelY: Float, wrapTwoLines: Boolean) {
            add(label, colX, labelY, 4.2f, textSecondary, bold = true)
            var vy = labelY + 6.5f
            val maxLines = if (wrapTwoLines) 2 else 1
            wrap(value.ifBlank { "-" }, 99f, 5.6f, bold = true, maxLines = maxLines).forEach { line ->
                add(line, colX, vy, 5.6f, navy, bold = true)
                vy += 6.7f
            }
        }
        field("TOOTH NUMBER(S)", card.toothNumbers, 61f, wrapTwoLines = true)
        field("CONSULTANT DR.", card.consultantDoctor, 82f, wrapTwoLines = false)
        field("DATE OF DELIVERY", dateFmt.format(Date(card.deliveryDate)), 96f, wrapTwoLines = false)
        field("WORK ORDER NO.", card.workOrderNumber, 108f, wrapTwoLines = false)
        field(
            "MATERIAL / SHADE",
            listOf(card.material, card.shade).filter { it.isNotBlank() }.joinToString("  •  "),
            120f, wrapTwoLines = false
        )

        // --- Footer strip (generic branding + contact only if configured)
        boxes += BoxShape(0f, 137f, w, h, lightBlue)
        lines += LineShape(0f, 137f, w, 137f, blue, 0.8f)
        val footerLeft =
            if (card.labPhone.isNotBlank()) "$APP_BRAND  |  ${card.labPhone}" else APP_BRAND
        add(ellipsize(footerLeft, 130f, 4.5f, bold = true), MARGIN, 147.5f, 4.5f, navy, bold = true)
        val keepW = measure(KEEP_NOTE, 4f, bold = false)
        add(KEEP_NOTE, w - MARGIN - keepW, 147.5f, 4f, textSecondary)

        return CardPage(w, h, boxes, lines, texts, tooths)
    }

    // ------------------------------------------------------------------ BACK
    //
    // Vertical plan (values are text baselines, card height = 153):
    //   0-16     navy header: tooth icon + "DENTAL LAB MANAGEMENT" + card number
    //   24.5     centered "{WORK TYPE} WARRANTY CARD" title
    //   28-43    warranty period strip (light blue, rounded)
    //   51       "TERMS & CONDITIONS" heading; body auto-fits 54.5-103
    //   105.5    divider
    //   111      "CARE RECOMMENDATIONS" heading; bullets auto-fit 114.5-132.5
    //   134.5    divider; disclaimer footer 139.5 / 143.7
    fun backLayout(card: WarrantyCard): CardPage {
        val boxes = mutableListOf<BoxShape>()
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

        // --- Header
        boxes += BoxShape(0f, 0f, w, 16f, navy)
        tooths += ToothShape(13f, 8f, 8f, 9.5f, Color.WHITE)
        add(APP_BRAND.uppercase(Locale.getDefault()), 20f, 10.5f, 5f, Color.WHITE, bold = true)
        val numW = measure(card.cardNumber, 4f, bold = false)
        add(card.cardNumber, w - MARGIN - numW, 10.5f, 4f, Color.WHITE)

        // --- Centered card title
        val title = ellipsize("${workTitle(card.workType)} WARRANTY CARD", 227f, 5.5f, bold = true)
        addCentered(title, MARGIN, w - MARGIN, 24.5f, 5.5f, navy, bold = true)

        // --- Warranty period strip
        boxes += BoxShape(6f, 28f, w - 6f, 43f, lightBlue, radius = 4f)
        add("WARRANTY PERIOD", 10f, 34.5f, 3.8f, textSecondary, bold = true)
        val yearsWord = if (card.warrantyYears == 1) "Year" else "Years"
        val periodValue = "Valid from ${dateFmt.format(Date(card.deliveryDate))} to " +
            "${dateFmt.format(Date(card.warrantyExpiryDate))} (${card.warrantyYears} $yearsWord)"
        add(ellipsize(periodValue, 223f, 4.6f, bold = true), 10f, 40.8f, 4.6f, navy, bold = true)

        // --- Terms & conditions (numbered, auto-fitted)
        add("TERMS & CONDITIONS", MARGIN, 51f, 4.2f, navy, bold = true)
        val termsLines = card.terms.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val (termsSize, termsWrapped) = fitBlock(termsLines, 227f, 48.5f)
        var ty = 54.5f + termsSize
        termsWrapped.forEach { line ->
            add(line, MARGIN, ty, termsSize, textPrimary)
            ty += termsSize + 1.6f
        }

        // --- Care recommendations (bulleted, auto-fitted)
        lines += LineShape(MARGIN, 105.5f, w - MARGIN, 105.5f, borderGray, 0.5f)
        add("CARE RECOMMENDATIONS", MARGIN, 111f, 4.2f, navy, bold = true)
        val careLines = card.careInstructions.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith("•") || it.startsWith("-")) it else "• $it" }
        val (careSize, careWrapped) = fitBlock(careLines, 227f, 18f, maxSize = 4.3f)
        var cy = 114.5f + careSize
        careWrapped.forEach { line ->
            add(line, MARGIN, cy, careSize, textPrimary)
            cy += careSize + 1.4f
        }

        // --- Disclaimer footer
        lines += LineShape(MARGIN, 134.5f, w - MARGIN, 134.5f, borderGray, 0.5f)
        wrap(DISCLAIMER, 227f, 3.2f, bold = false, maxLines = 2)
            .forEachIndexed { i, line ->
                add(line, MARGIN, 139.5f + i * 4.2f, 3.2f, textSecondary)
            }

        return CardPage(w, h, boxes, lines, texts, tooths)
    }
}
