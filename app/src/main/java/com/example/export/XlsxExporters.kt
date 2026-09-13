package com.example.export

import com.example.data.model.Clinic
import com.example.data.model.ClinicStatement
import com.example.data.model.LabSettings
import com.example.data.model.Payment
import com.example.data.model.StatementMath
import com.example.data.model.WorkOrder
import com.example.data.util.ToothFormat
import com.example.export.XlsxWriter.Cell
import com.example.export.XlsxWriter.Row
import com.example.export.XlsxWriter.RowStyle
import com.example.export.XlsxWriter.Sheet

/**
 * Builds professionally formatted XLSX workbooks from app data.
 *
 * Monthly statement/bill workbook = 3 sheets:
 *   1. "Bill Summary"   - lab title, bill-to, period, closing-balance accounting card
 *   2. "Work Details"   - 9-column work table (no case-wise payment column), frozen header
 *   3. "Payment Summary"- the month's clinic payments + totals
 *
 * Also full work orders ledger / clinic directory / payments ledger workbooks. Excel values
 * always match the PDF and in-app calculations (same MoneyUtils + StatementMath sources).
 */
object XlsxExporters {

    private val dateFmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
    private fun d(millis: Long) = dateFmt.format(java.util.Date(millis))

    private fun unitsCell(order: WorkOrder): Cell =
        if (order.pricingModel == "UNIT_BASED") Cell.Number(order.units.toDouble()) else Cell.Text("Fixed")

    // ------------------------------------------------------ monthly statement (3 sheets)

    /**
     * @param isBill true -> document is a monthly bill/invoice (BILL- prefix, "Bill Summary"
     * sheet); false -> full/filterable clinic statement (ST- prefix, "Statement Summary").
     */
    fun statementWorkbook(statement: ClinicStatement, labSettings: LabSettings, isBill: Boolean = false): ByteArray {
        return XlsxWriter.build(
            listOf(
                billSummarySheet(statement, labSettings, isBill),
                workDetailsSheet(statement),
                paymentSummarySheet(statement)
            )
        )
    }

    private fun billSummarySheet(st: ClinicStatement, lab: LabSettings, isBill: Boolean): Sheet {
        val docNumber = if (isBill) st.statementNumber.replace("ST-", "BILL-") else st.statementNumber
        val rows = mutableListOf<Row>()
        rows += Row(listOf(Cell.Text(lab.labName)), RowStyle.TITLE)
        rows += Row(
            listOf(
                Cell.Text(docNumber),
                Cell.Text("Date: ${d(st.statementDate)}")
            ), RowStyle.MUTED
        )
        rows += Row(listOf(Cell.Text("")), RowStyle.NORMAL)
        rows += Row(listOf(Cell.Text(if (isBill) "BILL TO" else "CLINIC"), Cell.Text(if (isBill) "BILLING PERIOD" else "PERIOD")), RowStyle.MUTED)
        rows += Row(
            listOf(
                Cell.Text(st.clinic.name),
                Cell.Text("${d(st.periodStart)} to ${d(st.periodEndExclusive - 1)}")
            ), RowStyle.SUBTITLE
        )
        rows += Row(listOf(Cell.Text("Dr. ${st.clinic.dentistName}"), Cell.Text(st.periodLabel)), RowStyle.NORMAL)
        rows += Row(
            listOf(
                Cell.Text(st.clinic.phone + if (st.clinic.email.isNotBlank()) " | ${st.clinic.email}" else ""),
                Cell.Text("${st.clinic.address}, ${st.clinic.city}")
            ), RowStyle.MUTED
        )
        rows += Row(listOf(Cell.Text("")), RowStyle.NORMAL)
        rows += Row(listOf(Cell.Text("SUMMARY"), Cell.Text("Amount (INR)")), RowStyle.HEADER)
        rows += Row(listOf(Cell.Text("Total Work Entries"), Cell.Number(st.totalEntries.toDouble())), RowStyle.NORMAL)
        rows += Row(listOf(Cell.Text("Total Units"), Cell.Number(st.totalUnits.toDouble())), RowStyle.NORMAL)
        rows += Row(listOf(Cell.Text("Current Month Revenue"), Cell.Money(st.totalRevenue)), RowStyle.NORMAL)
        rows += Row(listOf(Cell.Text("Previous Outstanding Balance"), Cell.Money(st.previousBalance)), RowStyle.NORMAL)
        rows += Row(listOf(Cell.Text("Payments Received (${st.payments.size} receipts)"), Cell.Money(st.paymentsReceived)), RowStyle.NORMAL)
        rows += Row(listOf(Cell.Text("CLOSING OUTSTANDING BALANCE DUE"), Cell.Money(st.remainingBalance)), RowStyle.ACCENT)
        rows += Row(listOf(Cell.Text("")), RowStyle.NORMAL)
        rows += Row(
            listOf(
                Cell.Text("Closing Outstanding = Previous Outstanding + Period Revenue - Payments Received"),
                Cell.Text("")
            ), RowStyle.MUTED
        )
        return Sheet(
            name = if (isBill) "Bill Summary" else "Statement Summary",
            rows = rows,
            colWidths = listOf(46.0, 26.0)
        )
    }

    private fun workDetailsSheet(st: ClinicStatement): Sheet {
        val rows = mutableListOf<Row>()
        rows += Row(
            listOf(
                "S.No", "Date", "Patient Name", "Work Type", "Shade", "Tooth Numbers",
                "Units", "Rate per Unit", "Total Price"
            ).map { Cell.Text(it) }, RowStyle.HEADER
        )
        st.orders.forEachIndexed { idx, o ->
            rows += Row(
                listOf(
                    Cell.Number((idx + 1).toDouble()),
                    Cell.Text(d(o.entryDate)),
                    Cell.Text(o.patientName),
                    Cell.Text(o.workTypeName),
                    Cell.Text(o.shade),
                    Cell.Text(ToothFormat.displayOrDash(o.selectedTeeth)),
                    unitsCell(o),
                    Cell.Money(o.rate),
                    Cell.Money(o.totalAmount)
                ), if (idx % 2 == 1) RowStyle.ZEBRA else RowStyle.NORMAL
            )
        }
        rows += Row(
            listOf(
                Cell.Text("TOTAL"), Cell.Text(""), Cell.Text(""), Cell.Text(""), Cell.Text(""),
                Cell.Text(""), Cell.Number(st.totalUnits.toDouble()), Cell.Text(""),
                Cell.Money(st.totalRevenue)
            ), RowStyle.TOTAL
        )
        return Sheet(
            name = "Work Details",
            rows = rows,
            colWidths = listOf(6.0, 12.0, 22.0, 32.0, 8.0, 18.0, 8.0, 13.0, 14.0),
            freezeRows = 1
        )
    }

    private fun paymentSummarySheet(st: ClinicStatement): Sheet {
        val rows = mutableListOf<Row>()
        rows += Row(listOf(Cell.Text("PAYMENTS RECEIVED - ${st.periodLabel}")), RowStyle.TITLE)
        rows += Row(listOf(Cell.Text("")), RowStyle.NORMAL)
        rows += Row(
            listOf("Date", "Amount", "Method", "Reference", "Note").map { Cell.Text(it) },
            RowStyle.HEADER
        )
        if (st.payments.isEmpty()) {
            rows += Row(listOf(Cell.Text("No payments recorded during this period"), Cell.Text(""), Cell.Text(""), Cell.Text(""), Cell.Text("")), RowStyle.MUTED)
        } else {
            st.payments.forEachIndexed { idx, p ->
                rows += Row(
                    listOf(
                        Cell.Text(d(p.paymentDate)),
                        Cell.Money(p.amount),
                        Cell.Text(p.paymentMethod),
                        Cell.Text(p.referenceNumber),
                        Cell.Text(p.notes)
                    ), if (idx % 2 == 1) RowStyle.ZEBRA else RowStyle.NORMAL
                )
            }
        }
        rows += Row(
            listOf(
                Cell.Text("TOTAL RECEIVED"), Cell.Money(st.paymentsReceived),
                Cell.Text(""), Cell.Text(""), Cell.Text("")
            ), RowStyle.TOTAL
        )
        rows += Row(listOf(Cell.Text("")), RowStyle.NORMAL)
        rows += Row(listOf(Cell.Text("Previous Outstanding Balance"), Cell.Money(st.previousBalance)), RowStyle.NORMAL)
        rows += Row(listOf(Cell.Text("Period Revenue"), Cell.Money(st.totalRevenue)), RowStyle.NORMAL)
        rows += Row(listOf(Cell.Text("Payments Received"), Cell.Money(st.paymentsReceived)), RowStyle.NORMAL)
        rows += Row(listOf(Cell.Text("CLOSING OUTSTANDING BALANCE DUE"), Cell.Money(st.remainingBalance)), RowStyle.ACCENT)
        return Sheet(
            name = "Payment Summary",
            rows = rows,
            colWidths = listOf(14.0, 14.0, 14.0, 18.0, 34.0)
        )
    }

    // ------------------------------------------------------ full work orders ledger

    fun workOrdersLedgerWorkbook(orders: List<WorkOrder>, labSettings: LabSettings): ByteArray {
        val rows = mutableListOf<Row>()
        rows += Row(listOf(Cell.Text(labSettings.labName)), RowStyle.TITLE)
        rows += Row(listOf(Cell.Text("WORK ORDERS PRODUCTION LEDGER (ALL ${orders.size} ROWS)")), RowStyle.SUBTITLE)
        rows += Row(listOf(Cell.Text("")), RowStyle.NORMAL)
        rows += Row(
            listOf(
                "Job Number", "Entry Date", "Delivery Date", "Clinic", "Doctor", "Patient",
                "Work Type", "Teeth", "Model", "Units", "Rate", "Total", "Status"
            ).map { Cell.Text(it) }, RowStyle.HEADER
        )
        orders.sortedBy { it.entryDate }.forEachIndexed { idx, o ->
            rows += Row(
                listOf(
                    Cell.Text(o.jobNumber),
                    Cell.Text(d(o.entryDate)),
                    Cell.Text(d(o.expectedDeliveryDate)),
                    Cell.Text(o.clinicName),
                    Cell.Text(o.dentistName),
                    Cell.Text(o.patientName),
                    Cell.Text(o.workTypeName),
                    Cell.Text(ToothFormat.displayOrDash(o.selectedTeeth)),
                    Cell.Text(if (o.pricingModel == "UNIT_BASED") "Unit" else "Fixed"),
                    unitsCell(o),
                    Cell.Money(o.rate),
                    Cell.Money(o.totalAmount),
                    Cell.Text(o.status)
                ), if (idx % 2 == 1) RowStyle.ZEBRA else RowStyle.NORMAL
            )
        }
        val billable = orders.filter { StatementMath.isBillable(it) }
        rows += Row(
            listOf(
                Cell.Text("TOTAL (excl. cancelled)"), Cell.Text(""), Cell.Text(""), Cell.Text(""),
                Cell.Text(""), Cell.Text(""), Cell.Text(""), Cell.Text(""), Cell.Text(""),
                Cell.Number(billable.sumOf { it.units }.toDouble()), Cell.Text(""),
                Cell.Money(billable.sumOf { it.totalAmount }), Cell.Text("")
            ), RowStyle.TOTAL
        )
        return XlsxWriter.build(
            listOf(
                Sheet(
                    name = "Work Orders",
                    rows = rows,
                    colWidths = listOf(16.0, 12.0, 12.0, 26.0, 18.0, 20.0, 30.0, 16.0, 8.0, 8.0, 11.0, 12.0, 12.0),
                    freezeRows = 4
                )
            )
        )
    }

    // ------------------------------------------------------ clinic directory

    fun clinicDirectoryWorkbook(
        clinics: List<Clinic>,
        orders: List<WorkOrder>,
        payments: List<Payment>
    ): ByteArray {
        val rows = mutableListOf<Row>()
        rows += Row(listOf(Cell.Text("CLINIC DIRECTORY & OUTSTANDING BALANCES")), RowStyle.TITLE)
        rows += Row(listOf(Cell.Text("")), RowStyle.NORMAL)
        rows += Row(
            listOf(
                "Code", "Clinic", "Doctor", "Phone", "Email", "City",
                "Orders", "Billed", "Payments Received", "Balance Due", "Status"
            ).map { Cell.Text(it) }, RowStyle.HEADER
        )
        clinics.forEachIndexed { idx, c ->
            val clinicOrders = orders.filter { it.clinicId == c.id && StatementMath.isBillable(it) }
            val billed = com.example.data.util.MoneyUtils.round2(clinicOrders.sumOf { it.totalAmount })
            val paid = com.example.data.util.MoneyUtils.round2(payments.filter { it.clinicId == c.id }.sumOf { it.amount })
            rows += Row(
                listOf(
                    Cell.Text(c.clinicCode),
                    Cell.Text(c.name),
                    Cell.Text(c.dentistName),
                    Cell.Text(c.phone),
                    Cell.Text(c.email),
                    Cell.Text(c.city),
                    Cell.Number(clinicOrders.size.toDouble()),
                    Cell.Money(billed),
                    Cell.Money(paid),
                    Cell.Money(com.example.data.util.MoneyUtils.round2(billed - paid).coerceAtLeast(0.0)),
                    Cell.Text(if (c.isActive) "Active" else "Inactive")
                ), if (idx % 2 == 1) RowStyle.ZEBRA else RowStyle.NORMAL
            )
        }
        return XlsxWriter.build(
            listOf(
                Sheet(
                    name = "Clinics",
                    rows = rows,
                    colWidths = listOf(10.0, 30.0, 20.0, 16.0, 28.0, 14.0, 8.0, 13.0, 16.0, 13.0, 10.0)
                )
            )
        )
    }

    // ------------------------------------------------------ payments ledger

    fun paymentsLedgerWorkbook(payments: List<Payment>, clinics: List<Clinic>): ByteArray {
        val clinicNames = clinics.associate { it.id to it.name }
        val rows = mutableListOf<Row>()
        rows += Row(listOf(Cell.Text("CLINIC PAYMENTS & RECEIPTS LEDGER (${payments.size} RECEIPTS)")), RowStyle.TITLE)
        rows += Row(listOf(Cell.Text("")), RowStyle.NORMAL)
        rows += Row(
            listOf("Receipt ID", "Date", "Clinic", "Amount", "Method", "Reference", "Note").map { Cell.Text(it) },
            RowStyle.HEADER
        )
        payments.sortedBy { it.paymentDate }.forEachIndexed { idx, p ->
            rows += Row(
                listOf(
                    Cell.Number(p.id.toDouble()),
                    Cell.Text(d(p.paymentDate)),
                    Cell.Text(clinicNames[p.clinicId] ?: "Clinic #${p.clinicId}"),
                    Cell.Money(p.amount),
                    Cell.Text(p.paymentMethod),
                    Cell.Text(p.referenceNumber),
                    Cell.Text(p.notes)
                ), if (idx % 2 == 1) RowStyle.ZEBRA else RowStyle.NORMAL
            )
        }
        rows += Row(
            listOf(
                Cell.Text("TOTAL"), Cell.Text(""), Cell.Text(""),
                Cell.Money(com.example.data.util.MoneyUtils.round2(payments.sumOf { it.amount })),
                Cell.Text(""), Cell.Text(""), Cell.Text("")
            ), RowStyle.TOTAL
        )
        return XlsxWriter.build(
            listOf(
                Sheet(
                    name = "Payments",
                    rows = rows,
                    colWidths = listOf(10.0, 12.0, 28.0, 13.0, 14.0, 18.0, 34.0)
                )
            )
        )
    }
}
