package com.example.data.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Central, reliable money calculations. Every monetary total in the app (work order totals,
 * payment sums, statement balances, dashboard aggregates, exports) is routed through these
 * helpers so results stay consistent across screens, statements and invoices.
 */
object MoneyUtils {

    /** Round to 2 decimal places using HALF_UP (standard for currency). */
    fun round2(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    /**
     * Total for a work order.
     * UNIT_BASED: units x rate. FIXED_PRICE: flat rate (units not used).
     */
    fun totalFor(units: Int, rate: Double, pricingModel: String): Double =
        if (pricingModel == "FIXED_PRICE") round2(rate)
        else round2(units.toDouble() * rate)

    /** Outstanding balance for a single order (never negative). */
    fun balanceDue(totalAmount: Double, paidAmount: Double): Double =
        round2(totalAmount - paidAmount).coerceAtLeast(0.0)

    /** Format with Indian digit grouping (12,34,567), showing paise only when present. */
    fun formatINR(amount: Double): String = "₹" + grouped(amount)

    /** Plain number with Indian grouping (no currency symbol) for CSV/XLSX cells. */
    fun plain(amount: Double): String = grouped(amount)

    /** e.g. 1234567 -> "12,34,567"; 1234.5 -> "1,234.50"; 0 -> "0". */
    private fun grouped(amount: Double): String {
        val negative = amount < 0
        val abs = Math.abs(round2(amount))
        val whole = abs.toLong()
        val paise = Math.round((abs - whole) * 100).toInt()
        val sb = StringBuilder()
        if (negative) sb.append('-')
        sb.append(indianGrouped(whole))
        if (paise != 0) {
            sb.append('.')
            if (paise < 10) sb.append('0')
            sb.append(paise)
        }
        return sb.toString()
    }

    /** Indian grouping: last 3 digits together, then groups of 2 (e.g. 1,00,00,000). */
    private fun indianGrouped(whole: Long): String {
        val s = java.lang.Long.toString(whole)
        if (s.length <= 3) return s
        val last3 = s.substring(s.length - 3)
        val rest = s.substring(0, s.length - 3)
        val parts = mutableListOf<String>()
        var i = rest.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            parts.add(0, rest.substring(start, i))
            i = start
        }
        return parts.joinToString(",") + "," + last3
    }
}
