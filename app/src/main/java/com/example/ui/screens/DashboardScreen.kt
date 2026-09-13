package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WorkOrder
import com.example.data.util.MoneyUtils
import com.example.ui.DentalLabViewModel
import com.example.ui.components.OrderStatusBadge
import com.example.ui.components.StatCard
import com.example.ui.theme.*
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: DentalLabViewModel,
    onNavigateToWorkOrders: () -> Unit,
    onNavigateToClinics: () -> Unit,
    onNavigateToPayments: () -> Unit,
    onNavigateToPatients: () -> Unit,
    onSelectWorkOrder: (WorkOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val workOrders by viewModel.workOrders.collectAsState()
    val clinics by viewModel.clinics.collectAsState()
    val patients by viewModel.patients.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val labSettings by viewModel.labSettings.collectAsState()

    var selectedPeriod by remember { mutableStateOf("All Time") } // "All Time", "Today", "This Week", "This Month"

    // Filter orders by date period
    val now = Calendar.getInstance()
    val filteredOrders = remember(workOrders, selectedPeriod) {
        when (selectedPeriod) {
            "Today" -> {
                val startOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                workOrders.filter { it.entryDate >= startOfDay }
            }
            "This Week" -> {
                val startOfWeek = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                }.timeInMillis
                workOrders.filter { it.entryDate >= startOfWeek }
            }
            "This Month" -> {
                val startOfMonth = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                }.timeInMillis
                workOrders.filter { it.entryDate >= startOfMonth }
            }
            else -> workOrders
        }
    }

    // Calculations: revenue from billable work; collected from clinic-account payments in
    // the same period; cancelled work is never billed (consistent with statements/bills)
    val periodStartMillis = remember(selectedPeriod) {
        when (selectedPeriod) {
            "Today" -> Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "This Week" -> Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            }.timeInMillis
            "This Month" -> Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            }.timeInMillis
            else -> 0L
        }
    }
    val billableOrders = remember(filteredOrders) { filteredOrders.filter { it.status != "Cancelled" } }
    val totalRevenue = billableOrders.sumOf { it.totalAmount }
    val totalCollected = remember(payments, periodStartMillis) {
        MoneyUtils.round2(payments.filter { it.paymentDate >= periodStartMillis }.sumOf { it.amount })
    }
    val totalOutstanding = (totalRevenue - totalCollected).coerceAtLeast(0.0)

    val pendingCount = filteredOrders.count { it.status == "Pending" || it.status == "Received" }
    val inProgressCount = filteredOrders.count { it.status == "In Progress" }
    val readyCount = filteredOrders.count { it.status == "Ready" }
    val completedCount = filteredOrders.count { it.status == "Completed" }
    val deliveredCount = filteredOrders.count { it.status == "Delivered" }

    val todayTime = System.currentTimeMillis()
    val upcomingOrOverdue = remember(workOrders) {
        workOrders.filter {
            (it.status != "Delivered" && it.status != "Cancelled") &&
            (it.expectedDeliveryDate <= todayTime + (2 * 24 * 60 * 60 * 1000L))
        }.sortedBy { it.expectedDeliveryDate }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // Welcome Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Navy900),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = labSettings.labName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dental Laboratory Operations & Production Center",
                            style = MaterialTheme.typography.bodySmall,
                            color = DentalBlueLight
                        )
                    }
                }
            }
        }

        // Period Filters
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All Time", "Today", "This Week", "This Month").forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period, fontSize = 12.sp) },
                        modifier = Modifier.testTag("filter_period_$period")
                    )
                }
            }
        }

        // Financial KPIs Grid (Revenue, Collected, Outstanding)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Total Revenue",
                    value = viewModel.formatCurrency(totalRevenue),
                    subtitle = "${filteredOrders.size} Orders",
                    icon = Icons.Default.MonetizationOn,
                    accentColor = DentalBlue,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Collected",
                    value = viewModel.formatCurrency(totalCollected),
                    subtitle = "Payments Received",
                    icon = Icons.Default.CheckCircle,
                    accentColor = StatusGreen,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Outstanding",
                    value = viewModel.formatCurrency(totalOutstanding),
                    subtitle = "Pending Due",
                    icon = Icons.Default.Warning,
                    accentColor = StatusRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Production Status Counters
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "PRODUCTION PIPELINE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PipelineStageItem(count = pendingCount, label = "Pending", color = StatusOrange)
                        PipelineStageItem(count = inProgressCount, label = "In Progress", color = DentalBlue)
                        PipelineStageItem(count = readyCount, label = "Ready", color = DentalCyan)
                        PipelineStageItem(count = completedCount, label = "Completed", color = StatusGreen)
                        PipelineStageItem(count = deliveredCount, label = "Delivered", color = TextSecondary)
                    }
                }
            }
        }

        // Critical Deliveries Alert
        if (upcomingOrOverdue.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = StatusRed, modifier = Modifier.size(18.dp))
                            Text(
                                text = "URGENT / UPCOMING DELIVERIES (${upcomingOrOverdue.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusRed
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        upcomingOrOverdue.take(3).forEach { order ->
                            val isOverdue = order.expectedDeliveryDate < todayTime
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectWorkOrder(order) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${order.jobNumber} • ${order.patientName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${order.clinicName} • ${order.workTypeName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (isOverdue) "OVERDUE" else "Due ${viewModel.formatDate(order.expectedDeliveryDate)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOverdue) StatusRed else StatusOrange
                                    )
                                    OrderStatusBadge(order.status)
                                }
                            }
                            HorizontalDivider(color = Color(0xFFFEE2E2), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // Network Entities Counter (Clinics & Patients)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Active Clinics",
                    value = "${clinics.count { it.isActive }}",
                    subtitle = "Tap to view list",
                    icon = Icons.Default.LocalHospital,
                    accentColor = DentalTeal,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToClinics() }
                )
                StatCard(
                    title = "Registered Patients",
                    value = "${patients.size}",
                    subtitle = "Tap to view list",
                    icon = Icons.Default.People,
                    accentColor = StatusPurple,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToPatients() }
                )
            }
        }

        // Recent Work Orders Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Work Orders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                TextButton(onClick = onNavigateToWorkOrders) {
                    Text("View All (${workOrders.size})", fontSize = 12.sp, color = DentalBlue)
                }
            }
        }

        // Recent Work Order Items
        val recentOrders = workOrders.take(5)
        if (recentOrders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No work orders recorded yet. Click 'New Work' to start!", color = TextMuted)
                }
            }
        } else {
            items(recentOrders, key = { it.id }) { order ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectWorkOrder(order) }
                        .testTag("recent_order_${order.jobNumber}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = order.jobNumber,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Navy800
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OrderStatusBadge(order.status)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${order.patientName} (${order.clinicName})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${order.workTypeName} • Shade: ${order.shade}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = viewModel.formatCurrency(order.totalAmount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = DentalBlue
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PipelineStageItem(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.12f), CircleShape)
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}
