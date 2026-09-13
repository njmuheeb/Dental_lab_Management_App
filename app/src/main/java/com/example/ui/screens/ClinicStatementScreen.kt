package com.example.ui.screens

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClinicStatement
import com.example.data.model.StatementMath
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
import java.io.File
import java.util.Calendar
import java.util.TimeZone

private val STATEMENT_MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private val STATUS_FILTERS = listOf(
    "All", "Received", "Pending", "In Progress", "Ready", "Completed", "Delivered", "Cancelled"
)

private enum class StatementPeriod { ALL_TIME, MONTHLY, YEARLY, CUSTOM }

/**
 * CLINIC STATEMENT - the complete, filterable work history of a clinic. Deliberately
 * different from the Monthly Bill: the statement can cover ALL work ever recorded
 * (All Time), a calendar month, a full year, or a custom date range, with an optional
 * work-status filter for the table. Exports to professional PDF and Excel.
 *
 * Accounting (always for the SELECTED period, from real stored data):
 *   Closing Outstanding = Previous Outstanding + Period Revenue - Payments Received
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicStatementScreen(
    viewModel: DentalLabViewModel,
    clinicId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val labSettings by viewModel.labSettings.collectAsState()
    val clinics by viewModel.clinics.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val workOrders by viewModel.workOrders.collectAsState()

    val now = Calendar.getInstance()
    var selectedClinicId by remember { mutableStateOf(clinicId) }
    var period by remember { mutableStateOf(StatementPeriod.ALL_TIME) }
    var selectedYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }
    var customStart by remember { mutableStateOf(StatementMath.monthStart(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)) }
    var customEnd by remember { mutableStateOf(StatementMath.endOfDay(System.currentTimeMillis())) }
    var statusFilter by remember { mutableStateOf("All") }

    var statement by remember { mutableStateOf<ClinicStatement?>(null) }
    var loading by remember { mutableStateOf(true) }
    var exportBusy by remember { mutableStateOf(false) }

    val clinic = clinics.find { it.id == selectedClinicId }

    // Compute the selected [start, endExclusive) range
    val rangeStart = when (period) {
        StatementPeriod.ALL_TIME -> 0L
        StatementPeriod.MONTHLY -> StatementMath.monthStart(selectedYear, selectedMonth)
        StatementPeriod.YEARLY -> StatementMath.yearStart(selectedYear)
        StatementPeriod.CUSTOM -> customStart
    }
    val rangeEnd = when (period) {
        StatementPeriod.ALL_TIME -> Long.MAX_VALUE
        StatementPeriod.MONTHLY -> StatementMath.monthEndExclusive(selectedYear, selectedMonth)
        StatementPeriod.YEARLY -> StatementMath.yearEndExclusive(selectedYear)
        StatementPeriod.CUSTOM -> customEnd
    }

    fun refresh() {
        val targetClinic = selectedClinicId
        val start = rangeStart
        val end = rangeEnd
        scope.launch {
            loading = true
            statement = withContext(Dispatchers.IO) {
                viewModel.getClinicStatementForRange(targetClinic, start, end)
            }
            loading = false
        }
    }

    LaunchedEffect(selectedClinicId, rangeStart, rangeEnd, payments.size, workOrders.size) { refresh() }
    BackHandler { onBack() }

    fun doExport(kind: String) {
        val st = statement ?: return
        val settings = labSettings
        exportBusy = true
        scope.launch(Dispatchers.IO) {
            try {
                when (kind) {
                    "pdf" -> {
                        val bytes = PdfExporter.buildClinicDocument(st, settings, PdfExporter.Mode.STATEMENT)
                        val name = "NazneenLab_Statement_${st.clinic.clinicCode}_${fileSafePeriod(st)}.pdf"
                        val file = FileExporter.writeExportFile(context, name, bytes)
                        withContext(Dispatchers.Main) {
                            showExportOptionsDialog(
                                context = context, file = file, mime = FileExporter.MIME_PDF,
                                title = "Statement PDF ready",
                                message = "Saved ${file.name} (${file.length() / 1024} KB) - ${st.totalEntries} entries, ${st.periodLabel}.",
                                viewModel = viewModel
                            )
                        }
                    }
                    "xlsx" -> {
                        val bytes = XlsxExporters.statementWorkbook(st, settings, isBill = false)
                        val name = "NazneenLab_Statement_${st.clinic.clinicCode}_${fileSafePeriod(st)}.xlsx"
                        val file = FileExporter.writeExportFile(context, name, bytes)
                        withContext(Dispatchers.Main) {
                            showExportOptionsDialog(
                                context = context, file = file, mime = FileExporter.MIME_XLSX,
                                title = "Statement workbook ready",
                                message = "Saved ${file.name} - 3 sheets (summary, work details, payments).",
                                viewModel = viewModel
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
                        Text("Clinic Statement", fontWeight = FontWeight.Bold, color = Navy900)
                        Text(clinic?.name ?: "Clinic", fontSize = 11.sp, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("statement_back_btn")) {
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
                // Clinic selector + period filters
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Navy900),
                    modifier = Modifier.fillMaxWidth().testTag("statement_selectors_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Clinic selector
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

                        // Period mode chips
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = DentalBlueLight)
                            listOf(
                                "All Time" to StatementPeriod.ALL_TIME,
                                "Monthly" to StatementPeriod.MONTHLY,
                                "Yearly" to StatementPeriod.YEARLY,
                                "Custom Range" to StatementPeriod.CUSTOM
                            ).forEach { (label, mode) ->
                                FilterChip(
                                    selected = period == mode,
                                    onClick = { period = mode },
                                    label = { Text(label, fontSize = 11.sp, color = Color.White) },
                                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                        containerColor = Color.Transparent,
                                        labelColor = Color.White,
                                        selectedContainerColor = DentalBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        // Period-specific selectors
                        when (period) {
                            StatementPeriod.MONTHLY -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    var monthExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = monthExpanded, onExpandedChange = { monthExpanded = it },
                                        modifier = Modifier.weight(1.4f)
                                    ) {
                                        OutlinedTextField(
                                            value = STATEMENT_MONTHS[selectedMonth - 1], onValueChange = {}, readOnly = true,
                                            label = { Text("Month", color = TextSecondary) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                                            modifier = Modifier.menuAnchor(),
                                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                                        )
                                        ExposedDropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                                            STATEMENT_MONTHS.forEachIndexed { idx, m ->
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
                                            (currentYear - 6..currentYear + 1).toList()
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
                            StatementPeriod.YEARLY -> {
                                var yearExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = yearExpanded, onExpandedChange = { yearExpanded = it },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val years = remember {
                                        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                                        (currentYear - 6..currentYear + 1).toList()
                                    }
                                    OutlinedTextField(
                                        value = selectedYear.toString(), onValueChange = {}, readOnly = true,
                                        label = { Text("Year", color = TextSecondary) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
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
                            StatementPeriod.CUSTOM -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DateFieldButton(
                                        label = "From",
                                        millis = customStart,
                                        modifier = Modifier.weight(1f)
                                    ) { customStart = it }
                                    DateFieldButton(
                                        label = "To",
                                        millis = customEnd,
                                        modifier = Modifier.weight(1f)
                                    ) { customEnd = StatementMath.endOfDay(it) }
                                }
                            }
                            StatementPeriod.ALL_TIME -> Unit
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

                    // Status filter for the work table
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "FILTER BY STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        items(STATUS_FILTERS) { status ->
                            FilterChip(
                                selected = statusFilter == status,
                                onClick = { statusFilter = status },
                                label = { Text(status, fontSize = 11.sp) },
                                modifier = Modifier.testTag("statement_status_filter_$status")
                            )
                        }
                    }

                    val filteredOrders = remember(st.orders, statusFilter) {
                        if (statusFilter == "All") st.orders
                        else st.orders.filter { it.status == statusFilter }
                    }
                    // Work summary follows the visible (filtered) rows
                    val filteredUnits = filteredOrders.sumOf { it.units }
                    val filteredRevenue = filteredOrders.sumOf { it.totalAmount }

                    // Statement meta
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(st.statementNumber, fontWeight = FontWeight.Bold, color = Navy900)
                            Text(
                                "Issue date: ${viewModel.formatDate(st.statementDate)} • Period: ${st.periodLabel}",
                                fontSize = 11.sp, color = TextSecondary
                            )
                            Text(
                                "${st.clinic.name} • Dr. ${st.clinic.dentistName} • ${st.clinic.city}",
                                fontSize = 11.sp, color = TextSecondary
                            )
                        }
                    }

                    // Work summary (matches the filtered table)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatSmall("Total Entries", "${filteredOrders.size}", DentalBlue, Modifier.weight(1f))
                        StatSmall("Total Units", "$filteredUnits", DentalBlue, Modifier.weight(1f))
                        StatSmall(
                            "Total Revenue", viewModel.formatCurrency(filteredRevenue), DentalBlue, Modifier.weight(1.4f)
                        )
                    }

                    // Account summary (period accounting - independent of status filter)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth().testTag("statement_summary_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "ACCOUNT SUMMARY - ${st.periodLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = Navy900
                            )
                            StatementSummaryRow("Previous Outstanding Balance (brought forward)", st.previousBalance, StatusOrange)
                            StatementSummaryRow("Revenue for selected period (${st.totalEntries} entries)", st.totalRevenue, DentalBlue)
                            StatementSummaryRow("Payments Received (${st.payments.size} receipts)", st.paymentsReceived, StatusGreen)
                            androidx.compose.material3.HorizontalDivider(color = TextMuted.copy(alpha = 0.3f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("REMAINING BALANCE", fontWeight = FontWeight.Bold, color = StatusRed)
                                Text(
                                    viewModel.formatCurrency(st.remainingBalance),
                                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = StatusRed
                                )
                            }
                            Text(
                                "Remaining Balance = Previous Outstanding + Period Revenue − Payments Received",
                                fontSize = 10.sp, color = TextMuted
                            )
                        }
                    }

                    // Exports
                    Text("EXPORT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Navy900)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { doExport("pdf") },
                            colors = ButtonDefaults.buttonColors(containerColor = DentalBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("export_statement_pdf_btn"),
                            enabled = !exportBusy
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Statement PDF", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { doExport("xlsx") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            enabled = !exportBusy
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusGreen)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel XLSX", fontSize = 11.sp)
                        }
                    }

                    // Work table
                    Text(
                        "WORK HISTORY (${filteredOrders.size} entries${if (statusFilter != "All") ", status: $statusFilter" else ""})",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Navy900
                    )
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        com.example.ui.components.WorkOrderTable(
                            orders = filteredOrders,
                            formatDate = { viewModel.formatDate(it) },
                            showActions = false,
                            emptyMessage = when {
                                period == StatementPeriod.ALL_TIME -> "No work recorded for this clinic yet"
                                else -> "No work entries in ${st.periodLabel}" +
                                    (if (statusFilter != "All") " with status $statusFilter" else "")
                            }
                        )
                    }

                    // Payments received in period
                    if (st.payments.isNotEmpty()) {
                        Text(
                            "PAYMENTS RECEIVED (${st.payments.size})",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Navy900
                        )
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                st.payments.forEach { p ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${viewModel.formatDate(p.paymentDate)} • ${p.paymentMethod}" +
                                                (if (p.referenceNumber.isNotBlank()) " • ${p.referenceNumber}" else ""),
                                            fontSize = 11.sp, color = TextSecondary
                                        )
                                        Text(
                                            viewModel.formatCurrency(p.amount),
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }
}

/** "From"/"To" date buttons opening the Material3 date picker (UTC->local noon). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFieldButton(
    label: String,
    millis: Long,
    modifier: Modifier = Modifier,
    onPicked: (Long) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val dateFmt = remember { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()) }

    OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("$label: ${dateFmt.format(java.util.Date(millis))}", fontSize = 11.sp)
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utc ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        onPicked(
                            Calendar.getInstance().apply {
                                set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                        )
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun fileSafePeriod(st: ClinicStatement): String =
    st.periodLabel.replace(" ", "").replace(",", "")

/** Non-blocking export options: share, print (PDF), save to Downloads. */
fun showExportOptionsDialog(
    context: Context,
    file: File,
    mime: String,
    title: String,
    message: String,
    viewModel: DentalLabViewModel
) {
    android.app.AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("Share") { _, _ ->
            FileExporter.shareFile(context, file, mime, title)
        }
        .setNeutralButton(if (mime == FileExporter.MIME_PDF) "Print" else "Save to Downloads") { _, _ ->
            if (mime == FileExporter.MIME_PDF) {
                FileExporter.printPdf(context, file, file.name)
            } else {
                val ok = FileExporter.saveToDownloads(context, file, mime, file.name)
                viewModel.showMessage(if (ok) "Saved to Downloads" else "Could not save to Downloads - use Share instead")
            }
        }
        .setNegativeButton(
            if (mime == FileExporter.MIME_PDF) "Save to Downloads" else "Close"
        ) { _, _ ->
            if (mime == FileExporter.MIME_PDF) {
                val ok = FileExporter.saveToDownloads(context, file, mime, file.name)
                viewModel.showMessage(if (ok) "Saved to Downloads" else "Could not save to Downloads - use Share instead")
            }
        }
        .show()
}

@Composable
private fun StatSmall(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
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
private fun StatementSummaryRow(label: String, amount: Double, color: Color) {
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
