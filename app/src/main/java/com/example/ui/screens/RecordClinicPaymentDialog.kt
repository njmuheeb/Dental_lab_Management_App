package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Clinic
import com.example.data.util.MoneyUtils
import com.example.ui.DentalLabViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Records a combined monthly payment against a CLINIC ACCOUNT (never an individual case).
 * Fields: clinic (selector when opened globally), payment date, amount, method, optional
 * reference and note. Multiple payments per clinic are supported; each payment reduces the
 * clinic's overall outstanding balance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordClinicPaymentDialog(
    viewModel: DentalLabViewModel,
    onDismiss: () -> Unit,
    fixedClinic: Clinic? = null
) {
    val clinics by viewModel.clinics.collectAsState()
    var selectedClinic by remember { mutableStateOf(fixedClinic ?: clinics.firstOrNull()) }

    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("UPI") }
    var ref by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var paymentDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Suggested amount: the clinic's current outstanding balance (never auto-applied per case)
    val outstanding = selectedClinic?.let { c ->
        val orders = viewModel.workOrders.value.filter { it.clinicId == c.id && it.status != "Cancelled" }
        val paid = viewModel.payments.value.filter { it.clinicId == c.id }.sumOf { it.amount }
        (orders.sumOf { it.totalAmount } - paid).coerceAtLeast(0.0)
    } ?: 0.0

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Clinic Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Clinic selector (hidden when opened from a specific clinic)
                if (fixedClinic == null) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = selectedClinic?.name ?: "Select clinic",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Clinic *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            clinics.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.name} (${c.clinicCode})") },
                                    onClick = { selectedClinic = c; expanded = false }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "${fixedClinic.name} • Dr. ${fixedClinic.dentistName}",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                }

                if (outstanding > 0.0) {
                    Text(
                        "Current outstanding: ${MoneyUtils.formatINR(outstanding)}",
                        fontSize = 11.sp,
                        color = com.example.ui.theme.StatusOrange
                    )
                }

                // Payment date
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  Date: ${dateFmt.format(Date(paymentDate))}", fontSize = 13.sp)
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹) *") },
                    placeholder = { Text(if (outstanding > 0) "e.g. ${MoneyUtils.plain(outstanding)}" else "e.g. 25000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Payment Method", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Cash", "UPI", "Bank Transfer", "Card", "Cheque").forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m, fontSize = 10.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = ref, onValueChange = { ref = it },
                    label = { Text("Reference / Txn ID (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Note (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    val clinic = selectedClinic
                    if (clinic == null) {
                        viewModel.showMessage("Select a clinic")
                    } else if (amount <= 0) {
                        viewModel.showMessage("Enter a valid payment amount")
                    } else {
                        viewModel.recordPayment(
                            clinicId = clinic.id,
                            amount = amount,
                            paymentMethod = method,
                            refNumber = ref,
                            notes = notes,
                            paymentDate = paymentDate,
                            onSuccess = onDismiss
                        )
                    }
                }
            ) { Text("Confirm Payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = paymentDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { paymentDate = utcMillisToLocal(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** DatePicker returns UTC midnight; convert to local calendar date (noon, stable for storage). */
private fun utcMillisToLocal(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
