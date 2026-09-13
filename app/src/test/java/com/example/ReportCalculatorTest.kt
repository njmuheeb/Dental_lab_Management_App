package com.example

import com.example.data.model.Clinic
import com.example.data.model.ClinicStatement
import com.example.data.model.Payment
import com.example.data.model.StatementMath
import com.example.data.model.WorkOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Verifies the monthly clinic-account statement accounting identity:
 *
 *     Closing Outstanding = Previous Outstanding + Month Revenue - Payments Received
 *     Previous Outstanding = billedBefore - paidBefore
 *
 * plus month filtering and cancelled-work exclusion, using hand-computed fixtures.
 * Payments are clinic-account level (never tied to individual work orders).
 */
class ReportCalculatorTest {

    private fun millis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day, 12, 0, 0)
        return cal.timeInMillis
    }

    private val clinic = Clinic(id = 1, clinicCode = "CLN-101", name = "Apex Dental", dentistName = "Khan")

    private fun order(
        id: Long, date: Long, total: Double,
        status: String = "Delivered", units: Int = 1
    ) = WorkOrder(
        id = id, jobNumber = "NDL-2026-%04d".format(id), entryDate = date,
        expectedDeliveryDate = date + 86400000L, clinicId = 1, clinicName = clinic.name,
        patientId = id, patientName = "P$id", workTypeId = 1, workTypeName = "Crown",
        pricingModel = "UNIT_BASED", units = units, rate = total / units.coerceAtLeast(1),
        totalAmount = total, status = status
    )

    private fun payment(id: Long, date: Long, amount: Double) = Payment(
        id = id, clinicId = 1, amount = amount, paymentDate = date
    )

    @Test
    fun `june statement applies the closing balance formula`() {
        // Before June: billed 10,000 (2 orders); clinic payments before June: 4,000
        val o1 = order(1, millis(2026, 4, 10), 6000.0)
        val o2 = order(2, millis(2026, 5, 15), 4000.0)
        val p1 = payment(1, millis(2026, 5, 20), 4000.0)

        // June: 2 orders billed 8,100 + 6,400 = 14,500; combined clinic payments in June: 6,350
        val o3 = order(3, millis(2026, 6, 3), 8100.0)
        val o4 = order(4, millis(2026, 6, 18), 6400.0)
        val p2 = payment(2, millis(2026, 6, 26), 6350.0) // one combined monthly settlement

        // After June (must be ignored entirely)
        val o5 = order(5, millis(2026, 7, 5), 99999.0)
        val p4 = payment(4, millis(2026, 7, 6), 99999.0)

        val st = StatementMath.buildStatement(
            clinic = clinic,
            allOrders = listOf(o1, o2, o3, o4, o5),
            allPayments = listOf(p1, p2, p4),
            start = StatementMath.monthStart(2026, 6),
            endExclusive = StatementMath.monthEndExclusive(2026, 6)
        )

        assertEquals("June 2026", st.periodLabel)
        assertEquals(2, st.totalEntries)
        assertEquals(14500.0, st.totalRevenue, 1e-9)
        // previous outstanding = 10,000 billed before - 4,000 paid before
        assertEquals(6000.0, st.previousBalance, 1e-9)
        assertEquals(6350.0, st.paymentsReceived, 1e-9)
        // THE equation: 6,000 + 14,500 - 6,350 = 14,150
        assertEquals(14150.0, st.remainingBalance, 1e-9)
        assertEquals(
            st.previousBalance + st.totalRevenue - st.paymentsReceived,
            st.remainingBalance, 1e-9
        )
        assertEquals("ST-CLN-101-202606", st.statementNumber)
        assertEquals("BILL-CLN-101-202606", ClinicStatement.billNumberFor("CLN-101", 2026, 6))
    }

    @Test
    fun `cancelled work is excluded from billing but clinic payments always count`() {
        val ok = order(1, millis(2026, 6, 5), 5000.0)
        val cancelled = order(2, millis(2026, 6, 12), 7000.0, status = "Cancelled")
        // June combined clinic payment of 2,000 (account level - not per case)
        val pJune = payment(8, millis(2026, 6, 28), 2000.0)

        val st = StatementMath.buildStatement(
            clinic, listOf(ok, cancelled), listOf(pJune),
            StatementMath.monthStart(2026, 6), StatementMath.monthEndExclusive(2026, 6)
        )

        assertEquals(1, st.totalEntries)               // cancelled excluded from entries
        assertEquals(5000.0, st.totalRevenue, 1e-9)   // and from revenue
        assertEquals(2000.0, st.paymentsReceived, 1e-9) // account payment fully counted
        assertEquals(3000.0, st.remainingBalance, 1e-9)
    }

    @Test
    fun `advance payment creates negative previous balance credit`() {
        val juneOrder = order(1, millis(2026, 6, 5), 5000.0)
        // Advance payment made in May, before any work was billed
        val advance = payment(1, millis(2026, 5, 30), 2000.0)
        val st = StatementMath.buildStatement(
            clinic, listOf(juneOrder), listOf(advance),
            StatementMath.monthStart(2026, 6), StatementMath.monthEndExclusive(2026, 6)
        )
        assertEquals(0.0, st.previousBilled, 1e-9)
        assertEquals(2000.0, st.previousPaid, 1e-9)
        assertEquals(-2000.0, st.previousBalance, 1e-9) // credit carried forward
        // closing = -2000 + 5000 - 0
        assertEquals(3000.0, st.remainingBalance, 1e-9)
    }

    @Test
    fun `month bounds are start-inclusive end-exclusive`() {
        val start = StatementMath.monthStart(2026, 6)
        val end = StatementMath.monthEndExclusive(2026, 6)
        // June has 30 days
        assertEquals(30L * 24 * 60 * 60 * 1000, end - start)
        // Entry at the very last millisecond of June still counts
        val lastMoment = order(1, end - 1, 100.0)
        val st = StatementMath.buildStatement(clinic, listOf(lastMoment), emptyList(), start, end)
        assertEquals(1, st.totalEntries)
        // Entry at the first millisecond of July does not
        val firstJuly = order(2, end, 100.0)
        val st2 = StatementMath.buildStatement(clinic, listOf(firstJuly), emptyList(), start, end)
        assertEquals(0, st2.totalEntries)
    }

    @Test
    fun `lab totals exclude cancelled and use account payments`() {
        val orders = listOf(
            order(1, millis(2026, 6, 1), 1000.0),
            order(2, millis(2026, 6, 2), 2000.0),
            order(3, millis(2026, 6, 3), 5000.0, status = "Cancelled")
        )
        val payments = listOf(
            payment(1, millis(2026, 6, 10), 1500.0)
        )
        val totals = StatementMath.labTotals(orders, payments)
        assertEquals(3, totals.totalOrders)
        assertEquals(2, totals.billableOrders)
        assertEquals(3000.0, totals.billed, 1e-9)
        assertEquals(1500.0, totals.collected, 1e-9)
        assertEquals(1500.0, totals.outstanding, 1e-9)
    }

    @Test
    fun `empty month produces zero statement`() {
        val st = StatementMath.buildStatement(
            clinic, emptyList(), emptyList(),
            StatementMath.monthStart(2026, 1), StatementMath.monthEndExclusive(2026, 1)
        )
        assertEquals(0, st.totalEntries)
        assertEquals(0.0, st.totalRevenue, 1e-9)
        assertEquals(0.0, st.previousBalance, 1e-9)
        assertEquals(0.0, st.remainingBalance, 1e-9)
        assertTrue(st.orders.isEmpty())
    }

    @Test
    fun `all time statement covers complete history with special label and number`() {
        val o1 = order(1, millis(2024, 11, 10), 5000.0)
        val o2 = order(2, millis(2026, 6, 5), 8000.0)
        val p1 = payment(1, millis(2026, 6, 20), 3000.0)

        val st = StatementMath.buildStatement(clinic, listOf(o1, o2), listOf(p1), 0L, Long.MAX_VALUE)

        assertEquals("All Time", st.periodLabel)
        assertEquals("ST-CLN-101-ALL", st.statementNumber)
        assertEquals(2, st.totalEntries)
        assertEquals(13000.0, st.totalRevenue, 1e-9)
        assertEquals(0.0, st.previousBalance, 1e-9) // nothing before "the beginning"
        assertEquals(3000.0, st.paymentsReceived, 1e-9)
        assertEquals(10000.0, st.remainingBalance, 1e-9)
    }

    @Test
    fun `custom range statement uses range label and compact number`() {
        val before = order(1, millis(2026, 5, 31), 1000.0)      // excluded
        val inRange = order(2, millis(2026, 6, 10), 2000.0)     // included
        val after = order(3, millis(2026, 7, 1), 4000.0)        // excluded
        val pBefore = payment(1, millis(2026, 5, 15), 500.0)
        val pIn = payment(2, millis(2026, 6, 15), 700.0)

        val start = millis(2026, 6, 1)
        val end = millis(2026, 6, 21) // custom cut-off inside June
        val st = StatementMath.buildStatement(clinic, listOf(before, inRange, after), listOf(pBefore, pIn), start, end)

        assertEquals("01 Jun 2026 to 21 Jun 2026", st.periodLabel)
        assertEquals("ST-CLN-101-20260601-20260621", st.statementNumber)
        assertEquals(1, st.totalEntries)
        assertEquals(2000.0, st.totalRevenue, 1e-9)
        assertEquals(1000.0 - 500.0, st.previousBalance, 1e-9)
        assertEquals(700.0, st.paymentsReceived, 1e-9)
        assertEquals(1800.0, st.remainingBalance, 1e-9)
    }

    @Test
    fun `yearly bounds helpers cover a full calendar year`() {
        // 365 days +/- possible DST shift of one hour
        assertEquals(
            365.0 * 24 * 60 * 60 * 1000,
            (StatementMath.yearEndExclusive(2027) - StatementMath.yearStart(2027)).toDouble(), 3_600_000.0
        )
        val o = order(1, StatementMath.yearEndExclusive(2026) - 1, 100.0) // 31 Dec 2026 last moment
        val st = StatementMath.buildStatement(
            clinic, listOf(o), emptyList(),
            StatementMath.yearStart(2026), StatementMath.yearEndExclusive(2026)
        )
        assertEquals(1, st.totalEntries)
        // A full calendar year is NOT a calendar month -> range label
        assertTrue(st.periodLabel.contains("to"))
        assertTrue(st.statementNumber.contains("20260101"))
        assertTrue(st.statementNumber.contains("20261231"))
    }
}
