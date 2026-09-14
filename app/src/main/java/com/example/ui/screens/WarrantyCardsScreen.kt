package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Clinic
import com.example.data.model.Patient
import com.example.data.model.WarrantyCard
import com.example.data.model.WorkOrder
import com.example.export.FileExporter
import com.example.export.WarrantyCardPdfExporter
import com.example.ui.DentalLabViewModel
import com.example.ui.theme.DentalBlue
import com.example.ui.theme.Navy900
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun yearOf(timeMillis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = timeMillis }.get(Calendar.YEAR)

/**
 * Dedicated Warranty Cards management screen (sidebar entry):
 *  - create a new card (from an existing work order, or manual entry)
 *  - search by patient, clinic, card number or work order
 *  - filter by clinic, delivery year and Active/Expired status
 *  - view (front/back preview), edit, generate PDF, print and delete cards
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarrantyCardsScreen(
    viewModel: DentalLabViewModel,
    onEditCard: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cards by viewModel.warrantyCards.collectAsState()
    val clinics by viewModel.clinics.collectAsState()
    val workOrders by viewModel.workOrders.collectAsState()

    var search by remember { mutableStateOf("") }
    var clinicFilter by remember { mutableStateOf<Long?>(null) }
    var statusFilter by remember { mutableStateOf("All") }
    var yearFilter by remember { mutableStateOf<Int?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var previewCard by remember { mutableStateOf<WarrantyCard?>(null) }
    var deleteTarget by remember { mutableStateOf<WarrantyCard?>(null) }
    var busyCardId by remember { mutableStateOf<Long?>(null) }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val now = remember { System.currentTimeMillis() }

    fun isExpired(card: WarrantyCard): Boolean = card.warrantyExpiryDate < now

    /** Builds the two-page CR80 PDF and either prints it or offers share/save. */
    fun generatePdf(card: WarrantyCard, print: Boolean) {
        busyCardId = card.id
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = WarrantyCardPdfExporter.build(card)
                val file = FileExporter.writeExportFile(context, "WarrantyCard_${card.cardNumber}.pdf", bytes)
                withContext(Dispatchers.Main) {
                    if (print) {
                        FileExporter.printPdf(
                            context, file, "Warranty Card ${card.cardNumber}",
                            WarrantyCardPdfExporter.PAGE_COUNT
                        )
                    } else {
                        showExportOptionsDialog(
                            context = context,
                            file = file,
                            mime = FileExporter.MIME_PDF,
                            title = "Warranty card ready",
                            message = "Saved ${file.name} - 2 card pages (85.6 x 54 mm): " +
                                "page 1 front, page 2 back. ${WarrantyCardPdfExporter.PRINT_NOTE}",
                            viewModel = viewModel,
                            pageCount = WarrantyCardPdfExporter.PAGE_COUNT
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { viewModel.showMessage("Export failed: ${e.message}") }
            } finally {
                withContext(Dispatchers.Main) { busyCardId = null }
            }
        }
    }

    val deliveryYears = remember(cards) {
        cards.map { yearOf(it.deliveryDate) }.distinct().sortedDescending()
    }

    val filtered = cards.filter { card ->
        val q = search.trim()
        (q.isBlank() || listOf(card.patientName, card.clinicName, card.cardNumber, card.workOrderNumber)
            .any { it.contains(q, ignoreCase = true) }) &&
            (clinicFilter == null || card.clinicId == clinicFilter) &&
            (statusFilter == "All" || (statusFilter == "Active") != isExpired(card)) &&
            (yearFilter == null || yearOf(card.deliveryDate) == yearFilter)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { showCreateDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = DentalBlue),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().testTag("create_warranty_btn")
        ) {
            Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text("Create New Warranty Card", fontWeight = FontWeight.Bold)
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search patient, clinic, card or work order...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isNotBlank()) {
                    IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("warranty_search_field")
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClinicFilterDropdown(
                selectedClinicId = clinicFilter,
                clinics = clinics,
                onSelect = { clinicFilter = it },
                modifier = Modifier.weight(1f)
            )
            YearFilterDropdown(
                selectedYear = yearFilter,
                years = deliveryYears,
                onSelect = { yearFilter = it },
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Active", "Expired").forEach { status ->
                FilterChip(
                    selected = statusFilter == status,
                    onClick = { statusFilter = status },
                    label = { Text(status) }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${filtered.size} Card(s)",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }

        if (cards.isEmpty()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = DentalBlue)
                    Text("No warranty cards yet", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Navy900)
                    Text(
                        "Create one from a delivered work order (Work Orders -> job details -> Warranty Card) " +
                            "or tap \"Create New Warranty Card\" above.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { card ->
                    WarrantyCardItem(
                        card = card,
                        expired = isExpired(card),
                        busy = busyCardId == card.id,
                        dateFmt = dateFmt,
                        onView = { previewCard = card },
                        onEdit = { onEditCard(card.id) },
                        onPdf = { generatePdf(card, print = false) },
                        onPrint = { generatePdf(card, print = true) },
                        onDelete = { deleteTarget = card }
                    )
                }
            }
        }
    }

    // --- Preview dialog (front/back + generate/print)
    previewCard?.let { card ->
        WarrantyCardPreviewDialog(
            card = card,
            onDismiss = { previewCard = null },
            onGeneratePdf = { generatePdf(card, print = false) },
            onPrint = { generatePdf(card, print = true) },
            busy = busyCardId == card.id
        )
    }

    // --- Delete confirmation
    deleteTarget?.let { card ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Warranty Card?") },
            text = {
                Text(
                    "Delete ${card.cardNumber} (${card.patientName.ifBlank { "no patient" }})? " +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWarrantyCard(card)
                    deleteTarget = null
                }) { Text("Delete", color = StatusRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    // --- Create dialog
    if (showCreateDialog) {
        NewWarrantyCardDialog(
            viewModel = viewModel,
            workOrders = workOrders,
            existingCardOrderIds = cards.mapNotNull { it.workOrderId }.toSet(),
            onDismiss = { showCreateDialog = false },
            onCardCreated = { cardId ->
                showCreateDialog = false
                onEditCard(cardId)
            }
        )
    }
}

// ------------------------------------------------------------------ list item

@Composable
private fun WarrantyCardItem(
    card: WarrantyCard,
    expired: Boolean,
    busy: Boolean,
    dateFmt: SimpleDateFormat,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onPdf: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(card.cardNumber, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Navy900)
                Surface(
                    color = if (expired) StatusRed.copy(alpha = 0.1f) else StatusGreen.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        if (expired) "Expired" else "Active",
                        color = if (expired) StatusRed else StatusGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                "${card.patientName.ifBlank { "-" }}  •  ${card.clinicName.ifBlank { "No clinic" }}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Navy900
            )
            Text(
                "${card.workType.ifBlank { "-" }}  •  Teeth: ${card.toothNumbers.ifBlank { "-" }}",
                fontSize = 11.sp,
                color = TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Delivered: ${dateFmt.format(Date(card.deliveryDate))}", fontSize = 10.5.sp, color = TextSecondary)
                Text(
                    "${card.warrantyYears} Year(s) - until ${dateFmt.format(Date(card.warrantyExpiryDate))}",
                    fontSize = 10.5.sp,
                    color = if (expired) StatusRed else StatusGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (card.workOrderNumber.isNotBlank()) {
                Text("Work Order: ${card.workOrderNumber}", fontSize = 10.sp, color = TextMuted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactAction("View", Icons.Default.Visibility, onView)
                CompactAction("Edit", Icons.Default.Edit, onEdit)
                CompactAction("PDF", Icons.Default.PictureAsPdf, onPdf)
                CompactAction("Print", Icons.Default.Print, onPrint, enabled = !busy)
                Spacer(modifier = Modifier.weight(1f))
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete card", tint = StatusRed)
                }
            }
        }
    }
}

@Composable
private fun CompactAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.heightIn(max = 34.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.size(4.dp))
        Text(label, fontSize = 11.sp)
    }
}

// ------------------------------------------------------------------ dropdowns

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClinicFilterDropdown(
    selectedClinicId: Long?,
    clinics: List<Clinic>,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selectedClinicId?.let { id -> clinics.find { it.id == id }?.name } ?: "All Clinics"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Filter by Clinic") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All Clinics") }, onClick = { onSelect(null); expanded = false })
            clinics.forEach { clinic ->
                DropdownMenuItem(text = { Text(clinic.name) }, onClick = { onSelect(clinic.id); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearFilterDropdown(
    selectedYear: Int?,
    years: List<Int>,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedYear?.toString() ?: "All Years",
            onValueChange = {},
            readOnly = true,
            label = { Text("Delivery Year") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All Years") }, onClick = { onSelect(null); expanded = false })
            years.forEach { year ->
                DropdownMenuItem(text = { Text(year.toString()) }, onClick = { onSelect(year); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClinicPickerDropdown(
    selected: Clinic?,
    clinics: List<Clinic>,
    onSelect: (Clinic?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected?.name ?: "No clinic link"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Clinic (optional)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("No clinic link") }, onClick = { onSelect(null); expanded = false })
            clinics.forEach { clinic ->
                DropdownMenuItem(text = { Text(clinic.name) }, onClick = { onSelect(clinic); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientPickerDropdown(
    selected: Patient?,
    patients: List<Patient>,
    onSelect: (Patient?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected?.let { "${it.name} (${it.patientCode})" } ?: "No patient link"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Patient (optional)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("No patient link") }, onClick = { onSelect(null); expanded = false })
            patients.forEach { patient ->
                DropdownMenuItem(
                    text = { Text("${patient.name} (${patient.patientCode})") },
                    onClick = { onSelect(patient); expanded = false }
                )
            }
        }
    }
}

// ------------------------------------------------------------------ create dialog

/**
 * "New Warranty Card" dialog with two modes:
 *  - From Work Order: searchable list of work orders that do not have a card yet;
 *    selecting one prefills the card from the order (existing get-or-create logic).
 *  - Manual Entry: standalone card, optionally linked to an existing clinic and
 *    patient (dropdowns); everything else is typed by hand in the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewWarrantyCardDialog(
    viewModel: DentalLabViewModel,
    workOrders: List<WorkOrder>,
    existingCardOrderIds: Set<Long>,
    onDismiss: () -> Unit,
    onCardCreated: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val activeClinics by viewModel.activeClinics.collectAsState()
    val patients by viewModel.patients.collectAsState()

    var fromOrder by remember { mutableStateOf(true) }
    var orderSearch by remember { mutableStateOf("") }
    var selectedClinic by remember { mutableStateOf<Clinic?>(null) }
    var selectedPatient by remember { mutableStateOf<Patient?>(null) }
    var creating by remember { mutableStateOf(false) }

    val availableOrders = workOrders
        .filter { it.id !in existingCardOrderIds }
        .filter { order ->
            orderSearch.isBlank() || listOf(
                order.jobNumber, order.patientName, order.clinicName, order.workTypeName
            ).any { it.contains(orderSearch, ignoreCase = true) }
        }
        .sortedByDescending { it.entryDate }

    val clinicPatients = if (selectedClinic != null) {
        patients.filter { it.clinicId == selectedClinic!!.id }
    } else patients

    Dialog(onDismissRequest = { if (!creating) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "New Warranty Card",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = fromOrder, onClick = { fromOrder = true }, label = { Text("From Work Order") })
                    FilterChip(selected = !fromOrder, onClick = { fromOrder = false }, label = { Text("Manual Entry") })
                }

                if (fromOrder) {
                    OutlinedTextField(
                        value = orderSearch,
                        onValueChange = { orderSearch = it },
                        label = { Text("Search work orders...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    when {
                        workOrders.isEmpty() -> Text(
                            "No work orders yet. Create a work order first, or use Manual Entry.",
                            fontSize = 12.sp, color = TextSecondary
                        )
                        availableOrders.isEmpty() -> Text(
                            if (existingCardOrderIds.size == workOrders.size)
                                "All work orders already have warranty cards. Use Manual Entry for an extra card."
                            else "No matching work orders.",
                            fontSize = 12.sp, color = TextSecondary
                        )
                        else -> LazyColumn(
                            modifier = Modifier.heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(availableOrders, key = { it.id }) { order ->
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        if (creating) return@OutlinedCard
                                        creating = true
                                        scope.launch {
                                            val card = viewModel.getOrCreateWarrantyCard(order)
                                            if (card != null) onCardCreated(card.id) else {
                                                creating = false
                                                viewModel.showMessage("Could not create warranty card")
                                            }
                                        }
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            "${order.jobNumber}  •  ${order.patientName}",
                                            fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                                        )
                                        Text(
                                            "${order.clinicName}  •  ${order.workTypeName}",
                                            fontSize = 10.5.sp, color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Manual entry: optional clinic + patient links
                    ClinicPickerDropdown(
                        selected = selectedClinic,
                        clinics = activeClinics,
                        onSelect = {
                            selectedClinic = it
                            selectedPatient = null // patient list depends on the clinic
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    PatientPickerDropdown(
                        selected = selectedPatient,
                        patients = clinicPatients,
                        onSelect = { selectedPatient = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Patient name, address and contact details can be entered by hand in the editor next. " +
                            "Selecting a patient pre-fills them.",
                        fontSize = 11.sp, color = TextSecondary, lineHeight = 14.sp
                    )
                    Button(
                        onClick = {
                            if (creating) return@Button
                            creating = true
                            scope.launch {
                                val card = viewModel.createManualWarrantyCard(selectedClinic, selectedPatient)
                                if (card != null) onCardCreated(card.id) else {
                                    creating = false
                                    viewModel.showMessage("Could not create warranty card")
                                }
                            }
                        },
                        enabled = !creating,
                        colors = ButtonDefaults.buttonColors(containerColor = DentalBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (creating) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Create & Edit")
                    }
                }

                TextButton(
                    onClick = { if (!creating) onDismiss() },
                    enabled = !creating,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Cancel") }
            }
        }
    }
}
