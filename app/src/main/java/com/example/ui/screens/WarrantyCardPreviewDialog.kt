package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.WarrantyCard
import com.example.export.WarrantyCardPdfExporter
import com.example.ui.components.WarrantyCardBackPreview
import com.example.ui.components.WarrantyCardFrontPreview
import com.example.ui.theme.StatusOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Front/back preview of a warranty card with the card actions:
 * Generate PDF (save + share), Print (Android print framework) and Close.
 * The preview reflects the actual saved card data and matches the two-page
 * CR80 PDF that will be produced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarrantyCardPreviewDialog(
    card: WarrantyCard,
    onDismiss: () -> Unit,
    onGeneratePdf: () -> Unit,
    onPrint: () -> Unit,
    busy: Boolean = false
) {
    var showFront by remember { mutableStateOf(true) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Warranty Card Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(card.cardNumber, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = showFront, onClick = { showFront = true }, label = { Text("Front") })
                    FilterChip(selected = !showFront, onClick = { showFront = false }, label = { Text("Back") })
                }

                if (showFront) {
                    WarrantyCardFrontPreview(card = card, formatDate = { dateFmt.format(Date(it)) })
                } else {
                    WarrantyCardBackPreview(card = card, formatDate = { dateFmt.format(Date(it)) })
                }

                Text(
                    "CR80 card (85.6 x 54 mm) - PDF page 1 = front, page 2 = back. " +
                        "${WarrantyCardPdfExporter.PRINT_NOTE}",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = StatusOrange
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text("Close")
                    }
                    OutlinedButton(onClick = onPrint, enabled = !busy, modifier = Modifier.weight(1f)) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.heightIn(max = 14.dp), strokeWidth = 2.dp)
                        else Text("Print")
                    }
                    Button(onClick = onGeneratePdf, enabled = !busy, modifier = Modifier.weight(1.3f)) {
                        Text("Generate PDF")
                    }
                }
            }
        }
    }
}
