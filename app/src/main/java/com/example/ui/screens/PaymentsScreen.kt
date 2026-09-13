package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Payment
import com.example.data.model.StatementMath
import com.example.data.util.MoneyUtils
import com.example.ui.DentalLabViewModel
import com.example.ui.components.StatCard
import com.example.ui.theme.DentalBlue
import com.example.ui.theme.Navy900
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

/**
 * Clinic accounts & payments hub (monthly combined payment model):
 *  - lab-wide totals (collected / outstanding) computed from real data
 *  - per-clinic account cards with current outstanding and inline "Record Payment"
 *  - full clinic-payment history ledger (clinic-level receipts, never case-wise)
 */
@Composable
fun PaymentsScreen(
    viewModel: DentalLabViewModel,
    modifier: Modifier = Modifier
) {
    val payments by viewModel.payments.collectAsState()
    val clinics by viewModel.clinics.collectAsState()
    val workOrders by viewModel.workOrders.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isRecordingPayment by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }

    // Lab totals: cancelled work is never billed; payments are clinic-account level
    val billableOrders = remember(workOrders) { workOrders.filter { StatementMath.isBillable(it) } }
    val totalBilled = remember(billableOrders) { MoneyUtils.round2(billableOrders.sumOf { it.totalAmount }) }
    val totalCollected = remember(payments) { MoneyUtils.round2(payments.sumOf { it.amount }) }
    val totalDue = (totalBilled - totalCollected).coerceAtLeast(0.0)

    // Per-clinic account summaries
    data class ClinicAccount(
        val clinicId: Long,
        val name: String,
        val code: String,
        val billed: Double,
        val paid: Double,
        val outstanding: Double
    )
    val accounts = remember(clinics, workOrders, payments) {
        clinics.map { c ->
            val billed = MoneyUtils.round2(
                workOrders.filter { it.clinicId == c.id && StatementMath.isBillable(it) }.sumOf { it.totalAmount }
            )
            val paid = MoneyUtils.round2(payments.filter { it.clinicId == c.id }.sumOf { it.amount })
            ClinicAccount(
                clinicId = c.id,
                name = c.name,
                code = c.clinicCode,
                billed = billed,
                paid = paid,
                outstanding = MoneyUtils.round2(billed - paid).coerceAtLeast(0.0)
            )
        }.sortedByDescending { it.outstanding }
    }

    val clinicNames = remember(clinics) { clinics.associate { it.id to it.name } }
    val filteredPayments = remember(payments, searchQuery, clinicNames) {
        if (searchQuery.isBlank()) payments
        else payments.filter { p ->
            (clinicNames[p.clinicId] ?: "").contains(searchQuery, true) ||
                p.paymentMethod.contains(searchQuery, true) ||
                p.referenceNumber.contains(searchQuery, true) ||
                p.notes.contains(searchQuery, true)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isRecordingPayment = true },
                containerColor = StatusGreen,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_record_payment")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Record Clinic Payment")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
        ) {
            // Financial summary
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        title = "Total Collected",
                        value = viewModel.formatCurrency(totalCollected),
                        subtitle = "${payments.size} clinic payments",
                        icon = Icons.Default.CheckCircle,
                        accentColor = StatusGreen,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Total Outstanding",
                        value = viewModel.formatCurrency(totalDue),
                        subtitle = "All clinics combined",
                        icon = Icons.Default.Warning,
                        accentColor = StatusRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Clinic accounts
            item {
                Text(
                    "CLINIC ACCOUNTS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Navy900
                )
            }
            items(accounts, key = { it.clinicId }) { account ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth().testTag("clinic_account_${account.code}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                account.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Navy900
                            )
                            Text(
                                "${account.code} • Billed ${viewModel.formatCurrency(account.billed)} • Paid ${
                                    viewModel.formatCurrency(account.paid)
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Text(
                                "Outstanding: ${viewModel.formatCurrency(account.outstanding)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (account.outstanding > 0) StatusRed else StatusGreen
                            )
                        }
                        Button(
                            onClick = { isRecordingPayment = true },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Record Payment", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Search
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().testTag("payments_search_input"),
                    placeholder = { Text("Search clinic, method, reference...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Payment history
            item {
                Text(
                    "Payment History (${filteredPayments.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Navy900
                )
            }

            if (filteredPayments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No clinic payments recorded yet", color = TextMuted)
                    }
                }
            } else {
                items(filteredPayments, key = { it.id }) { p ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth().testTag("payment_row_${p.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        clinicNames[p.clinicId] ?: "Clinic #${p.clinicId}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Navy900
                                    )
                                    Text(
                                        "${viewModel.formatDate(p.paymentDate)} • ${p.paymentMethod}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "+ ${viewModel.formatCurrency(p.amount)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusGreen
                                    )
                                    IconButton(onClick = { paymentToDelete = p }) {
                                        Icon(
                                            Icons.Default.Delete, contentDescription = "Delete",
                                            tint = TextMuted, modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            if (p.referenceNumber.isNotBlank() || p.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    listOfNotNull(
                                        p.referenceNumber.takeIf { it.isNotBlank() }?.let { "Ref: $it" },
                                        p.notes.takeIf { it.isNotBlank() }
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Record Clinic Payment (global: clinic selectable inside the dialog)
    if (isRecordingPayment) {
        RecordClinicPaymentDialog(
            viewModel = viewModel,
            onDismiss = { isRecordingPayment = false }
        )
    }

    // Delete Confirmation
    if (paymentToDelete != null) {
        val p = paymentToDelete!!
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("Delete Payment?") },
            text = { Text("Remove this clinic payment of ${viewModel.formatCurrency(p.amount)}? The clinic's outstanding balance will be recalculated.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePayment(p)
                        paymentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
