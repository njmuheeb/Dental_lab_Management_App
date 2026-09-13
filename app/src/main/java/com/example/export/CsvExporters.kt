package com.example.export

import com.example.data.model.Clinic
import com.example.data.model.ClinicStatement
import com.example.data.model.Payment
import com.example.data.model.StatementMath
import com.example.data.model.WorkOrder
import com.example.data.util.MoneyUtils
import com.example.data.util.ToothFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure-Kotlin CSV builders used by the Import/Export hub and the clinic statement screen.
 * Output is Excel-friendly (quoted fields, totals row). Work tables follow the standard
 * 9-column layout with quadrant-based single-digit tooth numbers and NO case-wise payment
 * columns (payments are clinic-account level).
 */
object CsvExporters {

    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    fun formatDate(millis: Long): String = dateFmt.format(Date(millis))

    private fun esc(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun unitsText(order: WorkOrder): String =
        if (order.pricingModel == "UNIT_BASED") order.units.toString() else "Fixed"

    // ---- Clinic monthly statement CSV ----
    fun clinicStatementCsv(statement: ClinicStatement): String {
        val sb = StringBuilder()
        sb.append(esc("${statement.statementNumber} - Monthly Statement"), ",", esc(statement.clinic.name), "\n")
        sb.append(esc("Billing Period: ${statement.periodLabel} (${formatDate(statement.periodStart)} to ${formatDate(statement.periodEndExclusive - 1)})"), "\n")
        sb.append(esc("Statement Date: ${formatDate(statement.statementDate)}"), "\n")
        sb.append("\n")
        sb.append("S.No,Date,Patient Name,Work Type,Shade,Tooth Numbers,Units,Rate per Unit,Total Price\n")
        statement.orders.forEachIndexed { idx, o ->
            sb.append(
                listOf(
                    (idx + 1).toString(),
                    formatDate(o.entryDate),
                    o.patientName,
                    o.workTypeName,
                    o.shade,
                    ToothFormat.displayOrDash(o.selectedTeeth),
                    unitsText(o),
                    MoneyUtils.plain(o.rate),
                    MoneyUtils.plain(o.totalAmount)
                ).joinToString(",") { esc(it) } + "\n"
            )
        }
        sb.append("TOTAL,,,,,,${statement.totalUnits},,${MoneyUtils.plain(statement.totalRevenue)}\n")
        sb.append("\n")
        sb.append("Summary,Amount (INR)\n")
        sb.append(esc("Total Work Entries"),",${statement.totalEntries}\n")
        sb.append(esc("Total Units (month)"),",${statement.totalUnits}\n")
        sb.append(esc("Current Month Revenue"),",${MoneyUtils.plain(statement.totalRevenue)}\n")
        sb.append(esc("Previous Outstanding Balance"),",${MoneyUtils.plain(statement.previousBalance)}\n")
        sb.append(esc("Payments Received (month)"),",${MoneyUtils.plain(statement.paymentsReceived)}\n")
        sb.append(esc("Closing Outstanding Balance"),",${MoneyUtils.plain(statement.remainingBalance)}\n")
        return sb.toString()
    }

    // ---- Full work orders ledger (all rows; totals exclude cancelled) ----
    fun workOrdersLedgerCsv(orders: List<WorkOrder>): String {
        val sb = StringBuilder()
        sb.append("Job Number,Entry Date,Delivery Date,Clinic,Doctor,Patient,Work Type,Teeth,Model,Units,Rate,Total (INR),Status\n")
        orders.sortedBy { it.entryDate }.forEach { o ->
            sb.append(
                listOf(
                    o.jobNumber,
                    formatDate(o.entryDate),
                    formatDate(o.expectedDeliveryDate),
                    o.clinicName,
                    o.dentistName,
                    o.patientName,
                    o.workTypeName,
                    ToothFormat.displayOrDash(o.selectedTeeth),
                    if (o.pricingModel == "UNIT_BASED") "Unit-Based" else "Fixed-Price",
                    unitsText(o),
                    MoneyUtils.plain(o.rate),
                    MoneyUtils.plain(o.totalAmount),
                    o.status
                ).joinToString(",") { esc(it) } + "\n"
            )
        }
        val billable = orders.filter { StatementMath.isBillable(it) }
        val totalBilled = MoneyUtils.round2(billable.sumOf { it.totalAmount })
        sb.append("TOTAL,,,,,,,,,${billable.sumOf { it.units }},,${MoneyUtils.plain(totalBilled)},\n")
        return sb.toString()
    }

    // ---- Clinic directory with outstanding balances (clinic-account model) ----
    fun clinicDirectoryCsv(clinics: List<Clinic>, orders: List<WorkOrder>, payments: List<Payment>): String {
        val sb = StringBuilder()
        sb.append("Clinic Code,Clinic Name,Doctor Name,Phone,Email,City,Total Orders,Total Billed (INR),Payments Received (INR),Balance Due (INR),Status\n")
        clinics.forEach { c ->
            val clinicOrders = orders.filter { it.clinicId == c.id && StatementMath.isBillable(it) }
            val billed = MoneyUtils.round2(clinicOrders.sumOf { it.totalAmount })
            val paid = MoneyUtils.round2(payments.filter { it.clinicId == c.id }.sumOf { it.amount })
            val due = MoneyUtils.round2(billed - paid).coerceAtLeast(0.0)
            sb.append(
                listOf(
                    c.clinicCode, c.name, c.dentistName, c.phone, c.email, c.city,
                    clinicOrders.size.toString(),
                    MoneyUtils.plain(billed), MoneyUtils.plain(paid), MoneyUtils.plain(due),
                    if (c.isActive) "Active" else "Inactive"
                ).joinToString(",") { esc(it) } + "\n"
            )
        }
        return sb.toString()
    }

    // ---- Clinic payments ledger (account-level) ----
    fun paymentsLedgerCsv(payments: List<Payment>, clinics: List<Clinic>): String {
        val clinicNames = clinics.associate { it.id to it.name }
        val sb = StringBuilder()
        sb.append("Receipt ID,Date,Clinic,Amount (INR),Method,Reference,Note\n")
        payments.sortedBy { it.paymentDate }.forEach { p ->
            sb.append(
                listOf(
                    p.id.toString(), formatDate(p.paymentDate),
                    clinicNames[p.clinicId] ?: "Clinic #${p.clinicId}",
                    MoneyUtils.plain(p.amount),
                    p.paymentMethod, p.referenceNumber, p.notes
                ).joinToString(",") { esc(it) } + "\n"
            )
        }
        sb.append("TOTAL,,,${MoneyUtils.plain(MoneyUtils.round2(payments.sumOf { it.amount }))},,,\n")
        return sb.toString()
    }
}
