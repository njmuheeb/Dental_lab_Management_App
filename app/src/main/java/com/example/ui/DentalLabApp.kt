package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.WorkOrder
import com.example.ui.screens.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class NavDestination(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    WORK_ORDERS("Work Orders", Icons.Default.Assignment),
    NEW_WORK("New Work Entry", Icons.Default.AddCircle),
    CLINICS("Clinics / Doctors", Icons.Default.LocalHospital),
    PATIENTS("Patients", Icons.Default.People),
    WORK_TYPES("Work Types", Icons.Default.Category),
    CLINIC_PRICING("Clinic Pricing", Icons.Default.PriceCheck),
    PAYMENTS("Payments & Billing", Icons.Default.Payment),
    REPORTS("Reports & Analytics", Icons.Default.BarChart),
    WARRANTY_CARDS("Warranty Cards", Icons.Default.WorkspacePremium),
    IMPORT_EXPORT("Import / Export", Icons.Default.SwapVert),
    SETTINGS("Settings & Backup", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DentalLabApp(
    viewModel: DentalLabViewModel = viewModel()
) {
    var currentDestination by remember { mutableStateOf(NavDestination.DASHBOARD) }
    // Navigation back stack: drawer/bottom selections push history so the device back
    // button returns to the previous screen instead of closing the app.
    val navHistory = remember { mutableStateListOf<NavDestination>() }

    var selectedOrderForDetails by remember { mutableStateOf<WorkOrder?>(null) }
    // Clinic detail (rendered inside the CLINICS destination)
    var detailClinicId by remember { mutableStateOf<Long?>(null) }
    // Dedicated secondary screens (opaque overlays above the scaffold)
    var billRequest by remember { mutableStateOf<Long?>(null) }          // monthly bill/invoice
    var statementRequest by remember { mutableStateOf<Long?>(null) }     // full clinic statement
    var warrantyRequest by remember { mutableStateOf<WorkOrder?>(null) } // warranty card (from a work order)
    var warrantyEditCardId by remember { mutableStateOf<Long?>(null) }   // warranty card (existing, from the list screen)

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val labSettings by viewModel.labSettings.collectAsState()

    // Listen for feedback messages
    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    /** Records history and switches destination (no-op when already there). */
    fun navigateTo(destination: NavDestination) {
        if (destination == currentDestination) return
        navHistory.add(currentDestination)
        if (navHistory.size > 20) navHistory.removeAt(0)
        currentDestination = destination
    }

    // Reset transient navigation when switching destinations
    LaunchedEffect(currentDestination) {
        if (currentDestination != NavDestination.CLINICS) {
            detailClinicId = null
            statementRequest = null
            billRequest = null
        }
        if (currentDestination != NavDestination.WORK_ORDERS) {
            selectedOrderForDetails = null
        }
    }

    // Root back handling. BackHandlers composed later (clinic detail, statement, bill,
    // warranty screens, dialogs) take precedence over this one; this handles drawer +
    // destination history, and shows press-again-to-exit on the Dashboard root.
    var lastBackPressAt by remember { mutableLongStateOf(0L) }
    BackHandler {
        when {
            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
            navHistory.isNotEmpty() -> currentDestination = navHistory.removeAt(navHistory.lastIndex)
            currentDestination != NavDestination.DASHBOARD -> currentDestination = NavDestination.DASHBOARD
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressAt < 2000L) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    lastBackPressAt = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
                drawerContainerColor = Navy900,
                drawerContentColor = Color.White
            ) {
                // Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Navy800)
                        .padding(20.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DentalBlue)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = labSettings.labName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Laboratory Management System",
                        style = MaterialTheme.typography.bodySmall,
                        color = DentalBlueLight
                    )
                }

                HorizontalDivider(color = Navy700)

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Items List (New Work stays available via the bottom bar only)
                NavDestination.values()
                    .filter { it != NavDestination.NEW_WORK }
                    .forEach { destination ->
                        val isSelected = currentDestination == destination
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) DentalBlueLight else Color(0xFF94A3B8)
                                )
                            },
                            label = {
                                Text(
                                    text = destination.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    fontSize = 13.sp
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                navigateTo(destination)
                                coroutineScope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Navy700,
                                unselectedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .testTag("nav_${destination.name.lowercase()}")
                        )
                    }

                Spacer(modifier = Modifier.weight(1f))

                // Drawer Footer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Dental Lab Management v1.0.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "Offline-First SQLite Database",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = DentalCyan
                    )
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = currentDestination.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("menu_drawer_btn")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = Navy900
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    val bottomItems = listOf(
                        NavDestination.DASHBOARD,
                        NavDestination.WORK_ORDERS,
                        NavDestination.NEW_WORK,
                        NavDestination.CLINICS,
                        NavDestination.PAYMENTS
                    )

                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(if (item == NavDestination.NEW_WORK) "+ New" else item.label.split(" ").first(), fontSize = 11.sp) },
                            selected = currentDestination == item,
                            onClick = { navigateTo(item) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = DentalBlue,
                                selectedTextColor = DentalBlue,
                                indicatorColor = DentalBlue.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier.testTag("bottom_nav_${item.name.lowercase()}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentDestination) {
                    NavDestination.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToWorkOrders = { navigateTo(NavDestination.WORK_ORDERS) },
                        onNavigateToClinics = { navigateTo(NavDestination.CLINICS) },
                        onNavigateToPayments = { navigateTo(NavDestination.PAYMENTS) },
                        onNavigateToPatients = { navigateTo(NavDestination.PATIENTS) },
                        onSelectWorkOrder = { order ->
                            selectedOrderForDetails = order
                            navigateTo(NavDestination.WORK_ORDERS)
                        }
                    )
                    NavDestination.WORK_ORDERS -> WorkOrdersScreen(
                        viewModel = viewModel,
                        onNavigateToNewWork = { navigateTo(NavDestination.NEW_WORK) },
                        selectedOrderForDetails = selectedOrderForDetails,
                        onClearSelectedOrder = { selectedOrderForDetails = null },
                        onOpenWarranty = { warrantyRequest = it }
                    )
                    NavDestination.NEW_WORK -> NewWorkEntryScreen(
                        viewModel = viewModel,
                        onWorkOrderCreated = { navigateTo(NavDestination.WORK_ORDERS) }
                    )
                    NavDestination.CLINICS -> {
                        val detailId = detailClinicId
                        if (detailId != null) {
                            val clinics by viewModel.clinics.collectAsState()
                            val clinic = clinics.find { it.id == detailId }
                            if (clinic != null) {
                                ClinicDetailScreen(
                                    viewModel = viewModel,
                                    clinic = clinic,
                                    onBack = { detailClinicId = null },
                                    onOpenBill = { billRequest = it },
                                    onOpenStatement = { statementRequest = it },
                                    onNavigateToNewWork = { navigateTo(NavDestination.NEW_WORK) }
                                )
                            } else {
                                ClinicsScreen(viewModel = viewModel, onOpenClinic = { detailClinicId = it })
                            }
                        } else {
                            ClinicsScreen(viewModel = viewModel, onOpenClinic = { detailClinicId = it })
                        }
                    }
                    NavDestination.PATIENTS -> PatientsScreen(viewModel = viewModel)
                    NavDestination.WORK_TYPES -> WorkTypesScreen(viewModel = viewModel)
                    NavDestination.CLINIC_PRICING -> ClinicPricingScreen(viewModel = viewModel)
                    NavDestination.PAYMENTS -> PaymentsScreen(viewModel = viewModel)
                    NavDestination.REPORTS -> ReportsScreen(viewModel = viewModel)
                    NavDestination.WARRANTY_CARDS -> WarrantyCardsScreen(
                        viewModel = viewModel,
                        onEditCard = { warrantyEditCardId = it }
                    )
                    NavDestination.IMPORT_EXPORT -> ImportExportScreen(viewModel = viewModel)
                    NavDestination.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }

    // Dedicated secondary screens (opaque, composed after the scaffold so their back
    // handling and content take precedence over everything beneath them)
    warrantyEditCardId?.let { cardId ->
        WarrantyCardScreen(
            viewModel = viewModel,
            cardId = cardId,
            workOrder = null,
            onBack = { warrantyEditCardId = null }
        )
    }
    warrantyRequest?.let { order ->
        WarrantyCardScreen(
            viewModel = viewModel,
            cardId = null,
            workOrder = order,
            onBack = { warrantyRequest = null }
        )
    }
    billRequest?.let { clinicId ->
        MonthlyBillScreen(
            viewModel = viewModel,
            clinicId = clinicId,
            onBack = { billRequest = null }
        )
    }
    statementRequest?.let { clinicId ->
        ClinicStatementScreen(
            viewModel = viewModel,
            clinicId = clinicId,
            onBack = { statementRequest = null }
        )
    }
}
