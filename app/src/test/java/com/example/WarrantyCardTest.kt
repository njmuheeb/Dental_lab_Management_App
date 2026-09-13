package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.Clinic
import com.example.data.model.Patient
import com.example.data.model.WorkOrder
import com.example.data.model.WorkType
import com.example.data.repository.DentalLabRepository
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
        assertEquals(order.clinicId, card.clinicId)
        assertEquals("Amina Shaikh", card.patientName)
        assertEquals("+91 91111 11111", card.patientPhone)
        assertEquals("Zirconia Crown (Monolithic)", card.workType)
        assertEquals("A2", card.shade)
        assertEquals("Multilayer Zirconia", card.material)
        // Teeth stored in quadrant single-digit notation (never raw FDI)
        assertEquals("Upper Right: 1, 2", card.toothNumbers)
        assertEquals("Dr. Sameer Khan", card.consultantDoctor)
        assertEquals(10, card.warrantyYears)
        assertTrue(card.cardNumber.startsWith("WC-"))
        assertTrue(card.terms.contains("warranty", ignoreCase = true))
        assertEquals(
            DentalLabRepository.warrantyExpiry(card.deliveryDate, 10),
            card.warrantyExpiryDate
        )
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

    // NOTE: PdfDocument rendering (WarrantyCardPdfExporter.build) cannot be exercised under
    // Robolectric - the native PDF document creation returns 0 ("document is closed!") even
    // in NATIVE graphics mode. The exporter is compile-verified and must be verified on a
    // device/emulator by tapping "Print / Export PDF" on the warranty card screen.
}
