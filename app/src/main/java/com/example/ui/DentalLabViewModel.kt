package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.ClinicStatement
import com.example.data.model.LabSettings
import com.example.data.model.Payment
import com.example.data.repository.DentalLabRepository
import com.example.data.sync.SheetsSyncService
import com.example.data.util.MoneyUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DentalLabViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    val repository = DentalLabRepository(database)
    private val syncService = SheetsSyncService(database)

    val clinics = repository.allClinics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeClinics = repository.activeClinics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val patients = repository.allPatients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workTypes = repository.allWorkTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeWorkTypes = repository.activeWorkTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clinicRates = repository.allRates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workOrders = repository.allWorkOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments = repository.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val labSettings: StateFlow<LabSettings> = repository.labSettings
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LabSettings())

    /** Number of local changes waiting for the optional Google Sheets sync. */
    val pendingSyncCount = repository.pendingSyncCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // UI Feedback Message Flow
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    fun showMessage(msg: String) {
        viewModelScope.launch { _userMessage.emit(msg) }
    }

    // --- Clinic Actions ---
    fun saveClinic(clinic: com.example.data.model.Clinic, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew) {
                repository.insertClinic(clinic)
                showMessage("Clinic '${clinic.name}' added successfully")
            } else {
                repository.updateClinic(clinic.copy(updatedAt = System.currentTimeMillis(), pendingSync = true))
                showMessage("Clinic '${clinic.name}' updated")
            }
        }
    }

    fun deleteClinic(clinic: com.example.data.model.Clinic) {
        viewModelScope.launch {
            val deleted = repository.deleteClinic(clinic)
            if (deleted) {
                showMessage("Clinic '${clinic.name}' deleted")
            } else {
                val orders = repository.getAllWorkOrdersForClinic(clinic.id).size
                showMessage("Cannot delete: '${clinic.name}' still has $orders work order(s) / registered patients. Delete or reassign them first.")
            }
        }
    }

    // --- Patient Actions ---
    fun savePatient(patient: com.example.data.model.Patient, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew) {
                repository.insertPatient(patient)
                showMessage("Patient '${patient.name}' registered")
            } else {
                repository.updatePatient(patient.copy(updatedAt = System.currentTimeMillis(), pendingSync = true))
                showMessage("Patient '${patient.name}' updated")
            }
        }
    }

    fun deletePatient(patient: com.example.data.model.Patient) {
        viewModelScope.launch {
            val deleted = repository.deletePatient(patient)
            showMessage(if (deleted) "Patient deleted" else "Cannot delete: this patient still has work orders on record")
        }
    }

    // --- Work Type Actions ---
    fun saveWorkType(workType: com.example.data.model.WorkType, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew) {
                repository.insertWorkType(workType)
                showMessage("Work Type '${workType.name}' created")
            } else {
                repository.updateWorkType(workType.copy(updatedAt = System.currentTimeMillis(), pendingSync = true))
                showMessage("Work Type updated")
            }
        }
    }

    fun deleteWorkType(workType: com.example.data.model.WorkType) {
        viewModelScope.launch {
            val deleted = repository.deleteWorkType(workType)
            showMessage(if (deleted) "Work Type removed" else "Cannot delete: this work type is used by work orders or clinic rates")
        }
    }

    // --- Clinic Rate Matrix ---
    fun setClinicRate(clinicId: Long, workTypeId: Long, rate: Double, pricingModel: String) {
        viewModelScope.launch {
            repository.setClinicRate(clinicId, workTypeId, rate, pricingModel)
            showMessage("Clinic rate saved (₹${rate.toInt()})")
        }
    }

    // --- Work Order Actions ---
    fun createWorkOrder(order: com.example.data.model.WorkOrder, onCreated: () -> Unit = {}) {
        viewModelScope.launch {
            repository.createWorkOrder(order)
            showMessage("Work Order ${order.jobNumber} created successfully")
            onCreated()
        }
    }

    fun updateWorkOrder(order: com.example.data.model.WorkOrder) {
        viewModelScope.launch {
            repository.updateWorkOrder(order)
            showMessage("Work Order ${order.jobNumber} updated")
        }
    }

    fun updateOrderStatus(orderId: Long, newStatus: String) {
        viewModelScope.launch {
            repository.updateWorkOrderStatus(orderId, newStatus)
            showMessage("Order status updated to $newStatus")
        }
    }

    fun deleteWorkOrder(order: com.example.data.model.WorkOrder) {
        viewModelScope.launch {
            repository.deleteWorkOrder(order)
            showMessage("Order ${order.jobNumber} deleted")
        }
    }

    /** Records a combined clinic-account payment (monthly settlement model). */
    fun recordPayment(
        clinicId: Long,
        amount: Double,
        paymentMethod: String,
        refNumber: String = "",
        notes: String = "",
        paymentDate: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                showMessage("Please enter a valid payment amount")
                return@launch
            }
            val payment = Payment(
                clinicId = clinicId,
                amount = amount,
                paymentDate = paymentDate,
                paymentMethod = paymentMethod,
                referenceNumber = refNumber,
                notes = notes
            )
            repository.recordPayment(payment)
            showMessage("Clinic payment of ₹${MoneyUtils.plain(amount)} recorded via $paymentMethod")
            onSuccess()
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            repository.deletePayment(payment)
            showMessage("Payment transaction removed")
        }
    }

    // --- Statements ---
    suspend fun getClinicMonthlyStatement(clinicId: Long, year: Int, month: Int): ClinicStatement? =
        repository.getClinicMonthlyStatement(clinicId, year, month)

    suspend fun getClinicStatementForRange(clinicId: Long, start: Long, endExclusive: Long): ClinicStatement? =
        repository.getClinicStatementForRange(clinicId, start, endExclusive)

    // --- Warranty Cards ---
    suspend fun getOrCreateWarrantyCard(order: com.example.data.model.WorkOrder): com.example.data.model.WarrantyCard? =
        repository.getOrCreateWarrantyCard(order)

    fun saveWarrantyCard(card: com.example.data.model.WarrantyCard, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveWarrantyCard(card)
            showMessage("Warranty card ${card.cardNumber} saved")
            onDone()
        }
    }

    // --- Settings Actions ---
    fun saveSettings(settings: LabSettings) {
        viewModelScope.launch {
            repository.saveLabSettings(settings)
            showMessage("Lab settings saved")
        }
    }

    fun saveSyncSettings(enabled: Boolean, url: String, secret: String) {
        viewModelScope.launch {
            val current = labSettings.value
            repository.saveLabSettings(
                current.copy(
                    syncEnabled = enabled,
                    syncUrl = url.trim(),
                    syncSecret = secret.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            showMessage(if (enabled) "Google Sheets sync enabled" else "Google Sheets sync disabled")
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            showMessage("Syncing to Google Sheets...")
            val result = syncService.syncNow()
            showMessage(if (result.success) "✔ ${result.message}" else "✖ ${result.message}")
        }
    }

    fun fullPush() {
        viewModelScope.launch {
            showMessage("Preparing full push of all data...")
            val result = syncService.fullPush()
            showMessage(if (result.success) "✔ ${result.message}" else "✖ ${result.message}")
        }
    }

    // --- Data management ---
    fun resetToDemoData() {
        viewModelScope.launch {
            showMessage("Rebuilding demo dataset (5 clinics, ~300 orders)...")
            repository.resetToSampleData()
            showMessage("Database reset to demo data")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            showMessage("All data cleared - the database is now empty")
        }
    }

    // Helper: format money in INR (Indian grouping)
    fun formatCurrency(amount: Double): String = MoneyUtils.formatINR(amount)

    fun formatDate(timeMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)
    fun currentMonth(): Int = Calendar.getInstance().get(Calendar.MONTH) + 1
}
