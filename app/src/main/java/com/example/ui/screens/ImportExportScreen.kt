package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Clinic
import com.example.data.model.WorkOrder
import com.example.export.CsvExporters
import com.example.export.FileExporter
import com.example.export.XlsxExporters
import com.example.ui.DentalLabViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    viewModel: DentalLabViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val workOrders by viewModel.workOrders.collectAsState()
    val clinics by viewModel.clinics.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val labSettings by viewModel.labSettings.collectAsState()

    var exportResultTitle by remember { mutableStateOf("") }
    var exportResultContent by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }

    var importDataInput by remember { mutableStateOf("") }
    var importType by remember { mutableStateOf("Clinics") } // "Clinics", "Work Orders"
    var importStatusMessage by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        viewModel.showMessage("Copied $label to clipboard!")
    }

    /** Generates an XLSX file in background, then offers share / save-to-Downloads. */
    fun exportXlsx(fileName: String, build: () -> ByteArray) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val file = FileExporter.writeExportFile(context, fileName, build())
                withContext(Dispatchers.Main) {
                    showExportOptionsDialog(
                        context = context,
                        file = file,
                        mime = FileExporter.MIME_XLSX,
                        title = "Excel workbook ready",
                        message = "Saved ${file.name} (${file.length() / 1024} KB). Share or save it to Downloads.",
                        viewModel = viewModel
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { viewModel.showMessage("XLSX export failed: ${e.message}") }
            }
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("import_export_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
        ) {
            // Header Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Navy900),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Excel & CSV Data Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Export production ledgers, billing statements, and import batch data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DentalBlueLight
                        )
                    }
                }
            }

            // Export Section
            item {
                Text(
                    text = "EXPORT DATA (CSV / SPREADSHEET FORMAT)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Navy800
                )
            }

            // Export Work Orders
            item {
                ExportOptionCard(
                    title = "Work Orders Production Ledger",
                    subtitle = "All ${workOrders.size} orders with Job #, Clinic, Patient, Teeth, Units, Rates, and Balance Due",
                    icon = Icons.Default.Assignment,
                    onExportCsv = {
                        val csv = CsvExporters.workOrdersLedgerCsv(workOrders)
                        exportResultTitle = "Work Orders Export (CSV)"
                        exportResultContent = csv
                        showExportDialog = true
                    },
                    onExportXlsx = {
                        exportXlsx("NazneenLab_WorkOrders_Ledger.xlsx") {
                            XlsxExporters.workOrdersLedgerWorkbook(workOrders, labSettings)
                        }
                    }
                )
            }

            // Export Clinics Ledger & Statement
            item {
                ExportOptionCard(
                    title = "Clinic Directory & Outstanding Balances",
                    subtitle = "Contact details, active status, total volume, and current due amounts (${clinics.size} clinics)",
                    icon = Icons.Default.LocalHospital,
                    onExportCsv = {
                        val csv = CsvExporters.clinicDirectoryCsv(clinics, workOrders, payments)
                        exportResultTitle = "Clinic Balances Export (CSV)"
                        exportResultContent = csv
                        showExportDialog = true
                    },
                    onExportXlsx = {
                        exportXlsx("NazneenLab_Clinic_Directory.xlsx") {
                            XlsxExporters.clinicDirectoryWorkbook(clinics, workOrders, payments)
                        }
                    }
                )
            }

            // Export Payment Transactions
            item {
                ExportOptionCard(
                    title = "Payments & Receipts Ledger",
                    subtitle = "Complete cash & digital transaction ledger with reference IDs (${payments.size} receipts)",
                    icon = Icons.Default.Receipt,
                    onExportCsv = {
                        val csv = CsvExporters.paymentsLedgerCsv(payments, clinics)
                        exportResultTitle = "Payment Receipts Export (CSV)"
                        exportResultContent = csv
                        showExportDialog = true
                    },
                    onExportXlsx = {
                        exportXlsx("NazneenLab_Payments_Ledger.xlsx") {
                            XlsxExporters.paymentsLedgerWorkbook(payments, clinics)
                        }
                    }
                )
            }

            // Import Section
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "IMPORT BATCH DATA (CSV)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Navy800
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Paste CSV Data into text field or load template:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                        // Import Target Type
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = importType == "Clinics",
                                onClick = {
                                    importType = "Clinics"
                                    importDataInput = "Clinic Code,Name,Dentist,Phone,City\nCLN-901,Dr. Mehta Dental Clinic,Dr. Aarav Mehta,+91 99123 45678,Mumbai\nCLN-902,Metro Smile Studio,Dr. Ritu Saxena,+91 98987 65432,Pune"
                                },
                                label = { Text("Clinics CSV") }
                            )
                            FilterChip(
                                selected = importType == "Work Orders",
                                onClick = {
                                    importType = "Work Orders"
                                    importDataInput = "Job Number,Patient Name,Work Type,Units,Rate,Shade\nNDL-2026-0010,Sanjay Patel,Zirconia Crown (Monolithic),2,2800,A2\nNDL-2026-0011,Kavita Sen,Complete Denture (Lucitone 199),1,14000,A3"
                                },
                                label = { Text("Work Orders CSV") }
                            )
                        }

                        OutlinedTextField(
                            value = importDataInput,
                            onValueChange = { importDataInput = it },
                            label = { Text("CSV Records (Header + Comma-separated rows)") },
                            modifier = Modifier.fillMaxWidth().testTag("import_csv_textarea"),
                            minLines = 4,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )

                        if (importStatusMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEFF6FF),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = importStatusMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DentalBlue,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val lines = importDataInput.trim().split("\n").filter { it.isNotBlank() }
                                if (lines.size <= 1) {
                                    importStatusMessage = "CSV contains no data rows to import."
                                    return@Button
                                }

                                coroutineScope.launch {
                                    var importedCount = 0
                                    var errorCount = 0

                                    if (importType == "Clinics") {
                                        // Skip header
                                        lines.drop(1).forEach { line ->
                                            val parts = line.split(",").map { it.trim().removeSurrounding("\"") }
                                            if (parts.size >= 3) {
                                                val code = parts.getOrNull(0)?.ifBlank { "CLN-${(100..999).random()}" } ?: "CLN-100"
                                                val name = parts.getOrNull(1) ?: "Clinic"
                                                val dentist = parts.getOrNull(2) ?: "Doctor"
                                                val phone = parts.getOrNull(3) ?: ""
                                                val city = parts.getOrNull(4) ?: "Mumbai"
                                                viewModel.repository.insertClinic(
                                                    Clinic(
                                                        clinicCode = code,
                                                        name = name,
                                                        dentistName = dentist,
                                                        phone = phone,
                                                        city = city
                                                    )
                                                )
                                                importedCount++
                                            } else {
                                                errorCount++
                                            }
                                        }
                                        importStatusMessage = "Import Finished: $importedCount clinic(s) successfully added, $errorCount row(s) skipped."
                                    } else {
                                        val firstClinic = clinics.firstOrNull()
                                        if (firstClinic == null) {
                                            importStatusMessage = "Please register at least one clinic before importing orders."
                                            return@launch
                                        }
                                        lines.drop(1).forEach { line ->
                                            val parts = line.split(",").map { it.trim().removeSurrounding("\"") }
                                            if (parts.size >= 3) {
                                                val job = parts.getOrNull(0)?.ifBlank { viewModel.repository.generateNextJobNumber() } ?: "NDL-2026-9999"
                                                val pat = parts.getOrNull(1) ?: "Walk-in Patient"
                                                val wtName = parts.getOrNull(2) ?: "Zirconia Crown"
                                                val u = parts.getOrNull(3)?.toIntOrNull() ?: 1
                                                val r = parts.getOrNull(4)?.toDoubleOrNull() ?: 2500.0
                                                val s = parts.getOrNull(5) ?: "A2"
                                                viewModel.createWorkOrder(
                                                    WorkOrder(
                                                        jobNumber = job,
                                                        clinicId = firstClinic.id,
                                                        clinicName = firstClinic.name,
                                                        dentistName = firstClinic.dentistName,
                                                        patientId = 1,
                                                        patientName = pat,
                                                        workTypeId = 1,
                                                        workTypeName = wtName,
                                                        pricingModel = if (u > 1) "UNIT_BASED" else "FIXED_PRICE",
                                                        units = u,
                                                        rate = r,
                                                        totalAmount = u * r,
                                                        shade = s
                                                    )
                                                )
                                                importedCount++
                                            } else {
                                                errorCount++
                                            }
                                        }
                                        importStatusMessage = "Import Finished: $importedCount work order(s) successfully created, $errorCount row(s) skipped."
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DentalBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_execute_import")
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Validate & Import CSV Records")
                        }
                    }
                }
            }
        }
    }

    // Export View Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(exportResultTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Formatted CSV preview ready for Excel / Google Sheets:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    OutlinedTextField(
                        value = exportResultContent,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        copyToClipboard(exportResultContent, exportResultTitle)
                        showExportDialog = false
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun ExportOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onExportCsv: () -> Unit,
    onExportXlsx: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .background(DentalBlue.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = DentalBlue, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontSize = 11.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onExportXlsx != null) {
                    Button(
                        onClick = onExportXlsx,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("XLSX", fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = onExportCsv,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("CSV", fontSize = 12.sp)
                }
            }
        }
    }
}
