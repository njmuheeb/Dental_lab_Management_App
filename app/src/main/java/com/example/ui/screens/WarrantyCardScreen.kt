package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WarrantyCard
import com.example.data.model.WorkOrder
import com.example.data.repository.DentalLabRepository
import com.example.export.FileExporter
import com.example.export.WarrantyCardPdfExporter
import com.example.ui.DentalLabViewModel
import com.example.ui.theme.DentalBlue
import com.example.ui.theme.DentalBlueLight
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusOrange
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Create / edit a patient WARRANTY CARD for a specific work order and print or export it
 * as an Aadhaar-sized (CR80, 85.6 x 54 mm) two-sided PDF (page 1 = front, page 2 = back).
 * The card is stored in the database, linked to the work order, and stays editable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarrantyCardScreen(
    viewModel: DentalLabViewModel,
    workOrder: WorkOrder,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var card by remember { mutableStateOf<WarrantyCard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showFront by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var exportBusy by remember { mutableStateOf(false) }

    // Load or create the draft card for this work order
    LaunchedEffect(workOrder.id) {
        loading = true
        card = withContext(Dispatchers.IO) { viewModel.getOrCreateWarrantyCard(workOrder) }
        loading = false
    }
    BackHandler { onBack() }

    fun doExportPdf() {
        val current = card ?: return
        exportBusy = true
        scope.launch(Dispatchers.IO) {
            try {
                // ensure the latest edits are persisted before printing
                val saved = withContext(Dispatchers.IO) {
                    val computed = current.copy(
                        warrantyExpiryDate = DentalLabRepository.warrantyExpiry(current.deliveryDate, current.warrantyYears)
                    )
                    viewModel.repository.saveWarrantyCard(computed)
                    computed
                }
                val bytes = WarrantyCardPdfExporter.build(saved)
                val file = FileExporter.writeExportFile(context, "WarrantyCard_${saved.cardNumber}.pdf", bytes)
                withContext(Dispatchers.Main) {
                    showExportOptionsDialog(
                        context = context,
                        file = file,
                        mime = FileExporter.MIME_PDF,
                        title = "Warranty card ready",
                        message = "Saved ${file.name} - page 1: front, page 2: back. Use Print for front-and-back card printing.",
                        viewModel = viewModel
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { viewModel.showMessage("Warranty card export failed: ${e.message}") }
            } finally {
                withContext(Dispatchers.Main) { exportBusy = false }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text("Warranty Card", fontWeight = FontWeight.Bold, color = Navy900)
                        Text(
                            "${workOrder.jobNumber} • ${workOrder.patientName}",
                            fontSize = 11.sp, color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("warranty_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = Navy900
                )
            )

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (card == null) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Could not load warranty card", color = TextMuted)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val c = card!!
                    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

                    // ---------------- Preview ----------------
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "CARD PREVIEW • ${c.cardNumber}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Navy900
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = showFront,
                                        onClick = { showFront = true },
                                        label = { Text("Front", fontSize = 11.sp) },
                                        modifier = Modifier.testTag("preview_front_chip")
                                    )
                                    FilterChip(
                                        selected = !showFront,
                                        onClick = { showFront = false },
                                        label = { Text("Back", fontSize = 11.sp) },
                                        modifier = Modifier.testTag("preview_back_chip")
                                    )
                                }
                            }
                            if (showFront) {
                                WarrantyCardFrontPreview(
                                    card = c,
                                    formatDate = dateFmt::format
                                )
                            } else {
                                WarrantyCardBackPreview(
                                    card = c,
                                    formatDate = dateFmt::format
                                )
                            }
                            Text(
                                "Print size: 85.6 x 54 mm (Aadhaar/CR80 card) - PDF page 1 front, page 2 back",
                                fontSize = 10.sp, color = TextMuted
                            )
                        }
                    }

                    // ---------------- Card details form ----------------
                    Text("CARD DETAILS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Navy900)
                    OutlinedTextField(
                        value = c.labName,
                        onValueChange = { card = c.copy(labName = it) },
                        label = { Text("Dental Lab Name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = c.labAddress,
                        onValueChange = { card = c.copy(labAddress = it) },
                        label = { Text("Lab Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = c.labPhone,
                        onValueChange = { card = c.copy(labPhone = it) },
                        label = { Text("Lab Phone Number") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = TextMuted.copy(alpha = 0.3f))
                    OutlinedTextField(
                        value = c.patientName,
                        onValueChange = { card = c.copy(patientName = it) },
                        label = { Text("Patient Name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = c.patientAddress,
                        onValueChange = { card = c.copy(patientAddress = it) },
                        label = { Text("Patient Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = c.patientPhone,
                        onValueChange = { card = c.copy(patientPhone = it) },
                        label = { Text("Patient Phone Number") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = c.consultantDoctor,
                        onValueChange = { card = c.copy(consultantDoctor = it) },
                        label = { Text("Consultant Doctor") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = c.workType,
                        onValueChange = { card = c.copy(workType = it) },
                        label = { Text("Work Type / Material") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = c.toothNumbers,
                        onValueChange = { card = c.copy(toothNumbers = it) },
                        label = { Text("Tooth Number(s)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = c.shade,
                        onValueChange = { card = c.copy(shade = it) },
                        label = { Text("Shade") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )

                    // Delivery date + warranty years
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("  Date of Delivery / Issue: ${dateFmt.format(Date(c.deliveryDate))}", fontSize = 13.sp)
                    }
                    val expiry = DentalLabRepository.warrantyExpiry(c.deliveryDate, c.warrantyYears)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        var yearsExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = yearsExpanded, onExpandedChange = { yearsExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = "${c.warrantyYears} Years",
                                onValueChange = {}, readOnly = true,
                                label = { Text("Warranty Period") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearsExpanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = yearsExpanded, onDismissRequest = { yearsExpanded = false }) {
                                listOf(1, 2, 3, 5, 7, 10, 15, 20).forEach { y ->
                                    DropdownMenuItem(
                                        text = { Text("$y Year${if (y > 1) "s" else ""} Warranty") },
                                        onClick = { card = c.copy(warrantyYears = y); yearsExpanded = false }
                                    )
                                }
                            }
                        }
                        Text(
                            "Valid until ${dateFmt.format(Date(expiry))}",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StatusGreen
                        )
                    }

                    OutlinedTextField(
                        value = c.terms,
                        onValueChange = { card = c.copy(terms = it) },
                        label = { Text("Warranty Terms / Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )

                    // ---------------- Actions ----------------
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.saveWarrantyCard(c) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DentalBlue),
                            modifier = Modifier.weight(1f).testTag("save_warranty_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Card", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { doExportPdf() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                            modifier = Modifier.weight(1.4f).testTag("print_warranty_btn"),
                            enabled = !exportBusy
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print / Export PDF", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(90.dp))
                }
            }
        }
    }

    if (showDatePicker && card != null) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = card!!.deliveryDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utc ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        card = card!!.copy(
                            deliveryDate = Calendar.getInstance().apply {
                                set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// ---------------------------------------------------------------- card previews (CR80 proportion)

@Composable
fun WarrantyCardFrontPreview(card: WarrantyCard, formatDate: (Long) -> String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0F172A), Navy800)),
                RoundedCornerShape(12.dp)
            )
            .border(1.dp, DentalBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("DENTAL WARRANTY CARD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(card.labName, color = DentalBlueLight, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
            }
            Text(card.cardNumber, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Patient + validity badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("PATIENT", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(card.patientName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                if (card.patientPhone.isNotBlank()) {
                    Text(card.patientPhone, color = TextMuted, fontSize = 9.sp)
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(StatusOrange, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("VALID", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("${card.warrantyYears} YEARS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Work details grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                PreviewField("WORK / MATERIAL", card.workType)
                PreviewField("SHADE", card.shade.ifBlank { "-" })
                PreviewField("DELIVERED ON", formatDate(card.deliveryDate))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                PreviewField("TOOTH NUMBER(S)", card.toothNumbers.ifBlank { "-" })
                PreviewField("CONSULTANT DOCTOR", card.consultantDoctor.ifBlank { "-" })
                PreviewField("VALID UNTIL", formatDate(card.warrantyExpiryDate))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        // Footer strip
        Column {
            HorizontalDivider(color = DentalBlue.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(card.labPhone, color = DentalBlueLight, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text("Keep this card safe for warranty claims", color = TextMuted, fontSize = 8.sp)
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
            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
            .border(1.dp, DentalBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Header band
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DentalBlue, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text("WARRANTY TERMS & CONDITIONS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            card.terms,
            color = TextPrimary, fontSize = 9.sp, lineHeight = 12.sp,
            maxLines = 9,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(color = TextMuted.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(card.labName, color = Navy900, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                if (card.labAddress.isNotBlank()) {
                    Text(card.labAddress, color = TextSecondary, fontSize = 9.sp, maxLines = 2)
                }
                if (card.labPhone.isNotBlank()) {
                    Text("Call: ${card.labPhone}", color = TextSecondary, fontSize = 9.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Card No: ${card.cardNumber}", color = TextSecondary, fontSize = 9.sp)
                Text("Issued: ${formatDate(card.deliveryDate)}", color = TextSecondary, fontSize = 9.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(1.dp)
                        .background(TextPrimary)
                )
                Text("Authorised Signatory", color = TextMuted, fontSize = 8.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PreviewField(label: String, value: String) {
    Column {
        Text(label, color = TextMuted, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
        Text(
            value,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
