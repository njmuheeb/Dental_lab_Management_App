package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Clinic
import com.example.data.model.StatementMath
import com.example.data.model.WorkOrder
import com.example.data.util.MoneyUtils
import com.example.export.FileExporter
import com.example.export.XlsxExporters
import com.example.ui.DentalLabViewModel
import com.example.ui.components.StatCard
import com.example.ui.components.WorkOrderTable
import com.example.ui.theme.DentalBlue
import com.example.ui.theme.Navy900
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TreeMap

/**
 * Complete clinic profile: contact details, account-level financials (total work entries,
 * units, revenue, payments received, outstanding balance), full work history table
 * (9 columns, no case-wise payment column), record clinic payment, monthly statement /
 * monthly bill entry points, and Excel/PDF exports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicDetailScreen(
    viewModel: DentalLabViewModel,
    clinic: Clinic,
    onBack: () -> Unit,
    onOpenBill: (clinicId: Long) -> Unit,
    onOpenStatement: (clinicId: Long) -> Unit,
    onNavigateToNewWork: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workOrders by viewModel.workOrders.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val patients by viewModel.patients.collectAsState()
    val labSettings by viewModel.labSettings.collectAsState()

    val clinicOrders = remember(workOrders, clinic.id) {
        workOrders.filter { it.clinicId == clinic.id }.sortedByDescending { it.entryDate }
    }
    val clinicPatients = patients.filter { it.clinicId == clinic.id }

    // Filters
    var searchQuery by remember { mutableStateOf("") }
    var selectedMonth by remember { mutableStateOf("All") } // "All" or "yyyy-MM"
    var selectedStatus by remember { mutableStateOf("All") }

    // Dialogs
    var orderToEdit by remember { mutableStateOf<WorkOrder?>(null) }
    var orderToDelete by remember { mutableStateOf<WorkOrder?>(null) }
    var showClinicPaymentDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    // Account-level financials (clinic-account model, cancelled work never billed)
    val billable = clinicOrders.filter { StatementMath.isBillable(it) }
    val totalBilled = MoneyUtils.round2(billable.sumOf { it.totalAmount })
    val totalUnits = billable.sumOf { it.units }
    val paymentsReceived = MoneyUtils.round2(
        payments.filter { it.clinicId == clinic.id }.sumOf { it.amount }
    )
    val outstanding = MoneyUtils.round2(totalBilled - paymentsReceived).coerceAtLeast(0.0)

    // Month list derived from this clinic's orders
    val monthOptions = remember(clinicOrders) {
        val cal = Calendar.getInstance()
        val map = TreeMap<String, String>()
        clinicOrders.forEach { o ->
            cal.timeInMillis = o.entryDate
            val key = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            val label = android.text.format.DateFormat.format("MMM yyyy", cal).toString()
            map[key] = label
        }
        map
    }
    val currentMonthKey = remember {
        val cal = Calendar.getInstance()
        String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    val filteredOrders = remember(clinicOrders, searchQuery, selectedMonth, selectedStatus) {
        var list = clinicOrders.asSequence()
        if (selectedMonth != "All") {
            list = list.filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.entryDate }
                String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1) == selectedMonth
            }
        }
        if (selectedStatus != "All") {
            list = list.filter { it.status == selectedStatus }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list = list.filter {
                it.jobNumber.contains(q, true) ||
                    it.patientName.contains(q, true) ||
                    it.workTypeName.contains(q, true) ||
                    it.status.contains(q, true)
            }
        }
        list.toList()
    }

    /** Full work-history Excel export (all clinic rows, no case-wise payment columns). */
    fun exportWorkHistoryExcel() {
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = XlsxExporters.workOrdersLedgerWorkbook(clinicOrders, labSettings)
                val file = FileExporter.writeExportFile(
                    context,
                    "DentalLab_${clinic.clinicCode}_WorkHistory.xlsx",
                    bytes
                )
                withContext(Dispatchers.Main) {
                    showExportOptionsDialog(
                        context = context,
                        file = file,
                        mime = FileExporter.MIME_XLSX,
                        title = "Work history exported",
                        message = "Saved ${file.name} (${file.length() / 1024} KB) - ${clinicOrders.size} work entries.",
                        viewModel = viewModel
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { viewModel.showMessage("Export failed: ${e.message}") }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(clinic.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Navy900)
                    Text(
                        "Dr. ${clinic.dentistName} • ${clinic.clinicCode}",
                        fontSize = 11.sp, color = TextSecondary
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("clinic_detail_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                TextButton(
                    onClick = { onOpenStatement(clinic.id) },
                    modifier = Modifier.testTag("open_statement_btn")
                ) {
                    Text("Statement", color = DentalBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = Navy900
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Contact card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val contactBits = listOfNotNull(
                        clinic.phone.takeIf { it.isNotBlank() },
                        clinic.email.takeIf { it.isNotBlank() },
                        "${clinic.address}, ${clinic.city}".takeIf { clinic.address.isNotBlank() || clinic.city.isNotBlank() }
                    )
                    if (contactBits.isNotEmpty()) {
                        Text(contactBits.joinToString("\n"), fontSize = 12.sp, color = TextSecondary)
                    }
                    if (clinic.notes.isNotBlank()) {
                        Text("Notes: ${clinic.notes}", fontSize = 11.sp, color = TextMuted)
                    }
                    Text(
                        "${clinicOrders.size} work entries • ${clinicPatients.size} registered patients",
                        fontSize = 11.sp, color = TextMuted
                    )
                }
            }

            // Account stats
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    title = "Work Entries", value = "${clinicOrders.size}",
                    subtitle = "$totalUnits units",
                    icon = Icons.Default.Assignment, accentColor = DentalBlue, modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Total Revenue", value = viewModel.formatCurrency(totalBilled),
                    icon = Icons.Default.Receipt, accentColor = Navy900, modifier = Modifier.weight(1.3f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    title = "Payments Received", value = viewModel.formatCurrency(paymentsReceived),
                    icon = Icons.Default.CheckCircle, accentColor = StatusGreen, modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Outstanding", value = viewModel.formatCurrency(outstanding),
                    icon = Icons.Default.Warning, accentColor = StatusRed, modifier = Modifier.weight(1f)
                )
            }

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { showClinicPaymentDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("clinic_record_payment_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Record Payment", fontSize = 11.sp)
                }
                Button(
                    onClick = { onOpenBill(clinic.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("generate_bill_btn")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Generate Bill", fontSize = 11.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onOpenStatement(clinic.id) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("View Statement", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = { exportWorkHistoryExcel() },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp), tint = StatusGreen)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export Excel", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onNavigateToNewWork,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ New Work", fontSize = 11.sp)
                }
            }

            // Filters
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search job, patient, work type...", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("clinic_detail_search")
                )

                var monthExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = monthExpanded,
                    onExpandedChange = { monthExpanded = it },
                    modifier = Modifier.width(150.dp)
                ) {
                    val selectedLabel = if (selectedMonth == "All") "All Months"
                    else monthOptions[selectedMonth] ?: selectedMonth
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Month", fontSize = 11.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                        modifier = Modifier.menuAnchor(),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("All Months") },
                            onClick = { selectedMonth = "All"; monthExpanded = false }
                        )
                        monthOptions.descendingMap().forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(if (key == currentMonthKey) "$label (current)" else label) },
                                onClick = { selectedMonth = key; monthExpanded = false }
                            )
                        }
                    }
                }
            }

            // Status quick chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("All", "In Progress", "Ready", "Delivered").forEach { f ->
                    FilterChip(
                        selected = selectedStatus == f,
                        onClick = { selectedStatus = f },
                        label = { Text(f, fontSize = 11.sp) }
                    )
                }
            }

            Text(
                "WORK HISTORY (${filteredOrders.size} entries)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Navy900
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                WorkOrderTable(
                    orders = filteredOrders,
                    formatDate = { viewModel.formatDate(it) },
                    onEdit = { orderToEdit = it },
                    onDelete = { orderToDelete = it },
                    onRowClick = { orderToEdit = it },
                    emptyMessage = if (selectedMonth == "All") "No work orders for this clinic yet"
                    else "No work orders in the selected month"
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // ---- Dialogs ----
    if (orderToEdit != null) {
        EditWorkOrderDialog(
            order = orderToEdit!!,
            viewModel = viewModel,
            onDismiss = { orderToEdit = null }
        )
    }

    if (orderToDelete != null) {
        val order = orderToDelete!!
        AlertDialog(
            onDismissRequest = { orderToDelete = null },
            title = { Text("Delete ${order.jobNumber}?") },
            text = { Text("This permanently removes the work entry (clinic payments are account-level and stay unchanged).") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteWorkOrder(order); orderToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { orderToDelete = null }) { Text("Cancel") } }
        )
    }

    if (showClinicPaymentDialog) {
        RecordClinicPaymentDialog(
            viewModel = viewModel,
            onDismiss = { showClinicPaymentDialog = false },
            fixedClinic = clinic
        )
    }
}
