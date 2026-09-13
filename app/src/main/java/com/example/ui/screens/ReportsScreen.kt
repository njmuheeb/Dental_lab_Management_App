package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DentalLabViewModel
import com.example.ui.components.StatCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: DentalLabViewModel,
    modifier: Modifier = Modifier
) {
    val workOrders by viewModel.workOrders.collectAsState()
    val clinics by viewModel.clinics.collectAsState()
    val payments by viewModel.payments.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Financial, 1 = Clinic Performance, 2 = Work Distribution

    // Financial aggregates: cancelled work is never billed (consistent with dashboard,
    // clinic details, statements and invoices). Payments are clinic-account level.
    val billableOrders = remember(workOrders) { workOrders.filter { it.status != "Cancelled" } }
    val totalRevenue = remember(billableOrders) { billableOrders.sumOf { it.totalAmount } }
    val totalCollected = remember(payments) { payments.sumOf { it.amount } }
    val totalOutstanding = (totalRevenue - totalCollected).coerceAtLeast(0.0)

    // Clinic-wise aggregation (account-level balances from real payment rows)
    val clinicSummaries = remember(clinics, workOrders, payments) {
        clinics.map { clinic ->
            val orders = workOrders.filter { it.clinicId == clinic.id }
            val billable = orders.filter { it.status != "Cancelled" }
            val revenue = billable.sumOf { it.totalAmount }
            val paid = payments.filter { it.clinicId == clinic.id }.sumOf { it.amount }
            val balance = (revenue - paid).coerceAtLeast(0.0)
            ClinicSummary(
                clinicName = clinic.name,
                dentistName = clinic.dentistName,
                orderCount = orders.size,
                revenue = revenue,
                paid = paid,
                balance = balance
            )
        }.sortedByDescending { it.revenue }
    }

    // Work-type distribution (billable work only - cancelled jobs are excluded)
    val workTypeDistribution = remember(billableOrders) {
        billableOrders.groupBy { it.workTypeName }
            .map { (name, list) ->
                WorkTypeSummary(
                    name = name,
                    count = list.size,
                    units = list.sumOf { it.units },
                    revenue = list.sumOf { it.totalAmount }
                )
            }.sortedByDescending { it.count }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Financial", fontSize = 12.sp) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Clinics", fontSize = 12.sp) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Work Types", fontSize = 12.sp) })
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedTab) {
                0 -> {
                    // Financial Overview
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize().testTag("reports_financial_tab")
                    ) {
                        item {
                            Text("OVERALL LAB LEDGER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextMuted)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatCard(title = "Billed", value = viewModel.formatCurrency(totalRevenue), accentColor = DentalBlue, modifier = Modifier.weight(1f))
                                StatCard(title = "Collected", value = viewModel.formatCurrency(totalCollected), accentColor = StatusGreen, modifier = Modifier.weight(1f))
                                StatCard(title = "Due", value = viewModel.formatCurrency(totalOutstanding), accentColor = StatusRed, modifier = Modifier.weight(1f))
                            }
                        }

                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("COLLECTION RECOVERY RATE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextMuted)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val recoveryRate = if (totalRevenue > 0) ((totalCollected / totalRevenue) * 100).toInt() else 100
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("$recoveryRate% Collected", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DentalBlue)
                                        Text("${workOrders.size} total orders processed", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    LinearProgressIndicator(
                                        progress = { (recoveryRate / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = StatusGreen,
                                        trackColor = StatusRedLight
                                    )
                                }
                            }
                        }

                        item {
                            Text("CLINICS WITH HIGHEST OUTSTANDING DUES", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextMuted)
                        }

                        val clinicsWithDues = clinicSummaries.filter { it.balance > 0 }
                        if (clinicsWithDues.isEmpty()) {
                            item {
                                Text("All clinic accounts are fully cleared!", color = StatusGreen, fontWeight = FontWeight.Medium)
                            }
                        } else {
                            items(clinicsWithDues) { summary ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = CardDefaults.outlinedCardBorder(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(summary.clinicName, fontWeight = FontWeight.Bold, color = TextPrimary)
                                            Text("Dr. ${summary.dentistName} • ${summary.orderCount} Orders", fontSize = 11.sp, color = TextSecondary)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(viewModel.formatCurrency(summary.balance), fontWeight = FontWeight.Bold, color = StatusRed)
                                            Text("Due Balance", fontSize = 10.sp, color = TextMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Clinic Performance Breakdown
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize().testTag("reports_clinics_tab")
                    ) {
                        items(clinicSummaries) { summary ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(summary.clinicName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Navy900)
                                            Text("Dr. ${summary.dentistName}", color = DentalBlue, fontSize = 12.sp)
                                        }
                                        Text(
                                            text = "${summary.orderCount} Cases",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextSecondary
                                        )
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = OutlineLight.copy(alpha = 0.5f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Billed", fontSize = 11.sp, color = TextMuted)
                                            Text(viewModel.formatCurrency(summary.revenue), fontWeight = FontWeight.Bold, color = TextPrimary)
                                        }
                                        Column {
                                            Text("Paid", fontSize = 11.sp, color = TextMuted)
                                            Text(viewModel.formatCurrency(summary.paid), fontWeight = FontWeight.Bold, color = StatusGreen)
                                        }
                                        Column {
                                            Text("Due", fontSize = 11.sp, color = TextMuted)
                                            Text(viewModel.formatCurrency(summary.balance), fontWeight = FontWeight.Bold, color = if (summary.balance > 0) StatusRed else StatusGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Work Type Distribution
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize().testTag("reports_work_types_tab")
                    ) {
                        items(workTypeDistribution) { wt ->
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(wt.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = Navy900)
                                        Text("${wt.count} Cases • ${wt.units} Units Total", fontSize = 12.sp, color = TextSecondary)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(viewModel.formatCurrency(wt.revenue), fontWeight = FontWeight.Bold, color = DentalBlue, style = MaterialTheme.typography.titleMedium)
                                        Text("Revenue", fontSize = 10.sp, color = TextMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class ClinicSummary(
    val clinicName: String,
    val dentistName: String,
    val orderCount: Int,
    val revenue: Double,
    val paid: Double,
    val balance: Double
)

data class WorkTypeSummary(
    val name: String,
    val count: Int,
    val units: Int,
    val revenue: Double
)
