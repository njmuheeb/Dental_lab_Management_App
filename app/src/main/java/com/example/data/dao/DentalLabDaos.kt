package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClinicDao {
    @Query("SELECT * FROM clinics ORDER BY name ASC")
    fun getAllClinics(): Flow<List<Clinic>>

    @Query("SELECT * FROM clinics WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveClinics(): Flow<List<Clinic>>

    @Query("SELECT * FROM clinics WHERE id = :id")
    suspend fun getClinicById(id: Long): Clinic?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClinic(clinic: Clinic): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllClinics(clinics: List<Clinic>)

    @Update
    suspend fun updateClinic(clinic: Clinic)

    @Delete
    suspend fun deleteClinic(clinic: Clinic)

    @Query("SELECT COUNT(*) FROM clinics")
    suspend fun getCount(): Int

    // --- Sync support ---
    @Query("SELECT * FROM clinics WHERE pendingSync = 1")
    suspend fun getPendingSyncClinics(): List<Clinic>

    @Query("SELECT COUNT(*) FROM clinics WHERE pendingSync = 1")
    fun pendingSyncCount(): Flow<Int>

    @Query("UPDATE clinics SET pendingSync = 0 WHERE pendingSync = 1")
    suspend fun clearPendingSyncFlag()

    @Query("UPDATE clinics SET pendingSync = 1")
    suspend fun markAllPendingSync()
}

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients ORDER BY name ASC")
    fun getAllPatients(): Flow<List<Patient>>

    @Query("SELECT * FROM patients WHERE clinicId = :clinicId ORDER BY name ASC")
    fun getPatientsForClinic(clinicId: Long): Flow<List<Patient>>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getPatientById(id: Long): Patient?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: Patient): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPatients(patients: List<Patient>)

    @Update
    suspend fun updatePatient(patient: Patient)

    @Delete
    suspend fun deletePatient(patient: Patient)

    @Query("SELECT COUNT(*) FROM patients WHERE clinicId = :clinicId")
    suspend fun getCountForClinic(clinicId: Long): Int

    // --- Sync support ---
    @Query("SELECT * FROM patients WHERE pendingSync = 1")
    suspend fun getPendingSyncPatients(): List<Patient>

    @Query("SELECT COUNT(*) FROM patients WHERE pendingSync = 1")
    fun pendingSyncCount(): Flow<Int>

    @Query("UPDATE patients SET pendingSync = 0 WHERE pendingSync = 1")
    suspend fun clearPendingSyncFlag()

    @Query("UPDATE patients SET pendingSync = 1")
    suspend fun markAllPendingSync()
}

@Dao
interface WorkTypeDao {
    @Query("SELECT * FROM work_types ORDER BY category ASC, name ASC")
    fun getAllWorkTypes(): Flow<List<WorkType>>

    @Query("SELECT * FROM work_types WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveWorkTypes(): Flow<List<WorkType>>

    @Query("SELECT * FROM work_types WHERE id = :id")
    suspend fun getWorkTypeById(id: Long): WorkType?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkType(workType: WorkType): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllWorkTypes(workTypes: List<WorkType>)

    @Update
    suspend fun updateWorkType(workType: WorkType)

    @Delete
    suspend fun deleteWorkType(workType: WorkType)

    @Query("SELECT COUNT(*) FROM work_types")
    suspend fun getCount(): Int

    // --- Sync support ---
    @Query("SELECT * FROM work_types WHERE pendingSync = 1")
    suspend fun getPendingSyncWorkTypes(): List<WorkType>

    @Query("SELECT COUNT(*) FROM work_types WHERE pendingSync = 1")
    fun pendingSyncCount(): Flow<Int>

    @Query("UPDATE work_types SET pendingSync = 0 WHERE pendingSync = 1")
    suspend fun clearPendingSyncFlag()

    @Query("UPDATE work_types SET pendingSync = 1")
    suspend fun markAllPendingSync()
}

@Dao
interface ClinicRateDao {
    @Query("SELECT * FROM clinic_rates WHERE clinicId = :clinicId")
    fun getRatesForClinic(clinicId: Long): Flow<List<ClinicRate>>

    @Query("SELECT * FROM clinic_rates")
    fun getAllRates(): Flow<List<ClinicRate>>

    @Query("SELECT * FROM clinic_rates WHERE clinicId = :clinicId AND workTypeId = :workTypeId LIMIT 1")
    suspend fun getRate(clinicId: Long, workTypeId: Long): ClinicRate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRate(clinicRate: ClinicRate): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRates(clinicRates: List<ClinicRate>)

    @Query("DELETE FROM clinic_rates WHERE clinicId = :clinicId AND workTypeId = :workTypeId")
    suspend fun deleteRate(clinicId: Long, workTypeId: Long)

    @Query("SELECT COUNT(*) FROM clinic_rates WHERE workTypeId = :workTypeId")
    suspend fun getCountForWorkType(workTypeId: Long): Int

    // --- Sync support ---
    @Query("SELECT * FROM clinic_rates WHERE pendingSync = 1")
    suspend fun getPendingSyncRates(): List<ClinicRate>

    @Query("SELECT COUNT(*) FROM clinic_rates WHERE pendingSync = 1")
    fun pendingSyncCount(): Flow<Int>

    @Query("UPDATE clinic_rates SET pendingSync = 0 WHERE pendingSync = 1")
    suspend fun clearPendingSyncFlag()

    @Query("UPDATE clinic_rates SET pendingSync = 1")
    suspend fun markAllPendingSync()
}

@Dao
interface WorkOrderDao {
    @Query("SELECT * FROM work_orders ORDER BY entryDate DESC, id DESC")
    fun getAllWorkOrders(): Flow<List<WorkOrder>>

    @Query("SELECT * FROM work_orders WHERE clinicId = :clinicId ORDER BY entryDate DESC")
    fun getWorkOrdersForClinic(clinicId: Long): Flow<List<WorkOrder>>

    @Query("SELECT * FROM work_orders WHERE clinicId = :clinicId ORDER BY entryDate ASC")
    suspend fun getAllWorkOrdersForClinic(clinicId: Long): List<WorkOrder>

    @Query("SELECT * FROM work_orders WHERE patientId = :patientId ORDER BY entryDate DESC")
    fun getWorkOrdersForPatient(patientId: Long): Flow<List<WorkOrder>>

    @Query("SELECT * FROM work_orders WHERE id = :id")
    suspend fun getWorkOrderById(id: Long): WorkOrder?

    @Query("SELECT * FROM work_orders WHERE jobNumber = :jobNumber LIMIT 1")
    suspend fun getWorkOrderByJobNumber(jobNumber: String): WorkOrder?

    @Query("SELECT jobNumber FROM work_orders ORDER BY id DESC LIMIT 1")
    suspend fun getLastJobNumber(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkOrder(workOrder: WorkOrder): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllWorkOrders(workOrders: List<WorkOrder>)

    @Update
    suspend fun updateWorkOrder(workOrder: WorkOrder)

    @Delete
    suspend fun deleteWorkOrder(workOrder: WorkOrder)

    @Query("UPDATE work_orders SET status = :status, updatedAt = :updatedAt, pendingSync = 1 WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM work_orders")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM work_orders WHERE clinicId = :clinicId")
    suspend fun getCountForClinic(clinicId: Long): Int

    @Query("SELECT COUNT(*) FROM work_orders WHERE patientId = :patientId")
    suspend fun getCountForPatient(patientId: Long): Int

    @Query("SELECT COUNT(*) FROM work_orders WHERE workTypeId = :workTypeId")
    suspend fun getCountForWorkType(workTypeId: Long): Int

    // --- Sync support ---
    @Query("SELECT * FROM work_orders WHERE pendingSync = 1")
    suspend fun getPendingSyncWorkOrders(): List<WorkOrder>

    @Query("SELECT COUNT(*) FROM work_orders WHERE pendingSync = 1")
    fun pendingSyncCount(): Flow<Int>

    @Query("UPDATE work_orders SET pendingSync = 0 WHERE pendingSync = 1")
    suspend fun clearPendingSyncFlag()

    @Query("UPDATE work_orders SET pendingSync = 1")
    suspend fun markAllPendingSync()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY paymentDate DESC, id DESC")
    fun getAllPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE clinicId = :clinicId ORDER BY paymentDate DESC")
    fun getPaymentsForClinic(clinicId: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE clinicId = :clinicId ORDER BY paymentDate ASC")
    suspend fun getAllPaymentsForClinic(clinicId: Long): List<Payment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPayments(payments: List<Payment>)

    @Update
    suspend fun updatePayment(payment: Payment)

    @Delete
    suspend fun deletePayment(payment: Payment)

    // --- Sync support ---
    @Query("SELECT * FROM payments WHERE pendingSync = 1")
    suspend fun getPendingSyncPayments(): List<Payment>

    @Query("SELECT COUNT(*) FROM payments WHERE pendingSync = 1")
    fun pendingSyncCount(): Flow<Int>

    @Query("UPDATE payments SET pendingSync = 0 WHERE pendingSync = 1")
    suspend fun clearPendingSyncFlag()

    @Query("UPDATE payments SET pendingSync = 1")
    suspend fun markAllPendingSync()
}

@Dao
interface LabSettingsDao {
    @Query("SELECT * FROM lab_settings WHERE id = 1")
    fun getSettings(): Flow<LabSettings?>

    @Query("SELECT * FROM lab_settings WHERE id = 1")
    suspend fun getSettingsDirect(): LabSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: LabSettings)
}

@Dao
interface WarrantyCardDao {
    @Query("SELECT * FROM warranty_cards WHERE workOrderId = :workOrderId LIMIT 1")
    suspend fun getByWorkOrderId(workOrderId: Long): WarrantyCard?

    @Query("SELECT * FROM warranty_cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WarrantyCard?

    @Query("SELECT * FROM warranty_cards ORDER BY createdAt DESC")
    fun getAllWarrantyCards(): Flow<List<WarrantyCard>>

    @Query("SELECT COUNT(*) FROM warranty_cards")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: WarrantyCard): Long

    @Update
    suspend fun update(card: WarrantyCard)

    @Delete
    suspend fun delete(card: WarrantyCard)
}
