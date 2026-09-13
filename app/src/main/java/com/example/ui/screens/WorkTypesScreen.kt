package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.data.model.WorkType
import com.example.ui.DentalLabViewModel
import com.example.ui.components.PricingModelBadge
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkTypesScreen(
    viewModel: DentalLabViewModel,
    modifier: Modifier = Modifier
) {
    val workTypes by viewModel.workTypes.collectAsState()

    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    var isAddingWorkType by remember { mutableStateOf(false) }
    var workTypeToEdit by remember { mutableStateOf<WorkType?>(null) }
    var workTypeToDelete by remember { mutableStateOf<WorkType?>(null) }

    val categories = listOf("All", "Crown & Bridge", "Dentures", "Implants", "Orthodontics", "Cosmetic", "Repairs")

    val filteredWorkTypes = remember(workTypes, selectedCategory, searchQuery) {
        workTypes.filter { wt ->
            val matchesCategory = selectedCategory == "All" || wt.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                    wt.name.contains(searchQuery, ignoreCase = true) ||
                    wt.description.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddingWorkType = true },
                containerColor = DentalBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_work_type")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Work Type")
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
                    .testTag("work_types_search_input"),
                placeholder = { Text("Search Work Types, Restorations...") },
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

            // Category Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "${filteredWorkTypes.size} Products & Restorations",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (filteredWorkTypes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No work types in this category", color = TextMuted)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredWorkTypes, key = { it.id }) { wt ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth().testTag("work_type_card_${wt.name.replace(" ", "_")}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = wt.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Navy900
                                        )
                                        Text(
                                            text = wt.category,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = DentalBlue
                                        )
                                    }

                                    PricingModelBadge(wt.pricingModel)
                                }

                                if (wt.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = wt.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = OutlineLight.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (wt.pricingModel == "UNIT_BASED") "Base Rate Per Unit" else "Standard Fixed Price",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                        Text(
                                            text = viewModel.formatCurrency(wt.defaultRate),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { workTypeToEdit = wt }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(onClick = { workTypeToDelete = wt }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRed, modifier = Modifier.size(20.dp))
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

    // Add / Edit Work Type Dialog
    if (isAddingWorkType || workTypeToEdit != null) {
        val editing = workTypeToEdit
        var name by remember { mutableStateOf(editing?.name ?: "") }
        var category by remember { mutableStateOf(editing?.category ?: "Crown & Bridge") }
        var description by remember { mutableStateOf(editing?.description ?: "") }
        var pricingModel by remember { mutableStateOf(editing?.pricingModel ?: "UNIT_BASED") }
        var defaultRateText by remember { mutableStateOf(if (editing != null && editing.defaultRate > 0) editing.defaultRate.toInt().toString() else "") }
        var isActive by remember { mutableStateOf(editing?.isActive ?: true) }

        AlertDialog(
            onDismissRequest = {
                isAddingWorkType = false
                workTypeToEdit = null
            },
            title = { Text(if (isAddingWorkType) "Add Work Type" else "Edit Work Type") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Restoration / Work Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                    // Category Dropdown
                    var catExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = catExpanded,
                        onExpandedChange = { catExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = catExpanded,
                            onDismissRequest = { catExpanded = false }
                        ) {
                            listOf("Crown & Bridge", "Dentures", "Implants", "Orthodontics", "Cosmetic", "Repairs", "Custom").forEach { c ->
                                DropdownMenuItem(text = { Text(c) }, onClick = { category = c; catExpanded = false })
                            }
                        }
                    }

                    // Pricing Model Selection
                    Text("Pricing Architecture", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = pricingModel == "UNIT_BASED",
                            onClick = { pricingModel = "UNIT_BASED" },
                            label = { Text("Unit-Based (Units × Rate)") }
                        )
                        FilterChip(
                            selected = pricingModel == "FIXED_PRICE",
                            onClick = { pricingModel = "FIXED_PRICE" },
                            label = { Text("Fixed-Price (Flat)") }
                        )
                    }

                    OutlinedTextField(
                        value = defaultRateText,
                        onValueChange = { defaultRateText = it },
                        label = { Text("Default Rate (₹) *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rate = defaultRateText.toDoubleOrNull() ?: 0.0
                        if (name.isNotBlank() && rate > 0) {
                            val wtObj = WorkType(
                                id = editing?.id ?: 0,
                                name = name.trim(),
                                category = category,
                                description = description.trim(),
                                pricingModel = pricingModel,
                                defaultRate = rate,
                                isActive = isActive
                            )
                            viewModel.saveWorkType(wtObj, isNew = (editing == null))
                            isAddingWorkType = false
                            workTypeToEdit = null
                        } else {
                            viewModel.showMessage("Please fill Name and valid Rate")
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isAddingWorkType = false
                    workTypeToEdit = null
                }) { Text("Cancel") }
            }
        )
    }

    if (workTypeToDelete != null) {
        val wt = workTypeToDelete!!
        AlertDialog(
            onDismissRequest = { workTypeToDelete = null },
            title = { Text("Delete Work Type?") },
            text = { Text("Are you sure you want to delete '${wt.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWorkType(wt)
                        workTypeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { workTypeToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
