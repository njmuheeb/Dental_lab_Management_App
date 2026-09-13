package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.WorkOrder
import com.example.data.util.ToothFormat
import com.example.ui.DentalLabViewModel
import com.example.ui.components.DentalOdontogram
import com.example.ui.components.OrderStatusBadge
import com.example.ui.components.PricingModelBadge
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrdersScreen(
    viewModel: DentalLabViewModel,
    onNavigateToNewWork: () -> Unit,
    selectedOrderForDetails: WorkOrder? = null,
    onClearSelectedOrder: () -> Unit = {},
    onOpenWarranty: (WorkOrder) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val workOrders by viewModel.workOrders.collectAsState()
    val clinics by viewModel.clinics.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("All") }
    var selectedClinicFilter by remember { mutableStateOf<Long?>(null) }
    var sortBy by remember { mutableStateOf("Newest") } // "Newest", "Oldest", "Total High", "Delivery Due"

    // Dialog states
    var orderToViewDetails by remember { mutableStateOf<WorkOrder?>(selectedOrderForDetails) }
    var orderToUpdateStatus by remember { mutableStateOf<WorkOrder?>(null) }
    var orderToDelete by remember { mutableStateOf<WorkOrder?>(null) }

    LaunchedEffect(selectedOrderForDetails) {
        if (selectedOrderForDetails != null) {
            orderToViewDetails = selectedOrderForDetails
        }
    }

    val statusOptions = listOf("All", "Received", "Pending", "In Progress", "Ready", "Completed", "Delivered", "Cancelled")

    // Filter & sort logic (payments are clinic-account level; no case-wise payment filter)
    val filteredOrders = remember(
        workOrders, searchQuery, selectedStatusFilter, selectedClinicFilter, sortBy
    ) {
        workOrders.filter { order ->
            val matchesQuery = searchQuery.isBlank() ||
                    order.jobNumber.contains(searchQuery, ignoreCase = true) ||
                    order.patientName.contains(searchQuery, ignoreCase = true) ||
                    order.clinicName.contains(searchQuery, ignoreCase = true) ||
                    order.workTypeName.contains(searchQuery, ignoreCase = true) ||
                    ToothFormat.formatCompact(order.selectedTeeth).contains(searchQuery, ignoreCase = true)

            val matchesStatus = selectedStatusFilter == "All" || order.status == selectedStatusFilter
            val matchesClinic = selectedClinicFilter == null || order.clinicId == selectedClinicFilter

            matchesQuery && matchesStatus && matchesClinic
        }.sortedWith { a, b ->
            when (sortBy) {
                "Oldest" -> a.entryDate.compareTo(b.entryDate)
                "Total High" -> b.totalAmount.compareTo(a.totalAmount)
                "Delivery Due" -> a.expectedDeliveryDate.compareTo(b.expectedDeliveryDate)
                else -> b.entryDate.compareTo(a.entryDate) // Newest
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToNewWork,
                containerColor = DentalBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_new_work_order")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Work Order")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Top Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .testTag("work_orders_search_input"),
                placeholder = { Text("Search Job #, Clinic, Patient, Teeth...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Status Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(statusOptions) { status ->
                    FilterChip(
                        selected = selectedStatusFilter == status,
                        onClick = { selectedStatusFilter = status },
                        label = { Text(status, fontSize = 12.sp) },
                        modifier = Modifier.testTag("filter_status_$status")
                    )
                }
            }

            // Secondary Filters: Sort
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredOrders.size} Orders Found",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Sort Selector
                    var sortMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { sortMenuExpanded = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Sort: $sortBy", fontSize = 11.sp)
                            Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            listOf("Newest", "Oldest", "Total High", "Delivery Due").forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        sortBy = opt
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Work Orders List
            if (filteredOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Assignment, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No matching work orders found", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Try adjusting your filters or create a new work order", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredOrders, key = { it.id }) { order ->
                        WorkOrderCard(
                            order = order,
                            viewModel = viewModel,
                            onViewDetails = { orderToViewDetails = order },
                            onChangeStatus = { orderToUpdateStatus = order },
                            onDelete = { orderToDelete = order }
                        )
                    }
                }
            }
        }
    }

    // --- Order Details Dialog ---
    if (orderToViewDetails != null) {
        val order = orderToViewDetails!!
        WorkOrderDetailsDialog(
            order = order,
            viewModel = viewModel,
            onDismiss = {
                orderToViewDetails = null
                onClearSelectedOrder()
            },
            onUpdateStatus = {
                orderToUpdateStatus = order
            },
            onOpenWarranty = {
                orderToViewDetails = null
                onClearSelectedOrder()
                onOpenWarranty(order)
            }
        )
    }

    // --- Update Status Dialog ---
    if (orderToUpdateStatus != null) {
        val order = orderToUpdateStatus!!
        var statusChoice by remember { mutableStateOf(order.status) }
        AlertDialog(
            onDismissRequest = { orderToUpdateStatus = null },
            title = { Text("Update Status: ${order.jobNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Received", "Pending", "In Progress", "Ready", "Completed", "Delivered", "Cancelled").forEach { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { statusChoice = s }
                                .padding(vertical = 6.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = statusChoice == s,
                                onClick = { statusChoice = s }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OrderStatusBadge(s)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateOrderStatus(order.id, statusChoice)
                        orderToUpdateStatus = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { orderToUpdateStatus = null }) { Text("Cancel") }
            }
        )
    }

    // --- Delete Confirmation Dialog ---
    if (orderToDelete != null) {
        val order = orderToDelete!!
        AlertDialog(
            onDismissRequest = { orderToDelete = null },
            title = { Text("Delete Work Order?") },
            text = { Text("Are you sure you want to delete ${order.jobNumber} (${order.patientName})? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWorkOrder(order)
                        orderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { orderToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun WorkOrderCard(
    order: WorkOrder,
    viewModel: DentalLabViewModel,
    onViewDetails: () -> Unit,
    onChangeStatus: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewDetails)
            .testTag("order_card_${order.jobNumber}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Job #, Entry Date, Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = order.jobNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Navy900
                    )
                    Text(
                        text = "Rec: ${viewModel.formatDate(order.entryDate)} • Due: ${viewModel.formatDate(order.expectedDeliveryDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PricingModelBadge(order.pricingModel)
                    OrderStatusBadge(order.status)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Patient & Clinic
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.patientName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = order.clinicName,
                        style = MaterialTheme.typography.bodySmall,
                        color = DentalBlue,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Work Type & Teeth Summary (quadrant-based single-digit tooth numbers)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.workTypeName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    val teeth = ToothFormat.formatCompact(order.selectedTeeth)
                    Text(
                        text = if (teeth.isNotBlank()) "Teeth: $teeth • Shade: ${order.shade}"
                        else "Shade: ${order.shade} • Material: ${order.material}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Navy700,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = OutlineLight.copy(alpha = 0.5f))

            // Footer: total + actions (no case-wise payment controls - clinics pay at account level)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = viewModel.formatCurrency(order.totalAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (order.pricingModel == "UNIT_BASED") {
                        Text(
                            text = " (${order.units} × ₹${order.rate.toInt()})",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                OutlinedButton(
                    onClick = onChangeStatus,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Status", fontSize = 11.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderDetailsDialog(
    order: WorkOrder,
    viewModel: DentalLabViewModel,
    onDismiss: () -> Unit,
    onUpdateStatus: () -> Unit,
    onOpenWarranty: () -> Unit = {}
) {
    val selectedTeethSet = remember(order.selectedTeeth) {
        if (order.selectedTeeth.isBlank()) emptySet()
        else order.selectedTeeth.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Job Details: ${order.jobNumber}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Navy900
                        )
                        Text(
                            text = "Registered on ${viewModel.formatDate(order.entryDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Status & Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OrderStatusBadge(order.status)

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onOpenWarranty,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("open_warranty_btn")
                            ) {
                                Icon(
                                    Icons.Default.WorkspacePremium, contentDescription = null,
                                    tint = StatusOrange, modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Warranty Card", fontSize = 11.sp)
                            }
                            Button(
                                onClick = onUpdateStatus,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Change Status", fontSize = 12.sp)
                            }
                        }
                    }

                    // Clinic & Patient Card
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("CLINICAL INFORMATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextMuted)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Clinic: ${order.clinicName}", fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (order.dentistName.isNotBlank()) {
                                Text("Doctor: ${order.dentistName}", color = TextSecondary, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Patient: ${order.patientName}", fontWeight = FontWeight.Bold, color = DentalBlue)
                            if (order.caseNumber.isNotBlank()) {
                                Text("Case/Ref No: ${order.caseNumber}", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    // Work Specification Card
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("WORK SPECIFICATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextMuted)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Work Type: ${order.workTypeName}", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Pricing Model: ${if (order.pricingModel == "UNIT_BASED") "Unit-Based (Units × Rate)" else "Fixed-Price"}", color = TextSecondary, fontSize = 12.sp)
                            Text("Shade: ${order.shade} • Material: ${order.material}", color = TextPrimary, fontSize = 13.sp)
                            if (order.pricingModel == "UNIT_BASED") {
                                Text("Units: ${order.units} @ ₹${order.rate.toInt()} per unit", fontWeight = FontWeight.SemiBold, color = DentalTeal, fontSize = 13.sp)
                            } else {
                                Text("Fixed Job Rate: ₹${order.rate.toInt()}", fontWeight = FontWeight.SemiBold, color = DentalTeal, fontSize = 13.sp)
                            }
                            if (order.specialInstructions.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Special Instructions:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(order.specialInstructions, color = Navy700, fontSize = 12.sp)
                            }
                        }
                    }

                    // Visual Odontogram (Read-Only, single-digit 1-8 per quadrant)
                    Text("TOOTH CHART (1-8 PER QUADRANT)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text(
                        ToothFormat.formatLong(order.selectedTeeth).ifBlank { "No teeth specified" },
                        style = MaterialTheme.typography.bodySmall,
                        color = DentalBlue,
                        fontWeight = FontWeight.Bold
                    )
                    DentalOdontogram(
                        selectedTeeth = selectedTeethSet,
                        onTeethChanged = {},
                        readOnly = true
                    )

                    // Billing Card (payments are clinic-account level, never per case)
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("BILLING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextMuted)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Total Amount", fontSize = 12.sp, color = TextSecondary)
                                    Text(viewModel.formatCurrency(order.totalAmount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }
                                Column {
                                    Text(if (order.pricingModel == "UNIT_BASED") "Units × Rate" else "Fixed Rate", fontSize = 12.sp, color = TextSecondary)
                                    Text(
                                        if (order.pricingModel == "UNIT_BASED") "${order.units} × ₹${order.rate.toInt()}"
                                        else "₹${order.rate.toInt()}",
                                        fontWeight = FontWeight.Bold, color = DentalTeal, style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Clinics settle monthly at the account level - see the clinic's Monthly Statement for outstanding balance.",
                                fontSize = 10.sp, color = TextMuted
                            )
                        }
                    }
                }

                // Footer Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close Details")
                }
            }
        }
    }
}
