package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.example.data.model.LabSettings
import com.example.ui.DentalLabViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DentalLabViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val labSettings by viewModel.labSettings.collectAsState()
    val clinics by viewModel.clinics.collectAsState()
    val patients by viewModel.patients.collectAsState()
    val workTypes by viewModel.workTypes.collectAsState()
    val workOrders by viewModel.workOrders.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val pendingSync by viewModel.pendingSyncCount.collectAsState()

    var labName by remember(labSettings) { mutableStateOf(labSettings.labName) }
    var phone by remember(labSettings) { mutableStateOf(labSettings.phone) }
    var email by remember(labSettings) { mutableStateOf(labSettings.email) }
    var address by remember(labSettings) { mutableStateOf(labSettings.address) }
    var city by remember(labSettings) { mutableStateOf(labSettings.city) }
    var turnaroundDaysText by remember(labSettings) { mutableStateOf(labSettings.defaultTurnaroundDays.toString()) }
    var currencySymbol by remember(labSettings) { mutableStateOf(labSettings.currencySymbol) }

    // Sync settings local state
    var syncEnabled by remember(labSettings) { mutableStateOf(labSettings.syncEnabled) }
    var syncUrl by remember(labSettings) { mutableStateOf(labSettings.syncUrl) }
    var syncSecret by remember(labSettings) { mutableStateOf(labSettings.syncSecret) }

    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var backupJsonString by remember { mutableStateOf("") }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("settings_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
        ) {
            // Lab Profile Section
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
                        Text(
                            text = "LABORATORY IDENTITY & PROFILE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Navy800
                        )

                        OutlinedTextField(
                            value = labName,
                            onValueChange = { labName = it },
                            label = { Text("Laboratory Name") },
                            modifier = Modifier.fillMaxWidth().testTag("lab_name_input"),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Phone / WhatsApp") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email Address") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("Address") },
                                modifier = Modifier.weight(1.3f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = city,
                                onValueChange = { city = it },
                                label = { Text("City") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = turnaroundDaysText,
                                onValueChange = { turnaroundDaysText = it },
                                label = { Text("Default Turnaround (Days)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = currencySymbol,
                                onValueChange = { currencySymbol = it },
                                label = { Text("Currency Symbol") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.saveSettings(
                                    labSettings.copy(
                                        labName = labName.trim(),
                                        phone = phone.trim(),
                                        email = email.trim(),
                                        address = address.trim(),
                                        city = city.trim(),
                                        defaultTurnaroundDays = turnaroundDaysText.toIntOrNull() ?: 5,
                                        currencySymbol = currencySymbol.trim()
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DentalBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("save_settings_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Lab Settings")
                        }
                    }
                }
            }

            // Optional Google Sheets Sync (offline-first: disabled by default)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth().testTag("sync_settings_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GOOGLE SHEETS SYNC (OPTIONAL)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Navy800
                                )
                                Text(
                                    text = "The app is fully offline-first. Enable to mirror data to your own Google Sheet via an Apps Script Web App.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = syncEnabled,
                                onCheckedChange = { syncEnabled = it },
                                modifier = Modifier.testTag("sync_enabled_switch")
                            )
                        }

                        if (syncEnabled) {
                            OutlinedTextField(
                                value = syncUrl,
                                onValueChange = { syncUrl = it },
                                label = { Text("Apps Script Web App URL") },
                                placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = syncSecret,
                                onValueChange = { syncSecret = it },
                                label = { Text("Shared Secret (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Text(
                                text = "Setup guide: docs/GOOGLE_SHEETS_SYNC_SETUP.md in the project. Keys stay on your device - nothing is compiled into the APK.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = TextMuted
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.saveSyncSettings(syncEnabled, syncUrl, syncSecret)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.syncNow() },
                                    modifier = Modifier.weight(1f).testTag("sync_now_btn"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync Now", fontSize = 12.sp)
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.fullPush() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Full Push (re-send all data)", fontSize = 12.sp)
                            }

                            Text(
                                text = buildString {
                                    append("Pending changes: $pendingSync")
                                    if (labSettings.lastSyncAt > 0) {
                                        append(" • Last sync: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(labSettings.lastSyncAt))}")
                                    } else {
                                        append(" • Never synced")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = if (pendingSync > 0) StatusOrange else StatusGreen
                            )
                        } else if (labSettings.syncEnabled) {
                            // Turning off: persist immediately (keep URL/secret for later re-enable)
                            LaunchedEffect(Unit) {
                                viewModel.saveSyncSettings(false, labSettings.syncUrl, labSettings.syncSecret)
                            }
                        }
                    }
                }
            }

            // Database Statistics
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "OFFLINE DATABASE STATISTICS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Navy800
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatMiniItem("Work Orders", "${workOrders.size}")
                            StatMiniItem("Clinics", "${clinics.size}")
                            StatMiniItem("Patients", "${patients.size}")
                            StatMiniItem("Work Types", "${workTypes.size}")
                            StatMiniItem("Receipts", "${payments.size}")
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = OutlineLight.copy(alpha = 0.5f))

                        Text(
                            text = "Database Engine: Room SQLite 3 (Offline-First, Zero Latency)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Backup & Restore
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
                        Text(
                            text = "DATA SAFETY & BACKUP",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Navy800
                        )

                        Text(
                            text = "Generate a full point-in-time JSON snapshot backup of your clinics, rates, work orders, and payment transactions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
                                    val jsonBuilder = StringBuilder()
                                    jsonBuilder.append("{\n")
                                    jsonBuilder.append("  \"backup_timestamp\": \"$timestamp\",\n")
                                    jsonBuilder.append("  \"lab_name\": \"${labSettings.labName}\",\n")
                                    jsonBuilder.append("  \"clinics_count\": ${clinics.size},\n")
                                    jsonBuilder.append("  \"patients_count\": ${patients.size},\n")
                                    jsonBuilder.append("  \"orders_count\": ${workOrders.size},\n")
                                    jsonBuilder.append("  \"total_revenue\": ${workOrders.sumOf { it.totalAmount }},\n")
                                    jsonBuilder.append("  \"total_payments\": ${payments.sumOf { it.amount }},\n")
                                    jsonBuilder.append("  \"work_orders\": [\n")
                                    workOrders.forEachIndexed { idx, o ->
                                        jsonBuilder.append("    {\"job\": \"${o.jobNumber}\", \"clinic\": \"${o.clinicName}\", \"patient\": \"${o.patientName}\", \"type\": \"${o.workTypeName}\", \"total\": ${o.totalAmount}, \"status\": \"${o.status}\"}${if (idx < workOrders.size - 1) "," else ""}\n")
                                    }
                                    jsonBuilder.append("  ]\n")
                                    jsonBuilder.append("}")

                                    backupJsonString = jsonBuilder.toString()
                                    showBackupDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate Backup")
                            }

                            OutlinedButton(
                                onClick = { showResetConfirmDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOrange),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset Demo Data")
                            }
                        }

                        Button(
                            onClick = { showClearConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete ALL Data (start empty)")
                        }
                    }
                }
            }

            // Windows PC / VS Code Local Packaging Architecture
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Navy900),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Computer, contentDescription = null, tint = DentalBlueLight, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "WINDOWS PC & VS CODE DESKTOP READY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = DentalBlueLight
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This application is designed with offline-first local persistence. All data is stored in the local SQLite database without external cloud latency. The database schema, business rules, and models are fully transferable to Windows PC Tauri / Electron local builds.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }
    }

    // Backup Viewer Dialog
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Database Backup JSON Snapshot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Complete offline database state formatted in JSON:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    OutlinedTextField(
                        value = backupJsonString,
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
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("NazneenDentalLab_Backup", backupJsonString)
                        clipboard.setPrimaryClip(clip)
                        viewModel.showMessage("Backup copied to clipboard!")
                        showBackupDialog = false
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Backup JSON")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) { Text("Close") }
            }
        )
    }

    // Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset to Demo Data?") },
            text = { Text("This clears the database and reloads the demo dataset: 5 clinics, ~140 patients, ~300 work orders across the last 8 months with payments and mixed statuses. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetToDemoData()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOrange)
                ) {
                    Text("Reset to Demo Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Clear All Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Delete ALL Data?") },
            text = { Text("This permanently deletes every clinic, patient, work order and payment, leaving an EMPTY database (work types and lab settings are preserved). This cannot be undone. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun StatMiniItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
    }
}
