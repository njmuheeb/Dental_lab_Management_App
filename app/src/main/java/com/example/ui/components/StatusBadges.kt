package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun OrderStatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (status) {
        "Received" -> Pair(StatusBlueLight, StatusBlue)
        "Pending" -> Pair(StatusOrangeLight, StatusOrange)
        "In Progress" -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
        "Ready" -> Pair(Color(0xFFE0F2FE), DentalBlue)
        "Completed" -> Pair(StatusGreenLight, StatusGreen)
        "Delivered" -> Pair(Color(0xFFD1FAE5), Color(0xFF047857))
        "Cancelled" -> Pair(Color(0xFFF1F5F9), TextMuted)
        else -> Pair(SurfaceVariantLight, TextSecondary)
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = textColor
        )
    }
}

@Composable
fun PricingModelBadge(
    model: String,
    modifier: Modifier = Modifier
) {
    val isUnit = model == "UNIT_BASED"
    val label = if (isUnit) "Unit-Based" else "Fixed-Price"
    val bgColor = if (isUnit) Color(0xFFEFF6FF) else Color(0xFFFAF5FF)
    val textColor = if (isUnit) DentalBlue else Color(0xFF9333EA)

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            color = textColor
        )
    }
}
