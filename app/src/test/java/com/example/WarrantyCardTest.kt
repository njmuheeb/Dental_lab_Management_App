package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.Clinic
import com.example.data.model.LabSettings
import com.example.data.model.Patient
import com.example.data.model.WorkOrder
import com.example.data.model.WorkType
import com.example.data.repository.DentalLabRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * Warranty card lifecycle: draft creation from a work order (prefilled from clinic/patient/
 * lab settings), editing + saving (expiry always recomputed), retrieval for future
 * printing, and front/back PDF generation (two CR80-sized pages).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WarrantyCardTest {

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

    private suspend fun makeOrder(): WorkOrder {
        val clinicId = repo.insertClinic(
            Clinic(clinicCode = "CLN-9", name = "Apex Dental", dentistName = "Sameer Khan", phone = "+91 90000 00000", city = "Mumbai")
        )
        val patientId = repo.insertPatient(
            Patient(patientCode = "PAT-9", name = "Amina Shaikh", phone = "+91 91111 11111", clinicId = clinicId)
        )
        val wtId = repo.insertWorkType(
            WorkType(name = "Zirconia Crown", category = "Crown & Bridge", pricingModel = "UNIT_BASED", defaultRate = 2800.0)
        )
        val id = repo.createWorkOrder(
            WorkOrder(
                jobNumber = "NDL-W-0001", clinicId = clinicId, clinicName = "Apex Dental",
                dentistName = "Sameer Khan", patientId = patientId, patientName = "Amina Shaikh",
                workTypeId = wtId, workTypeName = "Zirconia Crown (Monolithic)",
                pricingModel = "UNIT_BASED", selectedTeeth = "11,12", shade = "A2",
                material = "Multilayer Zirconia", units = 2, rate = 2800.0, totalAmount = 5600.0,
                status = "Delivered"
            )
        )
        return db.workOrderDao().getWorkOrderById(id)!!
    }

    @Test
    fun `draft card is prefilled from work order, clinic, patient and lab settings`() = runBlocking {
        val order = makeOrder()
        val card = repo.getOrCreateWarrantyCard(order)

        assertNotNull(card)
        assertEquals(order.id, card!!.workOrderId)
        assertEquals("NDL-W-0001", card.workOrderNumber)
        assertEquals(order.clinicId, card.clinicId)
        assertEquals("Apex Dental", card.clinicName)
        assertEquals("Amina Shaikh", card.patientName)
        assertEquals("+91 91111 11111", card.patientPhone)
        assertEquals("Zirconia Crown (Monolithic)", card.workType)
        assertEquals("A2", card.shade)
        assertEquals("Multilayer Zirconia", card.material)
        // Teeth stored in quadrant single-digit notation (never raw FDI)
        assertEquals("Upper Right: 1, 2", card.toothNumbers)
        // Raw FDI list kept for the four-quadrant diagram
        assertEquals("11,12", card.selectedTeeth)
        assertEquals("Dr. Sameer Khan", card.consultantDoctor)
        assertEquals(10, card.warrantyYears)
        assertTrue(card.cardNumber.startsWith("WC-"))
        assertTrue(card.terms.contains("warranty", ignoreCase = true))
        assertTrue(card.careInstructions.isNotBlank())
        assertEquals("", card.notes)
        assertEquals(
            DentalLabRepository.warrantyExpiry(card.deliveryDate, 10),
            card.warrantyExpiryDate
        )
    }

    @Test
    fun `default warranty years from lab settings drive new card drafts`() = runBlocking {
        repo.saveLabSettings(LabSettings(defaultWarrantyYears = 7))
        val order = makeOrder()
        val card = repo.getOrCreateWarrantyCard(order)!!
        assertEquals(7, card.warrantyYears)
        assertEquals(
            DentalLabRepository.warrantyExpiry(card.deliveryDate, 7),
            card.warrantyExpiryDate
        )
    }

    @Test
    fun `manual card is created standalone with settings defaults and can be deleted`() = runBlocking {
        repo.saveLabSettings(LabSettings(defaultWarrantyYears = 5))
        val clinicId = repo.insertClinic(
            Clinic(clinicCode = "CLN-M", name = "Apex Dental", dentistName = "Sameer Khan")
        )
        val patientId = repo.insertPatient(
            Patient(patientCode = "PAT-M", name = "Amina Shaikh", phone = "+91 91111 11111", clinicId = clinicId)
        )
        val clinic = db.clinicDao().getClinicById(clinicId)!!
        val patient = db.patientDao().getPatientById(patientId)!!

        val card = repo.createManualWarrantyCard(clinic, patient)
        assertEquals(null, card.workOrderId)
        assertEquals("", card.workOrderNumber)
        assertEquals(clinicId, card.clinicId)
        assertEquals("Apex Dental", card.clinicName)
        assertEquals(patientId, card.patientId)
        assertEquals("Amina Shaikh", card.patientName)
        assertEquals("+91 91111 11111", card.patientPhone)
        assertEquals("Dr. Sameer Khan", card.consultantDoctor)
        assertEquals(5, card.warrantyYears) // from lab settings, not a hard-coded value
        assertEquals(
            DentalLabRepository.warrantyExpiry(card.deliveryDate, 5),
            card.warrantyExpiryDate
        )
        assertTrue(card.cardNumber.startsWith("WC-"))
        assertEquals(DentalLabRepository.DEFAULT_WARRANTY_TERMS, card.terms)
        assertEquals(DentalLabRepository.DEFAULT_CARE_INSTRUCTIONS, card.careInstructions)

        // Visible in the all-cards list (drives the Warranty Cards screen)
        val all = repo.allWarrantyCards.first()
        assertEquals(listOf(card.id), all.map { it.id })

        // Deletable
        repo.deleteWarrantyCard(card)
        assertTrue(repo.allWarrantyCards.first().isEmpty())
    }

    @Test
    fun `saving edits updates the card and recomputes expiry`() = runBlocking {
        val order = makeOrder()
        val draft = repo.getOrCreateWarrantyCard(order)!!

        val cal = Calendar.getInstance().apply { set(2026, Calendar.JUNE, 15, 12, 0, 0) }
        val edited = draft.copy(
            patientAddress = "12 Hill Road, Bandra West, Mumbai",
            warrantyYears = 5,
            deliveryDate = cal.timeInMillis,
            terms = "Custom terms for this case"
        )
        repo.saveWarrantyCard(edited)

        val reloaded = repo.getWarrantyCardForWorkOrder(order.id)
        assertNotNull(reloaded)
        assertEquals("12 Hill Road, Bandra West, Mumbai", reloaded!!.patientAddress)
        assertEquals(5, reloaded.warrantyYears)
        assertEquals("Custom terms for this case", reloaded.terms)
        // expiry = June 2026 + 5 years
        val expectedExpiry = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            add(Calendar.YEAR, 5)
        }.timeInMillis
        assertEquals(expectedExpiry, reloaded.warrantyExpiryDate)

        // Second getOrCreate returns the SAME stored card (retrievable, not a new draft)
        val again = repo.getOrCreateWarrantyCard(order)
        assertEquals(reloaded.id, again!!.id)
        assertEquals(5, again.warrantyYears)
    }

    @Test
    fun `expiry helper advances by full calendar years`() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.FEBRUARY, 28, 0, 0, 0) }
        val expiry = DentalLabRepository.warrantyExpiry(cal.timeInMillis, 1)
        val expCal = Calendar.getInstance().apply { timeInMillis = expiry }
        assertEquals(2027, expCal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, expCal.get(Calendar.MONTH))
    }

    @Test
    fun `saved card survives database close and reopen (app restart)`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getDatabasePath("warranty-persistence-test.db").let { file ->
            file.parentFile?.mkdirs()
            file.delete()
        }

        // --- First "run": create and edit the card, then close the database
        val db1 = Room.databaseBuilder(context, AppDatabase::class.java, "warranty-persistence-test.db")
            .allowMainThreadQueries()
            .build()
        val repo1 = DentalLabRepository(db1)
        val clinicId = repo1.insertClinic(
            Clinic(clinicCode = "CLN-P", name = "Apex Dental", dentistName = "Sameer Khan")
        )
        val patientId = repo1.insertPatient(
            Patient(patientCode = "PAT-P", name = "Amina Shaikh", phone = "+91 91111 11111", clinicId = clinicId)
        )
        val wtId = repo1.insertWorkType(
            WorkType(name = "Zirconia Crown", category = "Crown & Bridge", pricingModel = "UNIT_BASED", defaultRate = 2800.0)
        )
        val orderId = repo1.createWorkOrder(
            WorkOrder(
                jobNumber = "NDL-P-0001", clinicId = clinicId, clinicName = "Apex Dental",
                dentistName = "Sameer Khan", patientId = patientId, patientName = "Amina Shaikh",
                workTypeId = wtId, workTypeName = "Zirconia Crown", pricingModel = "UNIT_BASED",
                selectedTeeth = "11", units = 1, rate = 2800.0, totalAmount = 2800.0, status = "Delivered"
            )
        )
        val order = repo1.getWorkOrderById(orderId)!!
        val draft = repo1.getOrCreateWarrantyCard(order)!!
        repo1.saveWarrantyCard(
            draft.copy(
                patientAddress = "12 Hill Road, Bandra West, Mumbai",
                warrantyYears = 5,
                terms = "Persisted terms"
            )
        )
        db1.close()

        // --- Second "run": reopen the same database file - the card must still be there
        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, "warranty-persistence-test.db")
            .allowMainThreadQueries()
            .build()
        val reloaded = DentalLabRepository(db2).getWarrantyCardForWorkOrder(orderId)
        assertNotNull(reloaded)
        assertEquals(draft.id, reloaded!!.id)
        assertEquals("Apex Dental", reloaded.clinicName)
        assertEquals("Amina Shaikh", reloaded.patientName)
        assertEquals("12 Hill Road, Bandra West, Mumbai", reloaded.patientAddress)
        assertEquals("+91 91111 11111", reloaded.patientPhone)
        assertEquals("Persisted terms", reloaded.terms)
        assertEquals(5, reloaded.warrantyYears)
        assertEquals(
            DentalLabRepository.warrantyExpiry(reloaded.deliveryDate, 5),
            reloaded.warrantyExpiryDate
        )
        db2.close()
    }

    // NOTE: PdfDocument rendering (WarrantyCardPdfExporter.build) cannot be exercised under
    // Robolectric - the native PDF document creation returns 0 ("document is closed!") even
    // in NATIVE graphics mode. The exporter's full LAYOUT (page count, card size, wrapping,
    // bounds, completeness) is covered by WarrantyCardPdfLayoutTest; the final PDF bytes
    // must be verified on a device/emulator by tapping "Print / Export PDF".
}
