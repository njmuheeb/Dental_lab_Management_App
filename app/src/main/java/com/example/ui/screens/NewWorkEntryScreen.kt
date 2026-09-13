package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Clinic
import com.example.data.model.Patient
import com.example.data.model.WorkOrder
import com.example.data.model.WorkType
import com.example.ui.DentalLabViewModel
import com.example.ui.components.DentalOdontogram
import com.example.ui.components.PricingModelBadge
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewWorkEntryScreen(
    viewModel: DentalLabViewModel,
    onWorkOrderCreated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clinics by viewModel.activeClinics.collectAsState()
    val allPatients by viewModel.patients.collectAsState()
    val workTypes by viewModel.activeWorkTypes.collectAsState()
    val labSettings by viewModel.labSettings.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    // Form state
    var jobNumber by remember { mutableStateOf("") }
    var selectedClinic by remember { mutableStateOf<Clinic?>(null) }
    var selectedPatient by remember { mutableStateOf<Patient?>(null) }
    var dentistName by remember { mutableStateOf("") }
    var caseNumber by remember { mutableStateOf("") }

    var selectedWorkType by remember { mutableStateOf<WorkType?>(null) }
    var pricingModel by remember { mutableStateOf("UNIT_BASED") } // "UNIT_BASED" or "FIXED_PRICE"
    var selectedTeeth by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var shade by remember { mutableStateOf("A2") }
    var material by remember { mutableStateOf("Multilayer Zirconia") }
    var units by remember { mutableIntStateOf(1) }
    var rate by remember { mutableDoubleStateOf(0.0) }
    var notes by remember { mutableStateOf("") }
    var specialInstructions by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Received") }

    // Delivery turnaround
    val entryDateMillis = remember { System.currentTimeMillis() }
    var deliveryDays by remember { mutableIntStateOf(labSettings.defaultTurnaroundDays) }
    val expectedDeliveryMillis = remember(entryDateMillis, deliveryDays) {
        entryDateMillis + (deliveryDays.toLong() * 24 * 60 * 60 * 1000L)
    }

    // Modal dialogs for inline creation
    var showAddClinicDialog by remember { mutableStateOf(false) }
    var showAddPatientDialog by remember { mutableStateOf(false) }

    // Generate Job Number on first load
    LaunchedEffect(Unit) {
        jobNumber = viewModel.repository.generateNextJobNumber()
        if (clinics.isNotEmpty() && selectedClinic == null) {
            selectedClinic = clinics.first()
            dentistName = clinics.first().dentistName
        }
    }

    // Auto-update rate when Clinic or WorkType changes
    LaunchedEffect(selectedClinic, selectedWorkType) {
        if (selectedClinic != null && selectedWorkType != null) {
            val (customRate, model) = viewModel.repository.getEffectiveRate(selectedClinic!!.id, selectedWorkType!!.id)
            rate = customRate
            pricingModel = model
        } else if (selectedWorkType != null) {
            rate = selectedWorkType!!.defaultRate
            pricingModel = selectedWorkType!!.pricingModel
        }
    }

    // Auto-sync units with selected teeth count for UNIT_BASED work if teeth are chosen
    LaunchedEffect(selectedTeeth) {
        if (pricingModel == "UNIT_BASED" && selectedTeeth.isNotEmpty()) {
            units = selectedTeeth.size
        }
    }

    // Calculate Total (single source of truth: MoneyUtils)
    val calculatedTotal = com.example.data.util.MoneyUtils.totalFor(units, rate, pricingModel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("New Work Entry", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (jobNumber.isNotEmpty()) "Job #: $jobNumber" else "Generating Job #...",
                            style = MaterialTheme.typography.bodySmall,
                            color = DentalBlue
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (selectedClinic == null) {
                                viewModel.showMessage("Please select a clinic")
                                return@Button
                            }
                            if (selectedPatient == null) {
                                viewModel.showMessage("Please select a patient")
                                return@Button
                            }
                            if (selectedWorkType == null) {
                                viewModel.showMessage("Please select a work type")
                                return@Button
                            }
                            if (pricingModel == "UNIT_BASED" && units <= 0) {
                                viewModel.showMessage("Units must be at least 1")
                                return@Button
                            }

                            val workOrder = WorkOrder(
                                jobNumber = jobNumber,
                                entryDate = entryDateMillis,
                                expectedDeliveryDate = expectedDeliveryMillis,
                                clinicId = selectedClinic!!.id,
                                clinicName = selectedClinic!!.name,
                                dentistName = dentistName.ifBlank { selectedClinic!!.dentistName },
                                patientId = selectedPatient!!.id,
                                patientName = selectedPatient!!.name,
                                caseNumber = caseNumber,
                                workTypeId = selectedWorkType!!.id,
                                workTypeName = selectedWorkType!!.name,
                                pricingModel = pricingModel,
                                selectedTeeth = selectedTeeth.sorted().joinToString(","),
                                shade = shade,
                                material = material,
                                units = if (pricingModel == "UNIT_BASED") units else 1,
                                rate = rate,
                                totalAmount = calculatedTotal,
                                status = status,
                                notes = notes,
                                specialInstructions = specialInstructions
                            )

                            viewModel.createWorkOrder(workOrder) {
                                onWorkOrderCreated()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DentalBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("save_work_order_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Work Order", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Basic Clinical Information
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
                        text = "1. CLINIC & PATIENT DETAILS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Navy800
                    )

                    // Clinic Selector
                    var clinicExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = clinicExpanded,
                            onExpandedChange = { clinicExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selectedClinic?.name ?: "Select Dental Clinic",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Dental Clinic *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clinicExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("clinic_dropdown"),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = clinicExpanded,
                                onDismissRequest = { clinicExpanded = false }
                            ) {
                                clinics.forEach { clinic ->
                                    DropdownMenuItem(
                                        text = { Text("${clinic.name} (${clinic.dentistName})") },
                                        onClick = {
                                            selectedClinic = clinic
                                            dentistName = clinic.dentistName
                                            clinicExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        FilledTonalIconButton(
                            onClick = { showAddClinicDialog = true },
                            modifier = Modifier.testTag("add_clinic_inline_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add New Clinic")
                        }
                    }

                    // Doctor & Case Number
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = dentistName,
                            onValueChange = { dentistName = it },
                            label = { Text("Dentist / Doctor") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = caseNumber,
                            onValueChange = { caseNumber = it },
                            label = { Text("Case / Ref #") },
                            placeholder = { Text("CS-101") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }

                    // Patient Selector
                    val clinicPatients = remember(allPatients, selectedClinic) {
                        if (selectedClinic == null) allPatients
                        else allPatients.filter { it.clinicId == selectedClinic!!.id }
                    }

                    var patientExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = patientExpanded,
                            onExpandedChange = { patientExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selectedPatient?.let { "${it.name} (${it.patientCode})" } ?: "Select Patient *",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Patient *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = patientExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("patient_dropdown"),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = patientExpanded,
                                onDismissRequest = { patientExpanded = false }
                            ) {
                                clinicPatients.forEach { patient ->
                                    DropdownMenuItem(
                                        text = { Text("${patient.name} (${patient.patientCode})") },
                                        onClick = {
                                            selectedPatient = patient
                                            patientExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        FilledTonalIconButton(
                            onClick = { showAddPatientDialog = true },
                            modifier = Modifier.testTag("add_patient_inline_btn")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add New Patient")
                        }
                    }
                }
            }

            // Section 2: Work Type & Pricing Model Configuration
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "2. WORK RESTORATION & PRICING SPECIFICATION",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Navy800
                    )

                    // Work Type Selector
                    var workTypeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = workTypeExpanded,
                        onExpandedChange = { workTypeExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedWorkType?.name ?: "Select Restoration / Work Type *",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Work Type / Service *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = workTypeExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("work_type_dropdown"),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = workTypeExpanded,
                            onDismissRequest = { workTypeExpanded = false }
                        ) {
                            workTypes.forEach { wt ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(wt.name)
                                            PricingModelBadge(wt.pricingModel)
                                        }
                                    },
                                    onClick = {
                                        selectedWorkType = wt
                                        workTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Critical Pricing Model Indicator
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (pricingModel == "UNIT_BASED") Color(0xFFEFF6FF) else Color(0xFFFAF5FF),
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
                                Text(
                                    text = if (pricingModel == "UNIT_BASED") "UNIT-BASED PRICING" else "FIXED-PRICE PRICING",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pricingModel == "UNIT_BASED") DentalBlue else Color(0xFF9333EA)
                                )
                                Text(
                                    text = if (pricingModel == "UNIT_BASED") "Formula: Units × Rate = Total"
                                           else "Single Flat Rate (Units not required / disabled)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            // Model switch toggle if user wants to override
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (pricingModel == "UNIT_BASED") "Unit" else "Fixed",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Switch(
                                    checked = pricingModel == "FIXED_PRICE",
                                    onCheckedChange = { isFixed ->
                                        pricingModel = if (isFixed) "FIXED_PRICE" else "UNIT_BASED"
                                    },
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }

                    // Units & Rate Inputs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Units input (Disabled / Not Applicable for Fixed-Price)
                        if (pricingModel == "UNIT_BASED") {
                            OutlinedTextField(
                                value = units.toString(),
                                onValueChange = { units = it.toIntOrNull() ?: 1 },
                                label = { Text("Units / Qty *") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("units_input"),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceVariantLight,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Column {
                                        Text("Units", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                        Text("N/A (Fixed Job)", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                                    }
                                }
                            }
                        }

                        // Rate Input
                        OutlinedTextField(
                            value = if (rate > 0) rate.toInt().toString() else "",
                            onValueChange = { rate = it.toDoubleOrNull() ?: 0.0 },
                            label = { Text("Rate (₹) *") },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("rate_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        // Calculated Total Display
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Navy900),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(56.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("TOTAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DentalBlueLight)
                                    Text("₹%,.0f".format(calculatedTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Visual Odontogram (FDI Teeth Selection)
            Text(
                text = "3. TOOTH NUMBER SELECTION (ODONTOGRAM)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Navy800
            )
            DentalOdontogram(
                selectedTeeth = selectedTeeth,
                onTeethChanged = { selectedTeeth = it }
            )

            // Section 4: Materials, Shade, and Turnaround
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
                        text = "4. SHADE, MATERIAL & PRODUCTION",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Navy800
                    )

                    // Shade & Material
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        var shadeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = shadeExpanded,
                            onExpandedChange = { shadeExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = shade,
                                onValueChange = { shade = it },
                                label = { Text("Shade (e.g. A2)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = shadeExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = shadeExpanded,
                                onDismissRequest = { shadeExpanded = false }
                            ) {
                                listOf("A1", "A2", "A3", "A3.5", "B1", "B2", "C1", "Bleach BL1", "Bleach BL2").forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s) },
                                        onClick = {
                                            shade = s
                                            shadeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        var materialExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = materialExpanded,
                            onExpandedChange = { materialExpanded = it },
                            modifier = Modifier.weight(1.2f)
                        ) {
                            OutlinedTextField(
                                value = material,
                                onValueChange = { material = it },
                                label = { Text("Material") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = materialExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = materialExpanded,
                                onDismissRequest = { materialExpanded = false }
                            ) {
                                listOf("Multilayer Zirconia", "Monolithic Zirconia", "High Translucent Zirconia", "E-Max Lithium Disilicate", "PFM Ceramic", "Cast Co-Cr Alloy", "Lucitone 199 Acrylic", "Valplast Thermoplastic").forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m) },
                                        onClick = {
                                            material = m
                                            materialExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Expected Turnaround
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Turnaround: $deliveryDays Days", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Delivery: ${viewModel.formatDate(expectedDeliveryMillis)}", style = MaterialTheme.typography.bodySmall, color = DentalBlue)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 5, 7, 10).forEach { d ->
                                FilterChip(
                                    selected = deliveryDays == d,
                                    onClick = { deliveryDays = d },
                                    label = { Text("${d}d", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Special Instructions
                    OutlinedTextField(
                        value = specialInstructions,
                        onValueChange = { specialInstructions = it },
                        label = { Text("Special Instructions / Technician Notes") },
                        placeholder = { Text("e.g. Check occlusion margin, high glaze, anatomical cusp relief") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        minLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // --- Inline Quick Add Clinic Dialog ---
    if (showAddClinicDialog) {
        var newClinicName by remember { mutableStateOf("") }
        var newDoctorName by remember { mutableStateOf("") }
        var newPhone by remember { mutableStateOf("") }
        var newCity by remember { mutableStateOf("Mumbai") }

        AlertDialog(
            onDismissRequest = { showAddClinicDialog = false },
            title = { Text("Add New Clinic") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newClinicName,
                        onValueChange = { newClinicName = it },
                        label = { Text("Clinic Name *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDoctorName,
                        onValueChange = { newDoctorName = it },
                        label = { Text("Dentist / Doctor *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone / WhatsApp") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCity,
                        onValueChange = { newCity = it },
                        label = { Text("City") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newClinicName.isNotBlank() && newDoctorName.isNotBlank()) {
                            val code = "CLN-${(100..999).random()}"
                            val newClinic = Clinic(
                                clinicCode = code,
                                name = newClinicName.trim(),
                                dentistName = newDoctorName.trim(),
                                phone = newPhone.trim(),
                                city = newCity.trim()
                            )
                            coroutineScope.launch {
                                val id = viewModel.repository.insertClinic(newClinic)
                                selectedClinic = newClinic.copy(id = id)
                                dentistName = newDoctorName.trim()
                                showAddClinicDialog = false
                            }
                        } else {
                            viewModel.showMessage("Please fill Clinic Name and Doctor")
                        }
                    }
                ) {
                    Text("Add Clinic")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddClinicDialog = false }) { Text("Cancel") }
            }
        )
    }

    // --- Inline Quick Add Patient Dialog ---
    if (showAddPatientDialog) {
        var newPatName by remember { mutableStateOf("") }
        var newPatPhone by remember { mutableStateOf("") }
        var newPatAge by remember { mutableStateOf("") }
        var newPatGender by remember { mutableStateOf("Female") }

        AlertDialog(
            onDismissRequest = { showAddPatientDialog = false },
            title = { Text("Add New Patient") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPatName,
                        onValueChange = { newPatName = it },
                        label = { Text("Patient Full Name *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPatPhone,
                        onValueChange = { newPatPhone = it },
                        label = { Text("Phone") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newPatAge,
                            onValueChange = { newPatAge = it },
                            label = { Text("Age") },
                            modifier = Modifier.weight(1f)
                        )
                        var genderExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = genderExpanded,
                            onExpandedChange = { genderExpanded = it },
                            modifier = Modifier.weight(1.2f)
                        ) {
                            OutlinedTextField(
                                value = newPatGender,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Gender") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = genderExpanded,
                                onDismissRequest = { genderExpanded = false }
                            ) {
                                listOf("Male", "Female", "Other").forEach { g ->
                                    DropdownMenuItem(
                                        text = { Text(g) },
                                        onClick = {
                                            newPatGender = g
                                            genderExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPatName.isNotBlank() && selectedClinic != null) {
                            val code = "PAT-${(100..999).random()}"
                            val newPat = Patient(
                                patientCode = code,
                                name = newPatName.trim(),
                                phone = newPatPhone.trim(),
                                age = newPatAge.toIntOrNull(),
                                gender = newPatGender,
                                clinicId = selectedClinic!!.id,
                                dentistName = dentistName
                            )
                            coroutineScope.launch {
                                val id = viewModel.repository.insertPatient(newPat)
                                selectedPatient = newPat.copy(id = id)
                                showAddPatientDialog = false
                            }
                        } else {
                            viewModel.showMessage("Please specify Patient Name and ensure Clinic is selected")
                        }
                    }
                ) {
                    Text("Register Patient")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPatientDialog = false }) { Text("Cancel") }
            }
        )
    }
}
