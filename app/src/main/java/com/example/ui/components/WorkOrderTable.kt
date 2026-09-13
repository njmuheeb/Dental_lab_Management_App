package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WorkOrder
import com.example.data.util.MoneyUtils
import com.example.data.util.ToothFormat
import com.example.ui.theme.Navy900
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private data class Col(val title: String, val width: Int)

/**
 * Standard work table used everywhere (clinic work history, monthly statement, bill preview).
 *
 * Columns: S.No | Date | Patient Name | Work Type | Shade | Tooth Numbers | Units |
 * Rate per Unit | Total Price (+ optional row actions).
 *
 * Per the clinic-account payment model there is deliberately NO case-wise payment column:
 * clinics settle monthly at the account level. Fixed-price work shows Units as "Fixed".
 * Teeth are shown as quadrant-based single digits (e.g. "UR:1,2 UL:4,5").
 */
private val TABLE_COLUMNS = listOf(
    Col("S.No", 40),
    Col("Date", 70),
    Col("Patient Name", 116),
    Col("Work Type", 136),
    Col("Shade", 50),
    Col("Tooth Numbers", 106),
    Col("Units", 46),
    Col("Rate/Unit", 66),
    Col("Total Price", 78),
    Col("Actions", 78)
)

@Composable
fun WorkOrderTable(
    orders: List<WorkOrder>,
    formatDate: (Long) -> String,
    onEdit: ((WorkOrder) -> Unit)? = null,
    onDelete: ((WorkOrder) -> Unit)? = null,
    onRowClick: ((WorkOrder) -> Unit)? = null,
    startSerial: Int = 1,
    showActions: Boolean = true,
    emptyMessage: String = "No work entries in this period"
) {
    Box(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .testTag("work_order_table")
    ) {
        Column {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(34.dp)
                    .background(Navy900)
            ) {
                val visibleCols = if (showActions) TABLE_COLUMNS else TABLE_COLUMNS.dropLast(1)
                visibleCols.forEach { col ->
                    Text(
                        text = col.title,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .width(col.width.dp)
                            .padding(horizontal = 5.dp)
                    )
                }
            }

            if (orders.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(52.dp)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = emptyMessage,
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            } else {
                orders.forEachIndexed { idx, order ->
                    val isZebra = idx % 2 == 1
                    val bg = if (isZebra) com.example.ui.theme.SurfaceVariantLight else MaterialTheme.colorScheme.surface
                    val isCancelled = order.status == "Cancelled"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(48.dp)
                            .background(bg)
                            .clickable(enabled = onRowClick != null) { onRowClick?.invoke(order) }
                            .testTag("table_row_${order.jobNumber}")
                    ) {
                        val textColor = if (isCancelled) TextMuted else TextSecondary
                        val strongColor = if (isCancelled) TextMuted else TextPrimary

                        cellText("${startSerial + idx}", TABLE_COLUMNS[0].width, color = textColor)
                        cellText(formatDate(order.entryDate), TABLE_COLUMNS[1].width, color = textColor)
                        cellText(order.patientName, TABLE_COLUMNS[2].width, bold = true, color = strongColor, maxLines = 2)
                        cellText(order.workTypeName, TABLE_COLUMNS[3].width, color = textColor, maxLines = 2)
                        cellText(order.shade, TABLE_COLUMNS[4].width, color = textColor)
                        cellText(ToothFormat.displayOrDash(order.selectedTeeth), TABLE_COLUMNS[5].width, color = strongColor, maxLines = 2)
                        cellText(
                            if (order.pricingModel == "UNIT_BASED") order.units.toString() else "Fixed",
                            TABLE_COLUMNS[6].width,
                            color = textColor
                        )
                        cellText(MoneyUtils.plain(order.rate), TABLE_COLUMNS[7].width, color = textColor)
                        cellText(MoneyUtils.plain(order.totalAmount), TABLE_COLUMNS[8].width, bold = true, color = strongColor)

                        if (showActions) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .width(TABLE_COLUMNS[9].width.dp)
                                    .padding(horizontal = 2.dp)
                            ) {
                                if (onEdit != null) {
                                    IconButton(onClick = { onEdit(order) }, modifier = Modifier.height(30.dp).width(30.dp)) {
                                        Icon(
                                            Icons.Default.Edit, contentDescription = "Edit",
                                            tint = TextSecondary, modifier = Modifier.width(15.dp).height(15.dp)
                                        )
                                    }
                                }
                                if (onDelete != null) {
                                    IconButton(onClick = { onDelete(order) }, modifier = Modifier.height(30.dp).width(30.dp)) {
                                        Icon(
                                            Icons.Default.Delete, contentDescription = "Delete",
                                            tint = StatusRed, modifier = Modifier.width(15.dp).height(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.cellText(
    text: String,
    widthDp: Int,
    bold: Boolean = false,
    color: androidx.compose.ui.graphics.Color = TextSecondary,
    maxLines: Int = 1
) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(widthDp.dp)
            .padding(horizontal = 5.dp)
    )
}
