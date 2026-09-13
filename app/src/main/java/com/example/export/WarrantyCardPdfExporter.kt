package com.example.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.WarrantyCard
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Patient warranty card PDF: two CR80-style pages (Aadhaar-like proportion, 85.6 x 54 mm
 * at 72 dpi = 243 x 153 points). Page 1 = front (lab identity, patient, work details,
 * validity), page 2 = back (terms & conditions, contact, signature). Front-and-back
 * printing works directly with the two card-sized pages.
 *
 * "Rs."-free zone: no currency here. Base-14 fonts are used, so only ASCII glyphs.
 */
object WarrantyCardPdfExporter {

    // 85.6mm x 54mm at 72dpi
    private const val CARD_W = 242.6f
    private const val CARD_H = 153.1f

    private val navy = Color.rgb(15, 23, 42)
    private val blue = Color.rgb(2, 132, 199)
    private val cyan = Color.rgb(56, 189, 248)
    private val lightBlue = Color.rgb(224, 242, 254)
    private val gold = Color.rgb(245, 158, 11)
    private val textPrimary = Color.rgb(15, 23, 42)
    private val textSecondary = Color.rgb(100, 116, 139)
    private val borderGray = Color.rgb(203, 213, 225)

    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun build(card: WarrantyCard): ByteArray {
        val doc = PdfDocument()
        try {
            // ---- Page 1: FRONT ----
            val front = doc.startPage(PdfDocument.PageInfo.Builder(f(CARD_W), f(CARD_H), 1).create())
            drawFront(front.canvas, card)
            doc.finishPage(front)

            // ---- Page 2: BACK ----
            val back = doc.startPage(PdfDocument.PageInfo.Builder(f(CARD_W), f(CARD_H), 2).create())
            drawBack(back.canvas, card)
            doc.finishPage(back)

            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            return out.toByteArray()
        } finally {
            doc.close()
        }
    }

    private fun f(v: Float) = Math.round(v).toInt()

    private fun paint(color: Int, size: Float, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.DEFAULT
        }

    private fun wrap(text: String, maxWidth: Float, p: Paint): List<String> {
        val lines = mutableListOf<String>()
        text.split(" ").forEach { word ->
            val candidate = if (lines.isEmpty()) word else lines.last() + " " + word
            if (p.measureText(candidate) <= maxWidth) {
                if (lines.isEmpty()) lines.add(candidate) else lines[lines.size - 1] = candidate
            } else {
                lines.add(word)
            }
        }
        return lines
    }

    // ------------------------------------------------------------------ FRONT

    private fun drawFront(canvas: Canvas, card: WarrantyCard) {
        // Background
        canvas.drawRect(0f, 0f, CARD_W, CARD_H, Paint().apply { color = Color.WHITE })
        // Outer frame
        canvas.drawRoundRect(RectF(2f, 2f, CARD_W - 2f, CARD_H - 2f), 8f, 8f, Paint().apply {
            color = borderGray; strokeWidth = 1f; style = Paint.Style.STROKE
        })

        // Header band
        canvas.drawRect(0f, 0f, CARD_W, 34f, Paint().apply { color = navy })
        canvas.drawRect(0f, 34f, CARD_W, 37f, Paint().apply { color = blue })

        val titleP = paint(Color.WHITE, 8.5f, bold = true)
        canvas.drawText("DENTAL WARRANTY CARD", 10f, 15f, titleP)
        // Lab name (may need wrapping, up to 2 lines)
        val labP = paint(cyan, 6.5f, bold = true)
        wrap(card.labName, CARD_W - 20f, labP).take(2).forEachIndexed { i, line ->
            canvas.drawText(line, 10f, 25f + i * 7.5f, labP)
        }
        // Card number (top right)
        val numP = paint(Color.WHITE, 5.5f, bold = true)
        val num = card.cardNumber
        canvas.drawText(num, CARD_W - 10f - numP.measureText(num), 15f, numP)

        // "Valid X Years" badge (gold)
        val badge = RectF(CARD_W - 62f, 44f, CARD_W - 10f, 62f)
        canvas.drawRoundRect(badge, 4f, 4f, Paint().apply { color = gold })
        canvas.drawText("VALID", badge.left + 14f, 51f, paint(Color.WHITE, 5f, bold = true))
        canvas.drawText("${card.warrantyYears} YEARS", badge.left + 6f, 59.5f, paint(Color.WHITE, 6.5f, bold = true))

        // Patient block
        var y = 50f
        canvas.drawText("PATIENT", 10f, y, paint(textSecondary, 4.5f, bold = true))
        val nameP = paint(textPrimary, 9f, bold = true)
        val nameLines = wrap(card.patientName, CARD_W - 90f, nameP).take(2)
        nameLines.forEachIndexed { i, l -> canvas.drawText(l, 10f, y + 9f + i * 10f, nameP)
        }
        if (nameLines.size == 1) {
            val phone = card.patientPhone
            if (phone.isNotBlank()) canvas.drawText(phone, 10f, y + 19f, paint(textSecondary, 5.5f))
        }

        // Fields grid (2 columns x 3 rows)
        val labelP = paint(textSecondary, 4.2f, bold = true)
        val valueP = paint(textPrimary, 6f, bold = true)
        val col1X = 10f
        val col2X = CARD_W / 2f + 4f
        val rows = listOf(
            Triple("WORK / MATERIAL", ellipsize("${card.workType}${if (card.material.isNotBlank()) " - ${card.material}" else ""}", valueP, CARD_W / 2f - 16f), col1X),
            Triple("TOOTH NUMBER(S)", card.toothNumbers.ifBlank { "-" }, col2X),
            Triple("SHADE", card.shade.ifBlank { "-" }, col1X),
            Triple("CONSULTANT DOCTOR", card.consultantDoctor.ifBlank { "-" }, col2X),
            Triple("DELIVERED ON", dateFmt.format(Date(card.deliveryDate)), col1X),
            Triple("VALID UNTIL", dateFmt.format(Date(card.warrantyExpiryDate)), col2X)
        )
        var gy = 78f
        rows.forEach { (label, value, x) ->
            canvas.drawText(label, x, gy, labelP)
            canvas.drawText(ellipsize(value, valueP, CARD_W / 2f - 14f), x, gy + 7f, valueP)
            gy += 17f
        }

        // Divider + expiry highlight strip
        canvas.drawRect(0f, CARD_H - 16f, CARD_W, CARD_H, Paint().apply { color = lightBlue })
        canvas.drawRect(0f, CARD_H - 16.5f, CARD_W, CARD_H - 16f, Paint().apply { color = blue })
        val footP = paint(navy, 5f, bold = true)
        val labContact = listOf(card.labPhone).filter { it.isNotBlank() }.joinToString(" | ")
        canvas.drawText(labContact, 10f, CARD_H - 6f, footP)
        val keep = "Keep this card safe for warranty claims"
        val keepP = paint(textSecondary, 4.5f)
        canvas.drawText(keep, CARD_W - 8f - keepP.measureText(keep), CARD_H - 6f, keepP)
    }

    // ------------------------------------------------------------------ BACK

    private fun drawBack(canvas: Canvas, card: WarrantyCard) {
        canvas.drawRect(0f, 0f, CARD_W, CARD_H, Paint().apply { color = Color.WHITE })
        canvas.drawRoundRect(RectF(2f, 2f, CARD_W - 2f, CARD_H - 2f), 8f, 8f, Paint().apply {
            color = borderGray; strokeWidth = 1f; style = Paint.Style.STROKE
        })

        // Header band
        canvas.drawRect(0f, 0f, CARD_W, 20f, Paint().apply { color = blue })
        canvas.drawText("WARRANTY TERMS & CONDITIONS", 10f, 13f, paint(Color.WHITE, 7f, bold = true))

        // Terms body (tiny but readable)
        val termsP = paint(textPrimary, 4.3f)
        val termsLines = card.terms.replace("\n", " ").split(" ").fold(mutableListOf<String>()) { acc, word ->
            val candidate = if (acc.isEmpty()) word else acc.last() + " " + word
            if (termsP.measureText(candidate) <= CARD_W - 24f) {
                if (acc.isEmpty()) acc.add(word) else acc[acc.size - 1] = candidate
            } else acc.add(word)
            acc
        }
        var ty = 28f
        termsLines.take(14).forEach { line ->
            canvas.drawText(line, 10f, ty, termsP)
            ty += 5.6f
        }

        // Lab contact block
        val cy = CARD_H - 46f
        canvas.drawLine(8f, cy - 6f, CARD_W - 8f, cy - 6f, Paint().apply { color = borderGray; strokeWidth = 0.6f })
        val contactP = paint(textSecondary, 4.6f)
        val contact1P = paint(navy, 5.5f, bold = true)
        canvas.drawText(card.labName, 10f, cy + 2f, contact1P)
        var cyy = cy + 9.5f
        if (card.labAddress.isNotBlank()) {
            wrap(card.labAddress, CARD_W - 90f, contactP).take(2).forEach { line ->
                canvas.drawText(line, 10f, cyy, contactP); cyy += 6f
            }
        }
        if (card.labPhone.isNotBlank()) {
            canvas.drawText("Call: ${card.labPhone}", 10f, cyy, contactP)
        }

        // Card number + issue date + signature
        val infoP = paint(textSecondary, 4.6f)
        canvas.drawText("Card No: ${card.cardNumber}", CARD_W - 82f, cy + 2f, infoP)
        canvas.drawText("Issued: ${dateFmt.format(Date(card.deliveryDate))}", CARD_W - 82f, cy + 9f, infoP)
        // Signature line
        val sigY = CARD_H - 12f
        canvas.drawLine(CARD_W - 78f, sigY, CARD_W - 12f, sigY, Paint().apply { color = textPrimary; strokeWidth = 0.6f })
        canvas.drawText("Authorised Signatory", CARD_W - 74f, sigY + 5.5f, paint(textSecondary, 4.2f))

        // Bottom navy strip
        canvas.drawRect(0f, CARD_H - 4f, CARD_W, CARD_H, Paint().apply { color = navy })
    }

    private fun ellipsize(text: String, p: Paint, maxWidth: Float): String {
        if (p.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t...") > maxWidth) t = t.dropLast(1)
        return "$t..."
    }
}
