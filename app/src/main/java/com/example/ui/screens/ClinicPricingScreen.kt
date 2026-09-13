package com.example.ui.screens

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
import com.example.data.model.ClinicRate
import com.example.data.model.WorkType
import com.example.ui.DentalLabViewModel
import com.example.ui.components.PricingModelBadge
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicPricingScreen(
    viewModel: DentalLabViewModel,
    modifier: Modifier = Modifier
) {
    val clinics by viewModel.clinics.collectAsState()
    val workTypes by viewModel.activeWorkTypes.collectAsState()
    val clinicRates by viewModel.clinicRates.collectAsState()

    var selectedClinic by remember { mutableStateOf<Clinic?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    // Rate editing dialog
    var itemToEditRate by remember { mutableStateOf<Pair<WorkType, Double>?>(null) }

    LaunchedEffect(clinics) {
        if (clinics.isNotEmpty() && selectedClinic == null) {
            selectedClinic = clinics.first()
        }
    }

    val currentClinicRates = remember(clinicRates, selectedClinic) {
        if (selectedClinic == null) emptyMap<Long, ClinicRate>()
        else clinicRates.filter { it.clinicId == selectedClinic!!.id }.associateBy { it.workTypeId }
    }

    val filteredWorkTypes = remember(workTypes, searchQuery, selectedCategory) {
        workTypes.filter { wt ->
            val matchesCat = selectedCategory == "All" || wt.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() || wt.name.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Clinic Selector
            var clinicDropdownExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = clinicDropdownExpanded,
                onExpandedChange = { clinicDropdownExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                OutlinedTextField(
                    value = selectedClinic?.let { "${it.name} (${it.dentistName})" } ?: "Select Clinic",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Dental Clinic to Configure Rates") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clinicDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().testTag("pricing_clinic_selector"),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = clinicDropdownExpanded,
                    onDismissRequest = { clinicDropdownExpanded = false }
                ) {
                    clinics.forEach { c ->
                        DropdownMenuItem(
                            text = { Text("${c.name} (${c.dentistName})") },
                            onClick = {
                                selectedClinic = c
                                clinicDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Notice about historical integrity
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = DentalBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clinic-specific rates automatically apply to future work orders. Past work orders preserve historical rates.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = Navy800
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Filter work types...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Work Types & Custom Rates List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredWorkTypes, key = { it.id }) { wt ->
                    val customRateObj = currentClinicRates[wt.id]
                    val hasCustomRate = customRateObj != null
                    val activeRate = customRateObj?.customRate ?: wt.defaultRate

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth().testTag("rate_row_${wt.id}")
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
                                    text = wt.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${wt.category} • Default: ${viewModel.formatCurrency(wt.defaultRate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = viewModel.formatCurrency(activeRate),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasCustomRate) DentalBlue else TextPrimary
                                    )
                                    Text(
                                        text = if (hasCustomRate) "Custom Rate" else "Standard Rate",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = if (hasCustomRate) DentalBlue else TextMuted
                                    )
                                }

                                IconButton(
                                    onClick = { itemToEditRate = Pair(wt, activeRate) }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Rate", tint = DentalBlue, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rate Edit Dialog
    if (itemToEditRate != null && selectedClinic != null) {
        val (wt, currentRate) = itemToEditRate!!
        var rateInput by remember { mutableStateOf(currentRate.toInt().toString()) }

        AlertDialog(
            onDismissRequest = { itemToEditRate = null },
            title = { Text("Set Rate for ${selectedClinic!!.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Work: ${wt.name}\nStandard Lab Rate: ₹${wt.defaultRate.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = rateInput,
                        onValueChange = { rateInput = it },
                        label = { Text("Clinic Rate (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newRate = rateInput.toDoubleOrNull() ?: 0.0
                        if (newRate > 0) {
                            viewModel.setClinicRate(
                                clinicId = selectedClinic!!.id,
                                workTypeId = wt.id,
                                rate = newRate,
                                pricingModel = wt.pricingModel
                            )
                            itemToEditRate = null
                        } else {
                            viewModel.showMessage("Please enter a valid rate")
                        }
                    }
                ) {
                    Text("Save Rate")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToEditRate = null }) { Text("Cancel") }
            }
        )
    }
}
