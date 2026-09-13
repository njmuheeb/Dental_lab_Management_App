package com.example

import com.example.data.model.Clinic
import com.example.data.model.Payment
import com.example.data.model.StatementMath
import com.example.data.model.WorkOrder
import com.example.export.CsvExporters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class CsvExportersTest {

    init {
        Locale.setDefault(Locale.US) // deterministic number formatting
    }

    private fun millis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day, 12, 0, 0)
        return cal.timeInMillis
    }

    private val clinic = Clinic(id = 1, clinicCode = "CLN-101", name = "Apex, Dental", dentistName = "Khan", city = "Mumbai")

    private fun order(id: Long, date: Long, total: Double, status: String = "Delivered") =
        WorkOrder(
            id = id, jobNumber = "NDL-2026-%04d".format(id), entryDate = date,
            expectedDeliveryDate = date, clinicId = 1, clinicName = clinic.name,
            patientId = id, patientName = "Patient $id", workTypeId = 1, workTypeName = "Zirconia Crown",
            pricingModel = "UNIT_BASED", selectedTeeth = "11,12", units = 2, rate = total / 2,
            totalAmount = total, status = status
        )

    @Test
    fun `work orders ledger contains every row, quadrant teeth and no payment columns`() {
        val orders = (1..40).map { order(it.toLong(), millis(2026, 6, 1 + (it % 20)), 1000.0 * it) }
        val csv = CsvExporters.workOrdersLedgerCsv(orders)

        // All 40 rows present
        orders.forEach { assertTrue(csv.contains(it.jobNumber)) }
        // Totals via Indian grouping
        val expectedBilled = orders.sumOf { it.totalAmount }
        assertTrue(csv.contains(com.example.data.util.MoneyUtils.plain(expectedBilled)))
        // Quoting protects commas in clinic name
        assertTrue(csv.contains("\"Apex, Dental\""))
        // Teeth in quadrant single-digit format, never raw FDI
        assertTrue(csv.contains("\"UR:1,2\""))
        assertFalse("raw FDI teeth must not appear", csv.contains("\"11,12\""))
        // NO case-wise payment columns
        assertFalse(csv.contains("Paid"))
        assertFalse(csv.contains("Balance"))
    }

    @Test
    fun `clinic directory lists every clinic with account balances`() {
        val clinics = listOf(
            clinic,
            Clinic(id = 2, clinicCode = "CLN-102", name = "Smile Craft", dentistName = "Sharma")
        )
        val orders = listOf(
            order(1, millis(2026, 6, 1), 5000.0),
            order(2, millis(2026, 6, 2), 3000.0)
        )
        // clinic 2 payment without any orders -> directory must still reflect it
        val payments = listOf(Payment(id = 1, clinicId = 2, amount = 500.0, paymentDate = millis(2026, 6, 10)))
        val csv = CsvExporters.clinicDirectoryCsv(clinics, orders, payments)

        assertTrue(csv.contains("CLN-101"))
        assertTrue(csv.contains("CLN-102"))
        // Quoting protects commas in clinic name
        assertTrue(csv.contains("\"Apex, Dental\""))
        // clinic 1 (billed 8000, no payments) has 8,000 due; clinic 2 has credit - shown 0
        val lines = csv.trim().lines()
        val clinic1Line = lines.last { it.contains("CLN-101") }
        assertTrue(clinic1Line.contains("8,000"))
    }

    @Test
    fun `statement csv matches the accounting equation and has no payment status column`() {
        val orders = listOf(
            order(1, millis(2026, 5, 10), 6000.0),
            order(2, millis(2026, 6, 5), 8100.0)
        )
        val payments = listOf(
            Payment(id = 1, clinicId = 1, amount = 4000.0, paymentDate = millis(2026, 5, 20)),
            Payment(id = 2, clinicId = 1, amount = 2000.0, paymentDate = millis(2026, 6, 15))
        )
        val st = StatementMath.buildStatement(
            clinic, orders, payments,
            StatementMath.monthStart(2026, 6), StatementMath.monthEndExclusive(2026, 6)
        )
        val csv = CsvExporters.clinicStatementCsv(st)

        assertTrue(csv.contains("ST-CLN-101-202606"))
        assertTrue(csv.contains("June 2026"))
        assertTrue(csv.contains("Previous Outstanding Balance"))
        assertTrue(csv.contains("Closing Outstanding Balance"))
        // 1 entry only (May order excluded by month filter); prev = 6000-4000 = 2000
        assertEquals(1, st.totalEntries)
        // closing = 2000 + 8100 - 2000 = 8100
        assertEquals(8100.0, st.remainingBalance, 1e-9)
        // 9-column layout: no case-wise payment status
        assertFalse("no case-wise payment status column", csv.contains("Payment Status"))
        assertFalse(csv.contains("Unpaid"))
        assertFalse(csv.contains("Partial"))
    }

    @Test
    fun `payments ledger includes all receipts, clinic names and total`() {
        val clinics = listOf(clinic)
        val payments = (1..25).map {
            Payment(id = it.toLong(), clinicId = 1, amount = 100.0 * it, paymentDate = millis(2026, 6, 2))
        }
        val csv = CsvExporters.paymentsLedgerCsv(payments, clinics)
        payments.forEach { assertTrue(csv.contains("\"${it.id}\"")) }
        assertTrue(csv.contains("Apex, Dental"))
        // Total = 100 * (1+2+...+25) = 32500
        assertTrue(csv.contains("32,500"))
    }

    @Test
    fun `csv fields escape double quotes`() {
        val csv = CsvExporters.paymentsLedgerCsv(
            listOf(Payment(id = 1, clinicId = 1, amount = 10.0, notes = "said \"thanks\"")),
            listOf(clinic)
        )
        assertTrue(csv.contains("\"said \"\"thanks\"\"\""))
    }
}
