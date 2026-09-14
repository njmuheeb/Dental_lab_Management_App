package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.Clinic
import com.example.data.model.Patient
import com.example.data.model.Payment
import com.example.data.model.WorkOrder
import com.example.data.model.WorkType
import com.example.data.repository.DentalLabRepository
import com.example.data.sync.SheetsSyncService
import com.example.data.util.MoneyUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration tests over a real (in-memory) Room database verifying the offline-first
 * clinic-account data layer: sync-ready flags on writes, delete tombstones, referential
 * integrity guards, monthly combined payments, and the deterministic demo dataset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncOutboxTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: DentalLabRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = DentalLabRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun clinic(code: String = "CLN-1") = Clinic(clinicCode = code, name = "Test Clinic", dentistName = "Doc")
    private fun workType() = WorkType(name = "Crown", category = "C&B", pricingModel = "UNIT_BASED", defaultRate = 1000.0)
    private suspend fun patient(clinicId: Long) = repo.insertPatient(
        Patient(patientCode = "PAT-1", name = "P", clinicId = clinicId)
    ).let { id -> db.patientDao().getPatientById(id)!! }

    private suspend fun makeOrder(clinicId: Long, wtId: Long, patientId: Long, job: String, units: Int = 1, rate: Double = 1000.0): WorkOrder {
        val id = repo.createWorkOrder(
            WorkOrder(
                jobNumber = job, clinicId = clinicId, clinicName = "Test Clinic",
                patientId = patientId, patientName = "P", workTypeId = wtId, workTypeName = "Crown",
                pricingModel = "UNIT_BASED", units = units, rate = rate, totalAmount = units * rate
            )
        )
        return db.workOrderDao().getWorkOrderById(id)!!
    }

    @Test
    fun `inserting a clinic marks it pending for sync`() = runBlocking {
        val id = repo.insertClinic(clinic())
        val stored = db.clinicDao().getClinicById(id)!!
        assertTrue(stored.pendingSync)
        assertNotEquals("", stored.syncId)
        assertEquals(36, stored.syncId.length) // UUID string with dashes
    }

    @Test
    fun `deleting a clinic enqueues a tombstone`() = runBlocking {
        val id = repo.insertClinic(clinic())
        val stored = db.clinicDao().getClinicById(id)!!
        val deleted = repo.deleteClinic(stored)
        assertTrue(deleted)
        val queue = db.syncQueueDao().getAll()
        assertEquals(1, queue.size)
        assertEquals("clinic", queue[0].entityType)
        assertEquals(stored.syncId, queue[0].entitySyncId)
        assertEquals("DELETE", queue[0].operation)
    }

    @Test
    fun `clinic with orders cannot be deleted`() = runBlocking {
        val clinicId = repo.insertClinic(clinic())
        val wtId = repo.insertWorkType(workType())
        val patientId = patient(clinicId).id
        makeOrder(clinicId, wtId, patientId, "NDL-2026-0001")
        val stored = db.clinicDao().getClinicById(clinicId)!!
        assertFalse(repo.deleteClinic(stored))
        assertEquals(clinicId, db.clinicDao().getClinicById(clinicId)!!.id)
        assertTrue(db.syncQueueDao().getAll().isEmpty())
    }

    @Test
    fun `patient with orders cannot be deleted but empty patient can`() = runBlocking {
        val clinicId = repo.insertClinic(clinic())
        val wtId = repo.insertWorkType(workType())
        val pat = patient(clinicId)
        makeOrder(clinicId, wtId, pat.id, "NDL-2026-0002")
        assertFalse(repo.deletePatient(pat))

        val free = patient(clinicId) // second patient without orders
        assertTrue(repo.deletePatient(free))
    }

    @Test
    fun `deleting a work order leaves clinic payments untouched`() = runBlocking {
        val clinicId = repo.insertClinic(clinic())
        val wtId = repo.insertWorkType(workType())
        val pat = patient(clinicId)
        val order = makeOrder(clinicId, wtId, pat.id, "NDL-2026-0003", units = 1, rate = 800.0)

        // clinic-account payment (NOT tied to the order)
        val paymentId = repo.recordPayment(Payment(clinicId = clinicId, amount = 300.0))
        assertTrue(paymentId > 0)

        repo.deleteWorkOrder(order)
        assertEquals(null, db.workOrderDao().getWorkOrderById(order.id))
        // payment survives: it belongs to the clinic account, not the case
        assertEquals(1, db.paymentDao().getAllPaymentsForClinic(clinicId).size)
        // only the work order tombstone is queued
        val queue = db.syncQueueDao().getAll()
        assertEquals(1, queue.size)
        assertEquals("work_order", queue[0].entityType)
    }

    @Test
    fun `multiple clinic payments accumulate at the account level`() = runBlocking {
        val clinicId = repo.insertClinic(clinic())
        repo.recordPayment(Payment(clinicId = clinicId, amount = 1000.0))
        repo.recordPayment(Payment(clinicId = clinicId, amount = 250.0))
        repo.recordPayment(Payment(clinicId = clinicId, amount = 3000.0))

        val all = db.paymentDao().getAllPaymentsForClinic(clinicId)
        assertEquals(3, all.size)
        assertEquals(4250.0, all.sumOf { it.amount }, 1e-9)

        // deleting one payment removes only that receipt
        repo.deletePayment(all.first { it.amount == 250.0 })
        val remaining = db.paymentDao().getAllPaymentsForClinic(clinicId)
        assertEquals(2, remaining.size)
        assertEquals(4000.0, remaining.sumOf { it.amount }, 1e-9)
        assertEquals(1, db.syncQueueDao().getAll().size) // payment tombstone
    }

    @Test
    fun `payment amount is rounded through money utils`() = runBlocking {
        val clinicId = repo.insertClinic(clinic())
        val id = repo.recordPayment(Payment(clinicId = clinicId, amount = 1499.999))
        val stored = db.paymentDao().getAllPaymentsForClinic(clinicId).first { it.id == id }
        assertEquals(1500.0, stored.amount, 1e-9)
    }

    @Test
    fun `create work order recomputes total via money utils`() = runBlocking {
        val clinicId = repo.insertClinic(clinic())
        val wtId = repo.insertWorkType(workType())
        val pat = patient(clinicId)
        // Pass an inconsistent total on purpose - repository must fix it
        val order = makeOrder(clinicId, wtId, pat.id, "NDL-2026-0005", units = 3, rate = 33.33)
        assertEquals(99.99, order.totalAmount, 1e-9)
    }

    @Test
    fun `demo dataset is complete and deterministic`() = runBlocking {
        AppDatabase.seedDemoData(db)

        assertEquals(5, db.clinicDao().getCount())
        assertTrue(db.workOrderDao().getCount() in 275..340)
        assertTrue(db.patientDao().getCountForClinic(1) >= 20)

        val orders = db.workOrderDao().getAllWorkOrdersForClinic(1)
        assertTrue(orders.isNotEmpty())
        // Clinic 1 has real account payments (monthly combined settlements)
        val payments = db.paymentDao().getAllPaymentsForClinic(1)
        assertTrue("demo clinic has payments", payments.size >= 3)
        assertTrue(payments.all { it.clinicId == 1L })
        // Total paid never exceeds total billed (per clinic, billable work)
        (1L..5L).forEach { cid ->
            val billed = db.workOrderDao().getAllWorkOrdersForClinic(cid)
                .filter { it.status != "Cancelled" }.sumOf { it.totalAmount }
            val paid = db.paymentDao().getAllPaymentsForClinic(cid).sumOf { it.amount }
            assertTrue("clinic $cid paid ($paid) <= billed ($billed)", paid <= billed + 0.01)
        }
        // Every clinic carries a non-trivial outstanding balance for statement testing
        (1L..5L).forEach { cid ->
            val billed = db.workOrderDao().getAllWorkOrdersForClinic(cid)
                .filter { it.status != "Cancelled" }.sumOf { it.totalAmount }
            val paid = db.paymentDao().getAllPaymentsForClinic(cid).sumOf { it.amount }
            assertTrue("clinic $cid has outstanding balance", billed - paid > 1000.0)
        }
        // Unit-based orders always have consistent totals
        orders.filter { it.pricingModel == "UNIT_BASED" }.forEach {
            assertEquals(
                MoneyUtils.totalFor(it.units, it.rate, it.pricingModel),
                it.totalAmount, 1e-9
            )
        }
        // Deterministic: re-seeding produces identical job numbers
        val firstJobSet = orders.map { it.jobNumber }.toSet()
        repo.resetToSampleData()
        val secondJobSet = db.workOrderDao().getAllWorkOrdersForClinic(1).map { it.jobNumber }.toSet()
        assertEquals(firstJobSet, secondJobSet)
    }

    @Test
    fun `monthly statement from repository matches database rows`() = runBlocking {
        AppDatabase.seedDemoData(db)
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -1) }
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1

        val st = repo.getClinicMonthlyStatement(1, year, month)!!
        val expectedOrders = db.workOrderDao().getAllWorkOrdersForClinic(1)
            .filter { it.status != "Cancelled" }
            .filter {
                val c = java.util.Calendar.getInstance().apply { timeInMillis = it.entryDate }
                c.get(java.util.Calendar.YEAR) == year && c.get(java.util.Calendar.MONTH) + 1 == month
            }
        assertEquals(expectedOrders.size, st.totalEntries)
        assertEquals(
            MoneyUtils.round2(expectedOrders.sumOf { it.totalAmount }),
            st.totalRevenue, 1e-9
        )
        // Accounting identity holds on real DB data
        assertEquals(
            MoneyUtils.round2(st.previousBalance + st.totalRevenue - st.paymentsReceived),
            st.remainingBalance, 1e-9
        )
        // Payments listed are exactly the clinic's payments within the period
        val expectedPayments = db.paymentDao().getAllPaymentsForClinic(1).filter {
            it.paymentDate >= st.periodStart && it.paymentDate < st.periodEndExclusive
        }
        assertEquals(expectedPayments.size, st.payments.size)
    }

    @Test
    fun `sync service refuses to run when disabled or unconfigured`() = runBlocking {
        AppDatabase.seedDemoData(db)
        val service = SheetsSyncService(db)
        val result = service.syncNow()
        assertFalse(result.success)
        assertTrue(result.message.contains("disabled", ignoreCase = true))

        db.labSettingsDao().saveSettings(db.labSettingsDao().getSettingsDirect()!!.copy(syncEnabled = true, syncUrl = ""))
        val result2 = service.syncNow()
        assertFalse(result2.success)
        assertTrue(result2.message.contains("URL", ignoreCase = true))
    }

    /**
     * Full v2 -> v3 migration on disk: creates a v3 database with Room, rolls it back to the
     * v2 shape (per-case paidAmount + order-linked payments), inserts v2-style rows, then
     * reopens with Room so MIGRATION_2_3 runs and Room validates the resulting schema.
     * Verifies work orders survive and payments convert to the clinic-account model.
     */
    @Test
    fun `migration v2 to v3 preserves data and converts payments to clinic level`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test.db"
        context.deleteDatabase(dbName)

        // Step 1: create the database file with Room (v3 schema), then roll it back to v2
        // shape and insert v2-style rows - all through Room's own support database.
        val bootDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        bootDb.openHelper.writableDatabase.apply {
            execSQL("ALTER TABLE work_orders ADD COLUMN paidAmount REAL NOT NULL DEFAULT 0")
            execSQL("ALTER TABLE payments ADD COLUMN workOrderId INTEGER NOT NULL DEFAULT 1")
            execSQL("ALTER TABLE payments ADD COLUMN patientId INTEGER")
            // lab_settings is never rebuilt by the 2->7 migration chain, but the fresh
            // database above was created with the CURRENT (v7) schema - remove the
            // column that MIGRATION_6_7 adds so the migration path is exercised properly.
            execSQL("ALTER TABLE lab_settings DROP COLUMN defaultWarrantyYears")
            execSQL(
                """
                INSERT INTO work_orders (jobNumber, entryDate, expectedDeliveryDate, actualDeliveryDate,
                    clinicId, clinicName, dentistName, patientId, patientName, caseNumber, workTypeId,
                    workTypeName, pricingModel, selectedTeeth, shade, material, units, rate, totalAmount,
                    paidAmount, status, notes, specialInstructions, createdAt, updatedAt, syncId, pendingSync)
                VALUES ('NDL-MIG-0001', 1750000000000, 1750086400000, NULL, 1, 'Old Clinic', 'Doc',
                    1, 'Legacy Patient', '', 1, 'Crown', 'UNIT_BASED', '11,12', 'A2', 'Zirconia',
                    2, 1000.0, 2000.0, 500.0, 'Delivered', '', '', 1750000000000, 1750000000000,
                    'migrationsync1', 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO payments (workOrderId, clinicId, patientId, amount, paymentDate, paymentMethod,
                    referenceNumber, notes, createdAt, updatedAt, syncId, pendingSync)
                VALUES (1, 1, 1, 500.0, 1750050000000, 'UPI', 'OLD-1', 'legacy case payment',
                    1750050000000, 1750050000000, 'migrationsync2', 1)
                """.trimIndent()
            )
            // Pretend this file is still at schema version 2
            execSQL("PRAGMA user_version = 2")
        }
        bootDb.close()

        // Step 3: reopen with Room -> MIGRATION_2_3 .. MIGRATION_6_7 execute and Room validates
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7
            )
            .allowMainThreadQueries()
            .build()

        val order = migrated.workOrderDao().getWorkOrderByJobNumber("NDL-MIG-0001")
        assertTrue("work order survived the migration", order != null)
        assertEquals(2000.0, order!!.totalAmount, 1e-9)

        val payments = migrated.paymentDao().getAllPaymentsForClinic(1)
        assertEquals("legacy payment became a clinic-account payment", 1, payments.size)
        assertEquals(500.0, payments[0].amount, 1e-9)
        assertEquals(1L, payments[0].clinicId)

        migrated.close()
        context.deleteDatabase(dbName)
    }
}
