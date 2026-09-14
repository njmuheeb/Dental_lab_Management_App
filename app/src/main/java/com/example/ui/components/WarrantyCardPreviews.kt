package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WarrantyCard
import com.example.data.util.ToothFormat
import com.example.export.WarrantyCardPdfExporter

// Palette mirrors the PDF exporter exactly so the in-app preview matches the printout.
private val GreenDark = Color(0xFF14532D)
private val Green = Color(0xFF16A34A)
private val GreenLight = Color(0xFFF0FDF4)
private val GreenBorder = Color(0xFFBBF7D0)
private val GreenTint = Color(0xFFBBF7D0)
private val Slate = Color(0xFF4B5563)

/**
 * In-app previews of the warranty card, matching the two-page CR80 PDF produced by
 * [WarrantyCardPdfExporter]: green-and-white front (details + four-quadrant tooth
 * diagram + warranty strip) and back (terms, care recommendations, disclaimer).
 * Proportioned to the physical card (85.6 x 54 mm).
 */

/** Stylized tooth glyph drawn with the exact geometry used in the PDF. */
@Composable
fun ToothIcon(color: Color, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width, height)) {
        val (start, segments) = WarrantyCardPdfExporter.toothGeometry(
            size.width / 2f, size.height / 2f, size.width, size.height
        )
        val path = Path().apply {
            moveTo(start[0], start[1])
            segments.forEach { cubicTo(it[0], it[1], it[2], it[3], it[4], it[5]) }
            close()
        }
        drawPath(path, color)
    }
}

/**
 * Four-quadrant tooth-number diagram: a vertical and a horizontal divider crossing
 * at the exact center create four quadrant cells (UR | UL / LR | LL — patient front
 * view, standard charting). Each cell shows only the teeth actually worked on, as
 * single digits 1-8 in ascending order; empty quadrants stay blank.
 */
@Composable
fun ToothQuadrantDiagram(selectedTeeth: String, modifier: Modifier = Modifier) {
    val quadrants = ToothFormat.quadrantDigits(selectedTeeth)
    Column(
        modifier = modifier
            .background(GreenLight, RoundedCornerShape(8.dp))
            .border(1.dp, GreenBorder, RoundedCornerShape(8.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            QuadrantCell("UR", quadrants.upperRight, Modifier.weight(1f).fillMaxHeight())
            VerticalDivider(color = Green, thickness = 1.dp, modifier = Modifier.fillMaxHeight())
            QuadrantCell("UL", quadrants.upperLeft, Modifier.weight(1f).fillMaxHeight())
        }
        HorizontalDivider(color = Green, thickness = 1.dp, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            QuadrantCell("LR", quadrants.lowerRight, Modifier.weight(1f).fillMaxHeight())
            VerticalDivider(color = Green, thickness = 1.dp, modifier = Modifier.fillMaxHeight())
            QuadrantCell("LL", quadrants.lowerLeft, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun QuadrantCell(label: String, digits: List<Int>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = Slate, fontWeight = FontWeight.Bold, fontSize = 7.sp)
        digits.chunked(4).forEach { row ->
            Text(
                row.joinToString(" ") { it.toString() },
                color = GreenDark,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun WarrantyCardFrontPreview(card: WarrantyCard, formatDate: (Long) -> String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.5.dp, Green, RoundedCornerShape(12.dp))
    ) {
        // Brand header (dark green band)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GreenDark)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToothIcon(color = Color.White, width = 10.dp, height = 12.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    card.labName.ifBlank { WarrantyCardPdfExporter.APP_BRAND },
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(WarrantyCardPdfExporter.TAGLINE, color = GreenTint, fontSize = 6.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("WARRANTY CARD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 6.5.sp)
                Text(card.cardNumber, color = GreenTint, fontSize = 6.sp)
            }
        }

        // Details (left) + tooth diagram (right)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PreviewField("DATE", formatDate(card.deliveryDate), Modifier.weight(1f))
                    PreviewField("CASE NO.", card.workOrderNumber.ifBlank { "-" }, Modifier.weight(1f))
                }
                PreviewField("PATIENT NAME", card.patientName.ifBlank { "-" }, wrap = true)
                PreviewField("ADDRESS", card.patientAddress.ifBlank { "-" }, wrap = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PreviewField("CONTACT NO.", card.patientPhone.ifBlank { "-" }, Modifier.weight(1f))
                    PreviewField("DENTIST NAME", card.consultantDoctor.ifBlank { "-" }, Modifier.weight(1f))
                }
                PreviewField(
                    "TYPE OF WORK",
                    listOf(card.workType, card.material).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "-" },
                    wrap = true
                )
            }
            Column(modifier = Modifier.weight(0.9f)) {
                Text(
                    "TOOTH NUMBER(S)",
                    color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 8.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
                )
                ToothQuadrantDiagram(
                    selectedTeeth = WarrantyCardPdfExporter.effectiveTeeth(card),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Warranty-period strip (bottom)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Green, RoundedCornerShape(8.dp))
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val yearsWord = if (card.warrantyYears == 1) "YEAR" else "YEARS"
            Text(
                "WARRANTY PERIOD: ${card.warrantyYears} $yearsWord",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp
            )
            Text(
                "Valid from ${formatDate(card.deliveryDate)} to ${formatDate(card.warrantyExpiryDate)}",
                color = Color.White, fontSize = 7.sp
            )
        }
    }
}

@Composable
fun WarrantyCardBackPreview(card: WarrantyCard, formatDate: (Long) -> String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.5.dp, Green, RoundedCornerShape(12.dp))
    ) {
        // Header (same band as the front)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GreenDark)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToothIcon(color = Color.White, width = 10.dp, height = 12.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    card.labName.ifBlank { WarrantyCardPdfExporter.APP_BRAND },
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(WarrantyCardPdfExporter.TAGLINE, color = GreenTint, fontSize = 6.sp)
            }
            Text(card.cardNumber, color = Color.White, fontSize = 6.sp)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Warranty period strip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GreenLight, RoundedCornerShape(6.dp))
                    .border(1.dp, GreenBorder, RoundedCornerShape(6.dp))
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val yearsWord = if (card.warrantyYears == 1) "YEAR" else "YEARS"
                Text(
                    "WARRANTY PERIOD: ${card.warrantyYears} $yearsWord",
                    color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 8.sp
                )
                Text(
                    "Valid from ${formatDate(card.deliveryDate)} to ${formatDate(card.warrantyExpiryDate)}",
                    color = GreenDark, fontSize = 6.5.sp
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                "WARRANTY TERMS & CONDITIONS",
                color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 8.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                card.terms,
                color = Color(0xFF111827), fontSize = 6.5.sp, lineHeight = 8.sp,
                maxLines = 7, overflow = TextOverflow.Ellipsis
            )

            HorizontalDivider(color = Slate.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 2.dp))

            Text(
                "CARE RECOMMENDATIONS",
                color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 8.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            val care = card.careInstructions.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n") { if (it.startsWith("•") || it.startsWith("-")) it else "• $it" }
            Text(care, color = Color(0xFF111827), fontSize = 6.5.sp, lineHeight = 8.sp, maxLines = 3)

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(color = Slate.copy(alpha = 0.3f))
            Text(
                when {
                    card.labPhone.isNotBlank() -> "Contact: ${card.labPhone}"
                    card.labAddress.isNotBlank() -> card.labAddress
                    else -> "For support, contact your prescribing dental clinic."
                },
                color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 6.5.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "Warranty terms are subject to the laboratory's actual policy and applicable agreements. " +
                    "This card is not a substitute for professional dental advice.",
                color = Slate, fontSize = 5.5.sp, lineHeight = 7.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun PreviewField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    wrap: Boolean = false
) {
    Column(modifier = modifier) {
        Text(label, color = Slate, fontWeight = FontWeight.Bold, fontSize = 6.5.sp)
        Text(
            value,
            color = Color(0xFF111827),
            fontWeight = FontWeight.SemiBold,
            fontSize = 8.5.sp,
            maxLines = if (wrap) 2 else 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
