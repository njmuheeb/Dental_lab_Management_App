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
import com.example.data.model.Clinic
import com.example.ui.DentalLabViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicsScreen(
    viewModel: DentalLabViewModel,
    onOpenClinic: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clinics by viewModel.clinics.collectAsState()
    val workOrders by viewModel.workOrders.collectAsState()
    val payments by viewModel.payments.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterActiveOnly by remember { mutableStateOf(false) }

    var clinicToEdit by remember { mutableStateOf<Clinic?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var clinicToDelete by remember { mutableStateOf<Clinic?>(null) }

    val filteredClinics = remember(clinics, searchQuery, filterActiveOnly) {
        clinics.filter { clinic ->
            val matchesQuery = searchQuery.isBlank() ||
                    clinic.name.contains(searchQuery, ignoreCase = true) ||
                    clinic.dentistName.contains(searchQuery, ignoreCase = true) ||
                    clinic.city.contains(searchQuery, ignoreCase = true) ||
                    clinic.phone.contains(searchQuery)
            val matchesActive = !filterActiveOnly || clinic.isActive
            matchesQuery && matchesActive
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddingNew = true },
                containerColor = DentalBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_clinic")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Clinic")
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
            // Search & Filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .testTag("clinics_search_input"),
                placeholder = { Text("Search Clinics, Doctors, Cities...") },
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredClinics.size} Clinics Registered",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active Only", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = filterActiveOnly,
                        onCheckedChange = { filterActiveOnly = it },
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            if (filteredClinics.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No clinics found", color = TextMuted)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredClinics, key = { it.id }) { clinic ->
                        val clinicOrders = remember(workOrders, clinic.id) {
                            workOrders.filter { it.clinicId == clinic.id }
                        }
                        val clinicRevenue = clinicOrders.filter { it.status != "Cancelled" }.sumOf { it.totalAmount }
                        val clinicCollected = payments.filter { it.clinicId == clinic.id }.sumOf { it.amount }
                        val clinicBalance = (clinicRevenue - clinicCollected).coerceAtLeast(0.0)

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenClinic(clinic.id) }
                                .testTag("clinic_card_${clinic.clinicCode}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = clinic.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Navy900
                                        )
                                        Text(
                                            text = "Dr. ${clinic.dentistName} • ${clinic.city}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = DentalBlue
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { clinicToEdit = clinic }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(onClick = { clinicToDelete = clinic }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRed, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }

                                if (clinic.phone.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Phone: ${clinic.phone}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = OutlineLight.copy(alpha = 0.5f))

                                // Financial stats row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Orders", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                        Text("${clinicOrders.size}", fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }
                                    Column {
                                        Text("Total Revenue", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                        Text(viewModel.formatCurrency(clinicRevenue), fontWeight = FontWeight.Bold, color = DentalBlue)
                                    }
                                    Column {
                                        Text("Outstanding Due", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                        Text(
                                            viewModel.formatCurrency(clinicBalance),
                                            fontWeight = FontWeight.Bold,
                                            color = if (clinicBalance > 0) StatusRed else StatusGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (isAddingNew || clinicToEdit != null) {
        val editing = clinicToEdit
        var name by remember { mutableStateOf(editing?.name ?: "") }
        var dentist by remember { mutableStateOf(editing?.dentistName ?: "") }
        var phone by remember { mutableStateOf(editing?.phone ?: "") }
        var whatsapp by remember { mutableStateOf(editing?.whatsapp ?: "") }
        var email by remember { mutableStateOf(editing?.email ?: "") }
        var address by remember { mutableStateOf(editing?.address ?: "") }
        var city by remember { mutableStateOf(editing?.city ?: "Mumbai") }
        var notes by remember { mutableStateOf(editing?.notes ?: "") }
        var isActive by remember { mutableStateOf(editing?.isActive ?: true) }

        AlertDialog(
            onDismissRequest = {
                isAddingNew = false
                clinicToEdit = null
            },
            title = { Text(if (isAddingNew) "Add Dental Clinic" else "Edit Clinic") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Clinic Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = dentist, onValueChange = { dentist = it }, label = { Text("Dentist / Doctor *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Lab Notes / Preferences") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Active Status")
                        Switch(checked = isActive, onCheckedChange = { isActive = it }, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && dentist.isNotBlank()) {
                            val code = editing?.clinicCode ?: "CLN-${(100..999).random()}"
                            val clinicObj = Clinic(
                                id = editing?.id ?: 0,
                                clinicCode = code,
                                name = name.trim(),
                                dentistName = dentist.trim(),
                                phone = phone.trim(),
                                whatsapp = whatsapp.ifBlank { phone }.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                city = city.trim(),
                                notes = notes.trim(),
                                isActive = isActive
                            )
                            viewModel.saveClinic(clinicObj, isNew = (editing == null))
                            isAddingNew = false
                            clinicToEdit = null
                        } else {
                            viewModel.showMessage("Please fill required clinic fields")
                        }
                    }
                ) {
                    Text("Save Clinic")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isAddingNew = false
                    clinicToEdit = null
                }) { Text("Cancel") }
            }
        )
    }

    // Delete Confirmation
    if (clinicToDelete != null) {
        val clinic = clinicToDelete!!
        AlertDialog(
            onDismissRequest = { clinicToDelete = null },
            title = { Text("Delete Clinic?") },
            text = { Text("Are you sure you want to remove '${clinic.name}'? Work orders linked to this clinic will be preserved.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteClinic(clinic)
                        clinicToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { clinicToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
