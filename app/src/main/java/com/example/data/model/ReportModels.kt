package com.example.data.model

import com.example.data.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A monthly (or arbitrary range) clinic statement. The core accounting identity enforced
 * everywhere in the app:
 *
 *     Closing Outstanding = Previous Outstanding Balance + Current Month Revenue - Payments Received
 *
 * where Previous Outstanding = (billed before period) - (paid before period), computed from
 * real work order and clinic-payment rows - never from UI-only values.
 */
data class ClinicStatement(
    val clinic: Clinic,
    val statementNumber: String,
    val statementDate: Long,
    val periodStart: Long,
    val periodEndExclusive: Long,
    val periodLabel: String,
    val orders: List<WorkOrder>,
    val payments: List<Payment>,
    val totalEntries: Int,
    val totalUnits: Int,
    val totalRevenue: Double,
    val previousBilled: Double,
    val previousPaid: Double,
    val previousBalance: Double,
    val paymentsReceived: Double,
    val remainingBalance: Double
) {
    val balanceFormula: String
        get() = "Closing Outstanding = Previous Outstanding + Current Month Revenue - Payments Received"

    companion object {
        fun statementNumberFor(clinicCode: String, year: Int, month: Int): String =
            "ST-${clinicCode}-${year}${month.toString().padStart(2, '0')}"

        fun billNumberFor(clinicCode: String, year: Int, month: Int): String =
            "BILL-${clinicCode}-${year}${month.toString().padStart(2, '0')}"
    }
}

/** Pure-Kotlin statement / monthly-report math. Fully unit-testable without Android. */
object StatementMath {

    private val rangeDateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val compactDateFmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    /** Inclusive start millis of the month (1-based) at 00:00 local time. */
    fun monthStart(year: Int, month: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, 1, 0, 0, 0)
        return cal.timeInMillis
    }

    /** Exclusive end millis (first millisecond of the next month). */
    fun monthEndExclusive(year: Int, month: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, 1, 0, 0, 0)
        cal.add(Calendar.MONTH, 1)
        return cal.timeInMillis
    }

    /** Inclusive start of a calendar year at 00:00 local time. */
    fun yearStart(year: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
        return cal.timeInMillis
    }

    /** Exclusive end of a calendar year (first millisecond of Jan 1 next year). */
    fun yearEndExclusive(year: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
        cal.add(Calendar.YEAR, 1)
        return cal.timeInMillis
    }

    /** End of day (exclusive) for custom "to" date pickers. */
    fun endOfDay(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis + 1
    }

    fun monthLabel(year: Int, month: Int): String {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return sdf.format(Date(monthStart(year, month)))
    }

    /** Cancelled work is never billed. */
    fun isBillable(order: WorkOrder): Boolean = order.status != "Cancelled"

    /**
     * Build a clinic statement for [start, endExclusive) from the clinic's full work order
     * and payment history. Payments are clinic-account level (clinics settle monthly with
     * combined amounts); cancelled work is never billed. All amounts pass through
     * [MoneyUtils.round2].
     */
    fun buildStatement(
        clinic: Clinic,
        allOrders: List<WorkOrder>,
        allPayments: List<Payment>,
        start: Long,
        endExclusive: Long,
        statementDate: Long = System.currentTimeMillis()
    ): ClinicStatement {
        val billable = allOrders.filter { isBillable(it) }

        val ordersInPeriod = billable
            .filter { it.entryDate >= start && it.entryDate < endExclusive }
            .sortedBy { it.entryDate }

        val paymentsInPeriod = allPayments
            .filter { it.paymentDate >= start && it.paymentDate < endExclusive }
            .sortedBy { it.paymentDate }

        val totalRevenue = MoneyUtils.round2(ordersInPeriod.sumOf { it.totalAmount })
        val totalUnits = ordersInPeriod.sumOf { it.units }
        val paymentsReceived = MoneyUtils.round2(paymentsInPeriod.sumOf { it.amount })

        val previousBilled = MoneyUtils.round2(billable.filter { it.entryDate < start }.sumOf { it.totalAmount })
        val previousPaid = MoneyUtils.round2(allPayments.filter { it.paymentDate < start }.sumOf { it.amount })
        val previousBalance = MoneyUtils.round2(previousBilled - previousPaid)

        val remainingBalance = MoneyUtils.round2(previousBalance + totalRevenue - paymentsReceived)

        // Period identity: calendar month -> "June 2026" / ST-<code>-YYYYMM; all time ->
        // "All Time" / ST-<code>-ALL; anything else -> "dd MMM yyyy to dd MMM yyyy" range.
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val isAllTime = start <= 0L && endExclusive >= Long.MAX_VALUE
        val isCalendarMonth = !isAllTime &&
            start == monthStart(year, month) && endExclusive == monthEndExclusive(year, month)

        val periodLabel = when {
            isAllTime -> "All Time"
            isCalendarMonth -> monthLabel(year, month)
            else -> "${rangeDateFmt.format(Date(start))} to ${rangeDateFmt.format(Date(endExclusive - 1))}"
        }
        val statementNumber = when {
            isAllTime -> "ST-${clinic.clinicCode}-ALL"
            isCalendarMonth -> ClinicStatement.statementNumberFor(clinic.clinicCode, year, month)
            else -> "ST-${clinic.clinicCode}-${compactDateFmt.format(Date(start))}-${compactDateFmt.format(Date(endExclusive - 1))}"
        }

        return ClinicStatement(
            clinic = clinic,
            statementNumber = statementNumber,
            statementDate = statementDate,
            periodStart = start,
            periodEndExclusive = endExclusive,
            periodLabel = periodLabel,
            orders = ordersInPeriod,
            payments = paymentsInPeriod,
            totalEntries = ordersInPeriod.size,
            totalUnits = totalUnits,
            totalRevenue = totalRevenue,
            previousBilled = previousBilled,
            previousPaid = previousPaid,
            previousBalance = previousBalance,
            paymentsReceived = paymentsReceived,
            remainingBalance = remainingBalance
        )
    }

    /** Aggregate dashboard/lab totals from all orders + clinic payments, excluding cancelled work. */
    fun labTotals(allOrders: List<WorkOrder>, allPayments: List<Payment>): LabTotals {
        val billable = allOrders.filter { isBillable(it) }
        val billed = MoneyUtils.round2(billable.sumOf { it.totalAmount })
        val collected = MoneyUtils.round2(allPayments.sumOf { it.amount })
        return LabTotals(
            billed = billed,
            collected = collected,
            outstanding = MoneyUtils.round2(billed - collected).coerceAtLeast(0.0),
            totalOrders = allOrders.size,
            billableOrders = billable.size,
            totalUnits = billable.sumOf { it.units }
        )
    }
}

data class LabTotals(
    val billed: Double,
    val collected: Double,
    val outstanding: Double,
    val totalOrders: Int,
    val billableOrders: Int,
    val totalUnits: Int
)
