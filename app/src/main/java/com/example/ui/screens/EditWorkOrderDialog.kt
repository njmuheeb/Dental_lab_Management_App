package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Patient
import com.example.data.model.WorkOrder
import com.example.data.model.WorkType
import com.example.data.util.MoneyUtils
import com.example.ui.DentalLabViewModel
import com.example.ui.components.DentalOdontogram
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Full edit dialog for an existing work order: patient, work type, pricing model, odontogram
 * teeth, shade, material, units, rate, delivery date, status and notes. Total is always
 * recomputed through [MoneyUtils] so stored amounts stay consistent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkOrderDialog(
    order: WorkOrder,
    viewModel: DentalLabViewModel,
    onDismiss: () -> Unit
) {
    val workTypes by viewModel.activeWorkTypes.collectAsState()
    val patients by viewModel.patients.collectAsState()

    var selectedPatient by remember { mutableStateOf<Patient?>(patients.find { it.id == order.patientId }) }
    var selectedWorkType by remember { mutableStateOf<WorkType?>(workTypes.find { it.id == order.workTypeId }) }
    var pricingModel by remember { mutableStateOf(order.pricingModel) }
    var selectedTeeth by remember {
        mutableStateOf(
            if (order.selectedTeeth.isBlank()) emptySet()
            else order.selectedTeeth.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        )
    }
    var shade by remember { mutableStateOf(order.shade) }
    var material by remember { mutableStateOf(order.material) }
    var units by remember { mutableIntStateOf(order.units) }
    var rate by remember { mutableStateOf(order.rate) }
    var status by remember { mutableStateOf(order.status) }
    var notes by remember { mutableStateOf(order.notes) }
    var caseNumber by remember { mutableStateOf(order.caseNumber) }

    // Delivery: days from entry date
    var deliveryDays by remember {
        mutableIntStateOf(
            ((order.expectedDeliveryDate - order.entryDate) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(1)
        )
    }

    // Refresh the effective (clinic-specific) rate only when the user actually switches to a
    // DIFFERENT work type - historical rates on past orders are preserved otherwise.
    LaunchedEffect(selectedWorkType) {
        if (selectedWorkType != null && selectedWorkType!!.id != order.workTypeId) {
            val (customRate, model) = viewModel.repository.getEffectiveRate(order.clinicId, selectedWorkType!!.id)
            rate = customRate
            pricingModel = model
        }
    }

    val calculatedTotal = MoneyUtils.totalFor(units, rate, pricingModel)
    val expectedDelivery = order.entryDate + deliveryDays.toLong() * 24 * 60 * 60 * 1000L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${order.jobNumber}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Patient
                var patientExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = patientExpanded, onExpandedChange = { patientExpanded = it }) {
                    OutlinedTextField(
                        value = selectedPatient?.name ?: order.patientName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Patient") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = patientExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = patientExpanded, onDismissRequest = { patientExpanded = false }) {
                        patients.filter { it.clinicId == order.clinicId }.forEach { p ->
                            DropdownMenuItem(
                                text = { Text("${p.name} (${p.patientCode})") },
                                onClick = { selectedPatient = p; patientExpanded = false }
                            )
                        }
                    }
                }

                // Work type
                var wtExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = wtExpanded, onExpandedChange = { wtExpanded = it }) {
                    OutlinedTextField(
                        value = selectedWorkType?.name ?: order.workTypeName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Work Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wtExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = wtExpanded, onDismissRequest = { wtExpanded = false }) {
                        workTypes.forEach { wt ->
                            DropdownMenuItem(
                                text = { Text(wt.name) },
                                onClick = { selectedWorkType = wt; wtExpanded = false }
                            )
                        }
                    }
                }

                // Pricing model switch
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (pricingModel == "UNIT_BASED") "Unit-Based (Units × Rate)" else "Fixed-Price",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = pricingModel == "FIXED_PRICE",
                        onCheckedChange = { pricingModel = if (it) "FIXED_PRICE" else "UNIT_BASED" }
                    )
                }

                // Teeth
                if (selectedTeeth.isNotEmpty() && pricingModel == "UNIT_BASED") {
                    LaunchedEffect(selectedTeeth) { units = selectedTeeth.size }
                }
                DentalOdontogram(selectedTeeth = selectedTeeth, onTeethChanged = { selectedTeeth = it })

                // Shade / material / case
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = shade, onValueChange = { shade = it },
                        label = { Text("Shade") }, modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = caseNumber, onValueChange = { caseNumber = it },
                        label = { Text("Case #") }, modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                OutlinedTextField(
                    value = material, onValueChange = { material = it },
                    label = { Text("Material") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                // Units / rate
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pricingModel == "UNIT_BASED") {
                        OutlinedTextField(
                            value = units.toString(),
                            onValueChange = { units = (it.toIntOrNull() ?: 1).coerceAtLeast(1) },
                            label = { Text("Units") }, modifier = Modifier.weight(1f), singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = if (rate > 0) MoneyUtils.plain(rate) else "",
                        onValueChange = { rate = it.toDoubleOrNull() ?: 0.0 },
                        label = { Text("Rate (₹)") }, modifier = Modifier.weight(1f), singleLine = true
                    )
                }

                // Delivery days
                Text("Delivery: ${viewModel.formatDate(expectedDelivery)} (${deliveryDays} days)", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 5, 7, 10, 14).forEach { d ->
                        FilterChip(
                            selected = deliveryDays == d,
                            onClick = { deliveryDays = d },
                            label = { Text("${d}d", fontSize = 11.sp) }
                        )
                    }
                }

                // Status
                Text("Status", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Received", "In Progress", "Ready").forEach { s ->
                        FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s, fontSize = 10.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Completed", "Delivered", "Cancelled").forEach { s ->
                        FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s, fontSize = 10.sp) })
                    }
                }

                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2
                )

                Text(
                    "Total: ${MoneyUtils.formatINR(calculatedTotal)}" +
                        (if (pricingModel == "UNIT_BASED") "  ($units × ${MoneyUtils.plain(rate)})" else "  (fixed)"),
                    fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val wt = selectedWorkType
                    val patient = selectedPatient
                    if (wt == null) {
                        viewModel.showMessage("Select a work type")
                        return@Button
                    }
                    val resolvedPatientName = patient?.name ?: order.patientName
                    val resolvedPatientId = patient?.id ?: order.patientId
                    val actualDelivery = if (status == "Delivered") expectedDelivery else order.actualDeliveryDate
                    val updated = order.copy(
                        patientId = resolvedPatientId,
                        patientName = resolvedPatientName,
                        workTypeId = wt.id,
                        workTypeName = wt.name,
                        pricingModel = pricingModel,
                        selectedTeeth = selectedTeeth.sorted().joinToString(","),
                        shade = shade,
                        material = material,
                        caseNumber = caseNumber,
                        units = if (pricingModel == "UNIT_BASED") units else 1,
                        rate = rate,
                        expectedDeliveryDate = expectedDelivery,
                        actualDeliveryDate = actualDelivery,
                        status = status,
                        notes = notes
                    )
                    viewModel.updateWorkOrder(updated)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_edit_work_order_btn")
            ) { Text("Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
