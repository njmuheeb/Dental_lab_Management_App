package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WarrantyCard
import com.example.export.WarrantyCardPdfExporter

// Palette mirrors the PDF exporter exactly so the in-app preview matches the printout.
private val PdfNavy = Color(0xFF0F172A)
private val PdfBlue = Color(0xFF0284C7)
private val PdfCyan = Color(0xFF38BDF8)
private val PdfLightBlue = Color(0xFFE0F2FE)
private val PdfSlate = Color(0xFF64748B)

/**
 * In-app previews of the warranty card, matching the two-page CR80 PDF produced by
 * [WarrantyCardPdfExporter]: front = patient + work details, back = terms, warranty
 * period, care recommendations and disclaimer. Proportioned to the physical card
 * (85.6 x 54 mm).
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

@Composable
fun WarrantyCardFrontPreview(card: WarrantyCard, formatDate: (Long) -> String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, PdfBlue.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        // Brand header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PdfNavy)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToothIcon(color = Color.White, width = 12.dp, height = 14.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    card.labName.ifBlank { WarrantyCardPdfExporter.APP_BRAND },
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(WarrantyCardPdfExporter.TAGLINE, color = PdfCyan, fontSize = 6.sp)
            }
            Text(card.cardNumber, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 7.sp)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(2.dp).background(PdfBlue)
        )

        // Work-type title + warranty badge
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    WarrantyCardPdfExporter.workTitle(card.workType),
                    color = PdfNavy, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("WARRANTY CARD", color = PdfBlue, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(PdfBlue, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "${card.warrantyYears} YEAR${if (card.warrantyYears == 1) "" else "S"}",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp
                )
                Text("WARRANTY", color = Color.White, fontSize = 6.sp)
            }
        }

        // Patient panel + clinical details
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1.05f)
                    .background(PdfLightBlue, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text("PATIENT", color = PdfSlate, fontWeight = FontWeight.Bold, fontSize = 7.sp)
                Text(
                    card.patientName.ifBlank { "-" },
                    color = PdfNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2
                )
                if (card.patientAddress.isNotBlank()) {
                    Text(card.patientAddress, color = PdfSlate, fontSize = 7.sp, maxLines = 2)
                }
                if (card.patientPhone.isNotBlank()) {
                    Text(card.patientPhone, color = PdfSlate, fontSize = 7.sp)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PreviewField("TOOTH NUMBER(S)", card.toothNumbers.ifBlank { "-" })
                PreviewField("CONSULTANT DR.", card.consultantDoctor.ifBlank { "-" })
                PreviewField("DATE OF DELIVERY", formatDate(card.deliveryDate))
                PreviewField("WORK ORDER NO.", card.workOrderNumber.ifBlank { "-" })
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Footer
        Column {
            HorizontalDivider(color = PdfBlue.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val footerLeft =
                    if (card.labPhone.isNotBlank()) "${WarrantyCardPdfExporter.APP_BRAND}  |  ${card.labPhone}"
                    else WarrantyCardPdfExporter.APP_BRAND
                Text(footerLeft, color = PdfNavy, fontWeight = FontWeight.Bold, fontSize = 7.sp)
                Text(WarrantyCardPdfExporter.KEEP_NOTE, color = PdfSlate, fontSize = 6.5.sp)
            }
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
            .border(1.dp, PdfBlue.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        // Header (navy, full bleed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PdfNavy)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToothIcon(color = Color.White, width = 9.dp, height = 11.dp)
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                WarrantyCardPdfExporter.APP_BRAND.uppercase(),
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 8.5.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(card.cardNumber, color = Color.White, fontSize = 6.5.sp)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Centered card title
            Text(
                "${WarrantyCardPdfExporter.workTitle(card.workType)} WARRANTY CARD",
                color = PdfNavy, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )

            // Warranty period strip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PdfLightBlue, RoundedCornerShape(5.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("WARRANTY PERIOD", color = PdfSlate, fontWeight = FontWeight.Bold, fontSize = 6.sp)
                val yearsWord = if (card.warrantyYears == 1) "Year" else "Years"
                Text(
                    "Valid from ${formatDate(card.deliveryDate)} to ${formatDate(card.warrantyExpiryDate)} " +
                        "(${card.warrantyYears} $yearsWord)",
                    color = PdfNavy, fontWeight = FontWeight.Bold, fontSize = 7.5.sp
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Text("TERMS & CONDITIONS", color = PdfNavy, fontWeight = FontWeight.Bold, fontSize = 7.sp)
            Text(
                card.terms,
                color = PdfNavy, fontSize = 6.5.sp, lineHeight = 8.sp,
                maxLines = 7, overflow = TextOverflow.Ellipsis
            )

            HorizontalDivider(color = PdfSlate.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 2.dp))

            Text("CARE RECOMMENDATIONS", color = PdfNavy, fontWeight = FontWeight.Bold, fontSize = 7.sp)
            val care = card.careInstructions.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n") { if (it.startsWith("•") || it.startsWith("-")) it else "• $it" }
            Text(care, color = PdfNavy, fontSize = 6.5.sp, lineHeight = 8.sp, maxLines = 3)

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(color = PdfSlate.copy(alpha = 0.3f))
            Text(
                "Warranty terms are subject to the laboratory's actual policy and applicable agreements. " +
                    "This card is not a substitute for professional dental advice.",
                color = PdfSlate, fontSize = 5.5.sp, lineHeight = 7.sp
            )
        }
    }
}

@Composable
private fun PreviewField(label: String, value: String) {
    Column {
        Text(label, color = PdfSlate, fontWeight = FontWeight.Bold, fontSize = 6.5.sp)
        Text(value, color = PdfNavy, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp, maxLines = 1)
    }
}
