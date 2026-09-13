package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Patient
import com.example.ui.DentalLabViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientsScreen(
    viewModel: DentalLabViewModel,
    modifier: Modifier = Modifier
) {
    val patients by viewModel.patients.collectAsState()
    val clinics by viewModel.clinics.collectAsState()
    val workOrders by viewModel.workOrders.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedClinicFilter by remember { mutableStateOf<Long?>(null) }

    var isAddingPatient by remember { mutableStateOf(false) }
    var patientToEdit by remember { mutableStateOf<Patient?>(null) }
    var patientToDelete by remember { mutableStateOf<Patient?>(null) }

    val filteredPatients = remember(patients, searchQuery, selectedClinicFilter) {
        patients.filter { patient ->
            val matchesQuery = searchQuery.isBlank() ||
                    patient.name.contains(searchQuery, ignoreCase = true) ||
                    patient.patientCode.contains(searchQuery, ignoreCase = true) ||
                    patient.phone.contains(searchQuery)
            val matchesClinic = selectedClinicFilter == null || patient.clinicId == selectedClinicFilter
            matchesQuery && matchesClinic
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddingPatient = true },
                containerColor = DentalBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_patient")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Patient")
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
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .testTag("patients_search_input"),
                placeholder = { Text("Search Patient Name, ID, Phone...") },
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

            Text(
                text = "${filteredPatients.size} Patients Recorded",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (filteredPatients.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No patients registered yet", color = TextMuted)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredPatients, key = { it.id }) { patient ->
                        val clinic = clinics.find { it.id == patient.clinicId }
                        val patientOrders = workOrders.filter { it.patientId == patient.id }
                        val patientRevenue = patientOrders.filter { it.status != "Cancelled" }.sumOf { it.totalAmount }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth().testTag("patient_card_${patient.patientCode}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = patient.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Navy900
                                        )
                                        Text(
                                            text = "${patient.patientCode} • ${patient.gender}${if (patient.age != null) ", ${patient.age} yrs" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { patientToEdit = patient }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(onClick = { patientToDelete = patient }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRed, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Clinic: ${clinic?.name ?: "Unknown Clinic"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DentalBlue,
                                    fontWeight = FontWeight.Medium
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = OutlineLight.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${patientOrders.size} Case(s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Total: ${viewModel.formatCurrency(patientRevenue)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Patient Dialog
    if (isAddingPatient || patientToEdit != null) {
        val editing = patientToEdit
        var name by remember { mutableStateOf(editing?.name ?: "") }
        var phone by remember { mutableStateOf(editing?.phone ?: "") }
        var ageText by remember { mutableStateOf(editing?.age?.toString() ?: "") }
        var gender by remember { mutableStateOf(editing?.gender ?: "Female") }
        var selectedClinicId by remember { mutableStateOf(editing?.clinicId ?: clinics.firstOrNull()?.id ?: 0L) }
        var notes by remember { mutableStateOf(editing?.notes ?: "") }

        AlertDialog(
            onDismissRequest = {
                isAddingPatient = false
                patientToEdit = null
            },
            title = { Text(if (isAddingPatient) "Register Patient" else "Edit Patient") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Patient Full Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = ageText, onValueChange = { ageText = it }, label = { Text("Age") }, modifier = Modifier.weight(1f), singleLine = true)
                        var genderExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = genderExpanded,
                            onExpandedChange = { genderExpanded = it },
                            modifier = Modifier.weight(1.2f)
                        ) {
                            OutlinedTextField(
                                value = gender,
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
                                    DropdownMenuItem(text = { Text(g) }, onClick = { gender = g; genderExpanded = false })
                                }
                            }
                        }
                    }

                    // Clinic Selector
                    var clinicDropExpanded by remember { mutableStateOf(false) }
                    val currentClinic = clinics.find { it.id == selectedClinicId }
                    ExposedDropdownMenuBox(
                        expanded = clinicDropExpanded,
                        onExpandedChange = { clinicDropExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentClinic?.name ?: "Select Clinic",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Associated Clinic *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clinicDropExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = clinicDropExpanded,
                            onDismissRequest = { clinicDropExpanded = false }
                        ) {
                            clinics.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = {
                                        selectedClinicId = c.id
                                        clinicDropExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Medical / Dental Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && selectedClinicId > 0) {
                            val code = editing?.patientCode ?: "PAT-${(100..999).random()}"
                            val patObj = Patient(
                                id = editing?.id ?: 0,
                                patientCode = code,
                                name = name.trim(),
                                phone = phone.trim(),
                                age = ageText.toIntOrNull(),
                                gender = gender,
                                clinicId = selectedClinicId,
                                dentistName = clinics.find { it.id == selectedClinicId }?.dentistName ?: "",
                                notes = notes.trim()
                            )
                            viewModel.savePatient(patObj, isNew = (editing == null))
                            isAddingPatient = false
                            patientToEdit = null
                        } else {
                            viewModel.showMessage("Please fill Patient Name and select a Clinic")
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isAddingPatient = false
                    patientToEdit = null
                }) { Text("Cancel") }
            }
        )
    }

    if (patientToDelete != null) {
        val patient = patientToDelete!!
        AlertDialog(
            onDismissRequest = { patientToDelete = null },
            title = { Text("Delete Patient?") },
            text = { Text("Are you sure you want to delete '${patient.name}'? Existing orders will remain in history.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePatient(patient)
                        patientToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { patientToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
