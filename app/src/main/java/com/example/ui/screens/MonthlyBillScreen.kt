package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClinicStatement
import com.example.export.CsvExporters
import com.example.export.FileExporter
import com.example.export.PdfExporter
import com.example.export.XlsxExporters
import com.example.ui.DentalLabViewModel
import com.example.ui.theme.DentalBlue
import com.example.ui.theme.DentalBlueLight
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
import java.util.Calendar

private val BILL_MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

/**
 * MONTHLY BILL / INVOICE for one clinic and one selected month.
 *
 * Deliberately different from the Clinic Statement: the bill contains ONLY the work entries
 * of the selected billing period, and serves as the invoice document (BILL-<code>-YYYYMM).
 * Includes the payment summary (previous outstanding + month revenue - payments received =
 * closing outstanding) and exports to PDF (print/share), Excel and CSV. A clinic-level
 * payment can be recorded directly from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyBillScreen(
    viewModel: DentalLabViewModel,
    clinicId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val labSettings by viewModel.labSettings.collectAsState()
    val clinics by viewModel.clinics.collectAsState()
    val payments by viewModel.payments.collectAsState()

    val now = Calendar.getInstance()
    var selectedClinicId by remember { mutableStateOf(clinicId) }
    var selectedYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }

    var statement by remember { mutableStateOf<ClinicStatement?>(null) }
    var loading by remember { mutableStateOf(true) }
    var exportBusy by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }

    val clinic = clinics.find { it.id == selectedClinicId }

    fun refresh() {
        val targetClinic = selectedClinicId
        val year = selectedYear
        val month = selectedMonth
        scope.launch {
            loading = true
            statement = withContext(Dispatchers.IO) {
                viewModel.getClinicMonthlyStatement(targetClinic, year, month)
            }
            loading = false
        }
    }

    // Reload on selection change or when a payment is recorded/deleted
    LaunchedEffect(selectedClinicId, selectedYear, selectedMonth, payments.size) { refresh() }
    BackHandler { onBack() }

    fun shiftMonth(delta: Int) {
        var m = selectedMonth + delta
        var y = selectedYear
        if (m < 1) { m = 12; y-- }
        if (m > 12) { m = 1; y++ }
        selectedMonth = m
        selectedYear = y
    }

    fun doExport(kind: String) {
        val st = statement ?: return
        val settings = labSettings
        exportBusy = true
        scope.launch(Dispatchers.IO) {
            try {
                when (kind) {
                    "pdf" -> {
                        val bytes = PdfExporter.buildClinicDocument(st, settings, PdfExporter.Mode.BILL)
                        val name = "DentalLab_Bill_${st.clinic.clinicCode}_${selectedYear}-${"%02d".format(selectedMonth)}.pdf"
                        val file = FileExporter.writeExportFile(context, name, bytes)
                        withContext(Dispatchers.Main) {
                            showExportOptionsDialog(
                                context = context,
                                file = file,
                                mime = FileExporter.MIME_PDF,
                                title = "Monthly Bill PDF ready",
                                message = "Saved ${file.name} (${file.length() / 1024} KB).",
                                viewModel = viewModel
                            )
                        }
                    }
                    "xlsx" -> {
                        val bytes = XlsxExporters.statementWorkbook(st, settings, isBill = true)
                        val name = "DentalLab_Bill_${st.clinic.clinicCode}_${selectedYear}-${"%02d".format(selectedMonth)}.xlsx"
                        val file = FileExporter.writeExportFile(context, name, bytes)
                        withContext(Dispatchers.Main) {
                            showExportOptionsDialog(
                                context = context, file = file, mime = FileExporter.MIME_XLSX,
                                title = "Bill workbook ready", message = "Saved ${file.name} - 3 sheets (summary, work details, payments).", viewModel = viewModel
                            )
                        }
                    }
                    "csv" -> {
                        val csv = CsvExporters.clinicStatementCsv(st)
                        val name = "DentalLab_Bill_${st.clinic.clinicCode}_${selectedYear}-${"%02d".format(selectedMonth)}.csv"
                        val file = FileExporter.writeExportFile(context, name, csv.toByteArray())
                        withContext(Dispatchers.Main) {
                            showExportOptionsDialog(
                                context = context, file = file, mime = FileExporter.MIME_CSV,
                                title = "Bill CSV ready", message = "Saved as ${file.name}", viewModel = viewModel
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { viewModel.showMessage("Export failed: ${e.message}") }
            } finally {
                withContext(Dispatchers.Main) { exportBusy = false }
            }
        }
    }

    // Opaque Surface so nothing shows through from the previous screen
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text("Monthly Bill / Invoice", fontWeight = FontWeight.Bold, color = Navy900)
                        Text(clinic?.name ?: "Clinic", fontSize = 11.sp, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("bill_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                // Clinic + month selectors
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Navy900),
                    modifier = Modifier.fillMaxWidth().testTag("bill_selectors_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        var clinicExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = clinicExpanded,
                            onExpandedChange = { clinicExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = clinic?.name ?: "Select clinic",
                                onValueChange = {}, readOnly = true,
                                label = { Text("Clinic", color = TextSecondary) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clinicExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                            )
                            ExposedDropdownMenu(expanded = clinicExpanded, onDismissRequest = { clinicExpanded = false }) {
                                clinics.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text("${c.name} (${c.clinicCode})") },
                                        onClick = { selectedClinicId = c.id; clinicExpanded = false }
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = DentalBlueLight)
                            Text(
                                "${BILL_MONTHS[selectedMonth - 1]} $selectedYear",
                                color = Color.White, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { shiftMonth(-1) }) { Text("◀", color = DentalBlueLight) }
                            OutlinedButton(onClick = { shiftMonth(1) }) { Text("▶", color = DentalBlueLight) }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var monthExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = monthExpanded, onExpandedChange = { monthExpanded = it },
                                modifier = Modifier.weight(1.4f)
                            ) {
                                OutlinedTextField(
                                    value = BILL_MONTHS[selectedMonth - 1], onValueChange = {}, readOnly = true,
                                    label = { Text("Month", color = TextSecondary) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                                    modifier = Modifier.menuAnchor(),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                                )
                                ExposedDropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                                    BILL_MONTHS.forEachIndexed { idx, m ->
                                        DropdownMenuItem(
                                            text = { Text(m) },
                                            onClick = { selectedMonth = idx + 1; monthExpanded = false }
                                        )
                                    }
                                }
                            }
                            var yearExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = yearExpanded, onExpandedChange = { yearExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                val years = remember {
                                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                                    (currentYear - 5..currentYear + 1).toList()
                                }
                                OutlinedTextField(
                                    value = selectedYear.toString(), onValueChange = {}, readOnly = true,
                                    label = { Text("Year", color = TextSecondary) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                                    modifier = Modifier.menuAnchor(),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                                )
                                ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                                    years.forEach { y ->
                                        DropdownMenuItem(
                                            text = { Text(y.toString()) },
                                            onClick = { selectedYear = y; yearExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (statement == null) {
                    Text("Clinic not found", color = TextMuted)
                } else {
                    val st = statement!!

                    // Bill meta
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "BILL ${st.statementNumber.replace("ST-", "BILL-")}",
                                fontWeight = FontWeight.Bold, color = Navy900
                            )
                            Text(
                                "Issue date: ${viewModel.formatDate(st.statementDate)} • Billing period: ${
                                    viewModel.formatDate(st.periodStart)
                                } to ${viewModel.formatDate(st.periodEndExclusive - 1)}",
                                fontSize = 11.sp, color = TextSecondary
                            )
                            Text(
                                "${st.clinic.name} • Dr. ${st.clinic.dentistName} • ${st.clinic.city}",
                                fontSize = 11.sp, color = TextSecondary
                            )
                        }
                    }

                    // Summary cards
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        BillSummaryStat("Work Entries", "${st.totalEntries}", DentalBlue, Modifier.weight(1f))
                        BillSummaryStat("Total Units", "${st.totalUnits}", DentalBlue, Modifier.weight(1f))
                        BillSummaryStat(
                            "Month Revenue", viewModel.formatCurrency(st.totalRevenue), DentalBlue, Modifier.weight(1.4f)
                        )
                    }

                    // Payment summary card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth().testTag("bill_summary_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "PAYMENT SUMMARY - ${st.periodLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = Navy900
                            )
                            BillSummaryRow("Previous Outstanding Balance (brought forward)", st.previousBalance, StatusOrange)
                            BillSummaryRow("Current Month Billing (${st.totalEntries} works, ${st.totalUnits} units)", st.totalRevenue, DentalBlue)
                            BillSummaryRow("Payments Received (${st.payments.size} receipts)", st.paymentsReceived, StatusGreen)
                            androidx.compose.material3.HorizontalDivider(color = TextMuted.copy(alpha = 0.3f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("REMAINING BALANCE DUE", fontWeight = FontWeight.Bold, color = StatusRed)
                                Text(
                                    viewModel.formatCurrency(st.remainingBalance),
                                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = StatusRed
                                )
                            }
                            Text(
                                "Remaining Balance = Previous Outstanding + Month Billing − Payments Received",
                                fontSize = 10.sp, color = TextMuted
                            )
                        }
                    }

                    // Record payment + exports
                    Text("PAYMENT & EXPORT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Navy900)
                    Button(
                        onClick = { showPaymentDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("bill_record_payment_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Record Clinic Payment for ${st.periodLabel}", fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { doExport("pdf") },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("export_bill_pdf_btn"),
                            enabled = !exportBusy
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bill PDF", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { doExport("xlsx") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            enabled = !exportBusy
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusGreen)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { doExport("csv") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(0.7f),
                            enabled = !exportBusy
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CSV", fontSize = 11.sp)
                        }
                    }

                    // Work entries of the billing period
                    Text(
                        "WORK ENTRIES - ${st.periodLabel} (${st.orders.size})",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Navy900
                    )
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        com.example.ui.components.WorkOrderTable(
                            orders = st.orders,
                            formatDate = { viewModel.formatDate(it) },
                            showActions = false,
                            emptyMessage = "No work entries in ${st.periodLabel}"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }

    if (showPaymentDialog && clinic != null) {
        RecordClinicPaymentDialog(
            viewModel = viewModel,
            onDismiss = { showPaymentDialog = false },
            fixedClinic = clinic
        )
    }
}

@Composable
private fun BillSummaryStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color)
        }
    }
}

@Composable
private fun BillSummaryRow(label: String, amount: Double, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = TextPrimary)
        Text(
            com.example.data.util.MoneyUtils.formatINR(amount),
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color
        )
    }
}
