package com.example.data.repository

import com.example.data.database.AppDatabase
import com.example.data.database.DemoDataGenerator
import com.example.data.model.Clinic
import com.example.data.model.ClinicRate
import com.example.data.model.ClinicStatement
import com.example.data.model.LabSettings
import com.example.data.model.Patient
import com.example.data.model.Payment
import com.example.data.model.StatementMath
import com.example.data.model.WarrantyCard
import com.example.data.model.WorkOrder
import com.example.data.model.WorkType
import com.example.data.sync.SyncQueueEntry
import com.example.data.util.MoneyUtils
import com.example.data.util.ToothFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Single data-access facade for the whole app. Room/SQLite remains the ONLY source of truth
 * (offline-first). Every mutation funnels through this class so that:
 *  - the optional Google Sheets sync layer can track changes (pendingSync flags + delete
 *    tombstones in the sync_queue outbox),
 *  - referential integrity is guarded (clinics/patients/work types with history cannot be
 *    orphaned; deleting a work order cascades to its payments inside one transaction),
 *  - money math goes through [MoneyUtils] for consistency.
 */
class DentalLabRepository(private val database: AppDatabase) {
    private val clinicDao = database.clinicDao()
    private val patientDao = database.patientDao()
    private val workTypeDao = database.workTypeDao()
    private val clinicRateDao = database.clinicRateDao()
    private val workOrderDao = database.workOrderDao()
    private val paymentDao = database.paymentDao()
    private val labSettingsDao = database.labSettingsDao()
    private val syncQueueDao = database.syncQueueDao()
    private val warrantyDao = database.warrantyCardDao()

    // --- Clinics ---
    val allClinics: Flow<List<Clinic>> = clinicDao.getAllClinics()
    val activeClinics: Flow<List<Clinic>> = clinicDao.getActiveClinics()

    suspend fun getClinicById(id: Long): Clinic? = clinicDao.getClinicById(id)
    suspend fun insertClinic(clinic: Clinic): Long = withContext(Dispatchers.IO) { clinicDao.insertClinic(clinic) }
    suspend fun updateClinic(clinic: Clinic) = withContext(Dispatchers.IO) { clinicDao.updateClinic(clinic) }

    /** Blocked (returns false) when the clinic still has work orders or patients. */
    suspend fun deleteClinic(clinic: Clinic): Boolean = withContext(Dispatchers.IO) {
        val orderCount = workOrderDao.getCountForClinic(clinic.id)
        val patientCount = patientDao.getCountForClinic(clinic.id)
        if (orderCount > 0 || patientCount > 0) {
            false
        } else {
            clinicDao.deleteClinic(clinic)
            enqueueDelete("clinic", clinic.syncId)
            true
        }
    }

    // --- Patients ---
    val allPatients: Flow<List<Patient>> = patientDao.getAllPatients()
    fun getPatientsForClinic(clinicId: Long): Flow<List<Patient>> = patientDao.getPatientsForClinic(clinicId)
    suspend fun getPatientById(id: Long): Patient? = patientDao.getPatientById(id)
    suspend fun insertPatient(patient: Patient): Long = withContext(Dispatchers.IO) { patientDao.insertPatient(patient) }
    suspend fun updatePatient(patient: Patient) = withContext(Dispatchers.IO) { patientDao.updatePatient(patient) }

    /** Blocked (returns false) when the patient still has work orders. */
    suspend fun deletePatient(patient: Patient): Boolean = withContext(Dispatchers.IO) {
        if (workOrderDao.getCountForPatient(patient.id) > 0) {
            false
        } else {
            patientDao.deletePatient(patient)
            enqueueDelete("patient", patient.syncId)
            true
        }
    }

    // --- Work Types ---
    val allWorkTypes: Flow<List<WorkType>> = workTypeDao.getAllWorkTypes()
    val activeWorkTypes: Flow<List<WorkType>> = workTypeDao.getActiveWorkTypes()
    suspend fun getWorkTypeById(id: Long): WorkType? = workTypeDao.getWorkTypeById(id)
    suspend fun insertWorkType(workType: WorkType): Long = withContext(Dispatchers.IO) { workTypeDao.insertWorkType(workType) }
    suspend fun updateWorkType(workType: WorkType) = withContext(Dispatchers.IO) { workTypeDao.updateWorkType(workType) }

    /** Blocked (returns false) when the work type is used by orders or clinic rates. */
    suspend fun deleteWorkType(workType: WorkType): Boolean = withContext(Dispatchers.IO) {
        val orderCount = workOrderDao.getCountForWorkType(workType.id)
        val rateCount = clinicRateDao.getCountForWorkType(workType.id)
        if (orderCount > 0 || rateCount > 0) {
            false
        } else {
            workTypeDao.deleteWorkType(workType)
            enqueueDelete("work_type", workType.syncId)
            true
        }
    }

    // --- Clinic Rates ---
    fun getRatesForClinic(clinicId: Long): Flow<List<ClinicRate>> = clinicRateDao.getRatesForClinic(clinicId)
    val allRates: Flow<List<ClinicRate>> = clinicRateDao.getAllRates()
    suspend fun getEffectiveRate(clinicId: Long, workTypeId: Long): Pair<Double, String> {
        val customRate = clinicRateDao.getRate(clinicId, workTypeId)
        val workType = workTypeDao.getWorkTypeById(workTypeId)
        val pricingModel = customRate?.pricingModel ?: workType?.pricingModel ?: "UNIT_BASED"
        val rate = customRate?.customRate ?: workType?.defaultRate ?: 0.0
        return Pair(rate, pricingModel)
    }
    suspend fun setClinicRate(clinicId: Long, workTypeId: Long, rate: Double, pricingModel: String) {
        withContext(Dispatchers.IO) {
            val existing = clinicRateDao.getRate(clinicId, workTypeId)
            clinicRateDao.upsertRate(
                ClinicRate(
                    id = existing?.id ?: 0,
                    clinicId = clinicId,
                    workTypeId = workTypeId,
                    pricingModel = pricingModel,
                    customRate = rate,
                    updatedAt = System.currentTimeMillis(),
                    syncId = existing?.syncId ?: java.util.UUID.randomUUID().toString(),
                    pendingSync = true
                )
            )
        }
    }

    // --- Work Orders ---
    val allWorkOrders: Flow<List<WorkOrder>> = workOrderDao.getAllWorkOrders()
    fun getWorkOrdersForClinic(clinicId: Long): Flow<List<WorkOrder>> = workOrderDao.getWorkOrdersForClinic(clinicId)
    fun getWorkOrdersForPatient(patientId: Long): Flow<List<WorkOrder>> = workOrderDao.getWorkOrdersForPatient(patientId)
    suspend fun getWorkOrderById(id: Long): WorkOrder? = workOrderDao.getWorkOrderById(id)
    suspend fun getWorkOrderByJobNumber(jobNumber: String): WorkOrder? = workOrderDao.getWorkOrderByJobNumber(jobNumber)
    suspend fun getAllWorkOrdersForClinic(clinicId: Long): List<WorkOrder> = workOrderDao.getAllWorkOrdersForClinic(clinicId)
    suspend fun getAllPaymentsForClinic(clinicId: Long): List<Payment> = paymentDao.getAllPaymentsForClinic(clinicId)

    suspend fun generateNextJobNumber(): String = withContext(Dispatchers.IO) {
        val lastJob = workOrderDao.getLastJobNumber()
        val year = Calendar.getInstance().get(Calendar.YEAR)
        if (lastJob != null && lastJob.startsWith("NDL-$year-")) {
            val seqStr = lastJob.substringAfterLast("-")
            val seq = seqStr.toIntOrNull() ?: 0
            String.format("NDL-%d-%04d", year, seq + 1)
        } else {
            val count = workOrderDao.getCount()
            String.format("NDL-%d-%04d", year, count + 1)
        }
    }

    suspend fun createWorkOrder(workOrder: WorkOrder): Long = withContext(Dispatchers.IO) {
        val total = MoneyUtils.totalFor(workOrder.units, workOrder.rate, workOrder.pricingModel)
        workOrderDao.insertWorkOrder(workOrder.copy(totalAmount = total, pendingSync = true))
    }

    suspend fun updateWorkOrder(workOrder: WorkOrder) = withContext(Dispatchers.IO) {
        val total = MoneyUtils.totalFor(workOrder.units, workOrder.rate, workOrder.pricingModel)
        workOrderDao.updateWorkOrder(
            workOrder.copy(
                totalAmount = total,
                updatedAt = System.currentTimeMillis(),
                pendingSync = true
            )
        )
    }

    suspend fun updateWorkOrderStatus(id: Long, status: String) = withContext(Dispatchers.IO) {
        workOrderDao.updateStatus(id, status)
    }

    /** Deletes the order; clinic-account payments are unaffected (they are not case-wise). */
    suspend fun deleteWorkOrder(workOrder: WorkOrder) = withContext(Dispatchers.IO) {
        workOrderDao.deleteWorkOrder(workOrder)
        enqueueDelete("work_order", workOrder.syncId)
    }

    // --- Payments (clinic-account level) ---
    val allPayments: Flow<List<Payment>> = paymentDao.getAllPayments()
    fun getPaymentsForClinic(clinicId: Long): Flow<List<Payment>> = paymentDao.getPaymentsForClinic(clinicId)

    /** Records a combined clinic payment (never tied to an individual work order). */
    suspend fun recordPayment(payment: Payment): Long = withContext(Dispatchers.IO) {
        paymentDao.insertPayment(payment.copy(amount = MoneyUtils.round2(payment.amount)))
    }

    suspend fun deletePayment(payment: Payment) = withContext(Dispatchers.IO) {
        paymentDao.deletePayment(payment)
        enqueueDelete("payment", payment.syncId)
    }

    // --- Lab Settings ---
    val labSettings: Flow<LabSettings?> = labSettingsDao.getSettings()
    suspend fun getLabSettingsDirect(): LabSettings? = labSettingsDao.getSettingsDirect()
    suspend fun saveLabSettings(settings: LabSettings) = labSettingsDao.saveSettings(settings)

    // --- Statements / Reports ---

    /** Monthly statement for a clinic (previous balance + billing - payments = remaining). */
    suspend fun getClinicMonthlyStatement(clinicId: Long, year: Int, month: Int): ClinicStatement? =
        withContext(Dispatchers.IO) {
            val clinic = clinicDao.getClinicById(clinicId) ?: return@withContext null
            val orders = workOrderDao.getAllWorkOrdersForClinic(clinicId)
            val payments = paymentDao.getAllPaymentsForClinic(clinicId)
            StatementMath.buildStatement(
                clinic = clinic,
                allOrders = orders,
                allPayments = payments,
                start = StatementMath.monthStart(year, month),
                endExclusive = StatementMath.monthEndExclusive(year, month)
            )
        }

    /** Statement for an arbitrary date range (custom bills). */
    suspend fun getClinicStatementForRange(clinicId: Long, start: Long, endExclusive: Long): ClinicStatement? =
        withContext(Dispatchers.IO) {
            val clinic = clinicDao.getClinicById(clinicId) ?: return@withContext null
            val orders = workOrderDao.getAllWorkOrdersForClinic(clinicId)
            val payments = paymentDao.getAllPaymentsForClinic(clinicId)
            StatementMath.buildStatement(clinic, orders, payments, start, endExclusive)
        }

    // --- Data management ---

    /** Clears all business data (clinics/patients/orders/payments/rates), keeps the app usable. */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        // Restore the minimal operating baseline: default lab settings + standard work types
        database.labSettingsDao().saveSettings(LabSettings())
        database.workTypeDao().insertAllWorkTypes(DemoDataGenerator.standardWorkTypes())
    }

    /** Clears everything and reloads the deterministic demo dataset. */
    suspend fun resetToSampleData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        AppDatabase.seedDemoData(database)
    }

    // --- Warranty Cards ---

    companion object {
        /** Generic, editable default terms printed on the warranty card back. */
        const val DEFAULT_WARRANTY_TERMS =
            "1. Covers eligible manufacturing defects of the restoration from the delivery date.\n" +
            "2. Valid only for the original patient; non-transferable.\n" +
            "3. Not covered: accidents, misuse, bruxism, poor oral hygiene, unauthorized repairs.\n" +
            "4. Six-monthly dental check-ups are mandatory to keep the warranty valid.\n" +
            "5. For claims, contact the laboratory/clinic with this card and the original invoice.\n" +
            "6. Final claim assessment rests with the laboratory per its warranty policy."

        /** Generic default care recommendations printed on the card back. */
        const val DEFAULT_CARE_INSTRUCTIONS =
            "Brush and floss daily around the restoration.\n" +
            "Avoid biting hard objects; use a night guard if you grind your teeth.\n" +
            "Visit your dentist every six months."

        /** Disclaimer printed on the card back - never a universal legal guarantee. */
        const val WARRANTY_DISCLAIMER =
            "Warranty terms are subject to the laboratory's actual policy and applicable agreements. " +
            "This card is not a substitute for professional dental advice."

        /** Expiry date = delivery date + [years] full calendar years. */
        fun warrantyExpiry(deliveryDate: Long, years: Int): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = deliveryDate
            cal.add(Calendar.YEAR, years)
            return cal.timeInMillis
        }
    }

    /** All warranty cards, newest first (drives the Warranty Cards screen). */
    val allWarrantyCards: Flow<List<WarrantyCard>> = warrantyDao.getAllWarrantyCards()

    suspend fun getWarrantyCardById(id: Long): WarrantyCard? =
        withContext(Dispatchers.IO) { warrantyDao.getById(id) }

    suspend fun deleteWarrantyCard(card: WarrantyCard) = withContext(Dispatchers.IO) {
        warrantyDao.delete(card)
    }

    /** Sequential card number, e.g. "WC-2026-0007" (count-based like job numbers). */
    private suspend fun nextWarrantyCardNumber(): String {
        val count = warrantyDao.getCount()
        return String.format("WC-%d-%04d", Calendar.getInstance().get(Calendar.YEAR), count + 1)
    }

    /**
     * Loads the existing warranty card for the work order, or creates an editable draft
     * prefilled from the work order, patient, clinic and lab settings.
     */
    suspend fun getOrCreateWarrantyCard(workOrder: WorkOrder): WarrantyCard? = withContext(Dispatchers.IO) {
        val existing = warrantyDao.getByWorkOrderId(workOrder.id)
        if (existing != null) return@withContext existing

        val clinic = clinicDao.getClinicById(workOrder.clinicId)
        val patient = patientDao.getPatientById(workOrder.patientId)
        val settings = labSettingsDao.getSettingsDirect() ?: LabSettings()
        val delivery = workOrder.actualDeliveryDate ?: workOrder.expectedDeliveryDate
        val years = settings.defaultWarrantyYears.coerceIn(1, 50)

        val draft = WarrantyCard(
            workOrderId = workOrder.id,
            workOrderNumber = workOrder.jobNumber,
            clinicId = workOrder.clinicId,
            clinicName = clinic?.name ?: workOrder.clinicName,
            patientId = workOrder.patientId,
            labName = settings.labName,
            labAddress = listOf(settings.address, settings.city).filter { it.isNotBlank() }.joinToString(", "),
            labPhone = settings.phone,
            patientName = patient?.name ?: workOrder.patientName,
            patientAddress = "",
            patientPhone = patient?.phone ?: "",
            workType = workOrder.workTypeName,
            material = workOrder.material,
            shade = workOrder.shade,
            toothNumbers = ToothFormat.formatLong(workOrder.selectedTeeth),
            consultantDoctor = "Dr. ${workOrder.dentistName.ifBlank { clinic?.dentistName ?: "" }}",
            deliveryDate = delivery,
            warrantyYears = years,
            warrantyExpiryDate = warrantyExpiry(delivery, years),
            cardNumber = nextWarrantyCardNumber(),
            terms = DEFAULT_WARRANTY_TERMS,
            careInstructions = DEFAULT_CARE_INSTRUCTIONS
        )
        val id = warrantyDao.insert(draft)
        warrantyDao.getById(id)
    }

    /**
     * Creates a standalone (manually entered) warranty card. Optionally linked to an
     * existing clinic/patient when the user picked them; otherwise the patient details
     * are typed by hand in the editor and the ids stay 0 (no record linkage).
     */
    suspend fun createManualWarrantyCard(clinic: Clinic?, patient: Patient?): WarrantyCard =
        withContext(Dispatchers.IO) {
            val settings = labSettingsDao.getSettingsDirect() ?: LabSettings()
            val delivery = System.currentTimeMillis()
            val years = settings.defaultWarrantyYears.coerceIn(1, 50)
            val draft = WarrantyCard(
                workOrderId = null,
                workOrderNumber = "",
                clinicId = clinic?.id ?: 0L,
                clinicName = clinic?.name ?: "",
                patientId = patient?.id ?: 0L,
                labName = settings.labName,
                labAddress = listOf(settings.address, settings.city).filter { it.isNotBlank() }.joinToString(", "),
                labPhone = settings.phone,
                patientName = patient?.name ?: "",
                patientAddress = "",
                patientPhone = patient?.phone ?: "",
                consultantDoctor = clinic?.dentistName?.takeIf { it.isNotBlank() }?.let { "Dr. $it" } ?: "",
                deliveryDate = delivery,
                warrantyYears = years,
                warrantyExpiryDate = warrantyExpiry(delivery, years),
                cardNumber = nextWarrantyCardNumber(),
                terms = DEFAULT_WARRANTY_TERMS,
                careInstructions = DEFAULT_CARE_INSTRUCTIONS
            )
            val id = warrantyDao.insert(draft)
            warrantyDao.getById(id)!!
        }

    /** Inserts or updates a card; the expiry date is always recomputed from delivery + years. */
    suspend fun saveWarrantyCard(card: WarrantyCard): Long = withContext(Dispatchers.IO) {
        val computed = card.copy(
            warrantyExpiryDate = warrantyExpiry(card.deliveryDate, card.warrantyYears),
            updatedAt = System.currentTimeMillis(),
            pendingSync = true
        )
        warrantyDao.insert(computed) // REPLACE upsert keyed by id / unique workOrderId
    }

    suspend fun getWarrantyCardForWorkOrder(workOrderId: Long): WarrantyCard? =
        withContext(Dispatchers.IO) { warrantyDao.getByWorkOrderId(workOrderId) }

    // --- Optional Google Sheets sync plumbing (inert while disabled) ---

    /** Total number of local rows/tombstones waiting to be pushed (for the Settings UI). */
    val pendingSyncCount: Flow<Int> = combine(
        clinicDao.pendingSyncCount(),
        patientDao.pendingSyncCount(),
        workTypeDao.pendingSyncCount(),
        clinicRateDao.pendingSyncCount(),
        workOrderDao.pendingSyncCount(),
        paymentDao.pendingSyncCount(),
        syncQueueDao.countFlow()
    ) { counts -> counts.sum() }

    private suspend fun enqueueDelete(entityType: String, syncId: String) {
        syncQueueDao.enqueue(
            SyncQueueEntry(entityType = entityType, entitySyncId = syncId, operation = "DELETE")
        )
    }
}
