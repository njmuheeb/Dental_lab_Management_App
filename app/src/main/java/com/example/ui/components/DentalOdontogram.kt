package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.util.ToothFormat
import com.example.ui.theme.*

// FDI Teeth Definitions
val UPPER_RIGHT_TEETH = listOf(18, 17, 16, 15, 14, 13, 12, 11) // Q1
val UPPER_LEFT_TEETH = listOf(21, 22, 23, 24, 25, 26, 27, 28)  // Q2
val LOWER_RIGHT_TEETH = listOf(48, 47, 46, 45, 44, 43, 42, 41) // Q4
val LOWER_LEFT_TEETH = listOf(31, 32, 33, 34, 35, 36, 37, 38)  // Q3

fun getToothTypeLabel(fdi: Int): String {
    val toothPos = fdi % 10
    return when (toothPos) {
        1 -> "CI" // Central Incisor
        2 -> "LI" // Lateral Incisor
        3 -> "C"  // Canine
        4 -> "1P" // 1st Premolar
        5 -> "2P" // 2nd Premolar
        6 -> "1M" // 1st Molar
        7 -> "2M" // 2nd Molar
        8 -> "3M" // 3rd Molar
        else -> ""
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DentalOdontogram(
    selectedTeeth: Set<Int>,
    onTeethChanged: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dental_odontogram_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Title & Selected Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Dental Odontogram (1-8 per Quadrant)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (selectedTeeth.isEmpty()) "Tap teeth to select for restoration"
                               else "${selectedTeeth.size} tooth/teeth selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedTeeth.isEmpty()) TextMuted else DentalBlue,
                        fontWeight = if (selectedTeeth.isEmpty()) FontWeight.Normal else FontWeight.SemiBold
                    )
                }

                if (!readOnly && selectedTeeth.isNotEmpty()) {
                    TextButton(
                        onClick = { onTeethChanged(emptySet()) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("clear_teeth_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear all",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All", fontSize = 12.sp)
                    }
                }
            }

            // Quick Selection Chips (when interactive)
            if (!readOnly) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickSelectChip(
                        label = "All Upper",
                        onClick = {
                            val newSet = selectedTeeth.toMutableSet()
                            newSet.addAll(UPPER_RIGHT_TEETH + UPPER_LEFT_TEETH)
                            onTeethChanged(newSet)
                        }
                    )
                    QuickSelectChip(
                        label = "All Lower",
                        onClick = {
                            val newSet = selectedTeeth.toMutableSet()
                            newSet.addAll(LOWER_RIGHT_TEETH + LOWER_LEFT_TEETH)
                            onTeethChanged(newSet)
                        }
                    )
                    QuickSelectChip(
                        label = "Q1 (Upper Right)",
                        onClick = {
                            val newSet = selectedTeeth.toMutableSet()
                            newSet.addAll(UPPER_RIGHT_TEETH)
                            onTeethChanged(newSet)
                        }
                    )
                    QuickSelectChip(
                        label = "Q2 (Upper Left)",
                        onClick = {
                            val newSet = selectedTeeth.toMutableSet()
                            newSet.addAll(UPPER_LEFT_TEETH)
                            onTeethChanged(newSet)
                        }
                    )
                    QuickSelectChip(
                        label = "Q4 (Lower Right)",
                        onClick = {
                            val newSet = selectedTeeth.toMutableSet()
                            newSet.addAll(LOWER_RIGHT_TEETH)
                            onTeethChanged(newSet)
                        }
                    )
                    QuickSelectChip(
                        label = "Q3 (Lower Left)",
                        onClick = {
                            val newSet = selectedTeeth.toMutableSet()
                            newSet.addAll(LOWER_LEFT_TEETH)
                            onTeethChanged(newSet)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Upper Arch Container
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceVariantLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MAXILLARY (UPPER ARCH)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Navy700,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Q1: Right (18 to 11)
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            UPPER_RIGHT_TEETH.forEach { tooth ->
                                ToothItem(
                                    fdi = tooth,
                                    isSelected = selectedTeeth.contains(tooth),
                                    onToggle = {
                                        if (!readOnly) {
                                            val newSet = selectedTeeth.toMutableSet()
                                            if (newSet.contains(tooth)) newSet.remove(tooth) else newSet.add(tooth)
                                            onTeethChanged(newSet)
                                        }
                                    }
                                )
                            }
                        }

                        // Midline Divider
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(56.dp)
                                .background(DentalBlue.copy(alpha = 0.4f))
                        )

                        // Q2: Left (21 to 28)
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            UPPER_LEFT_TEETH.forEach { tooth ->
                                ToothItem(
                                    fdi = tooth,
                                    isSelected = selectedTeeth.contains(tooth),
                                    onToggle = {
                                        if (!readOnly) {
                                            val newSet = selectedTeeth.toMutableSet()
                                            if (newSet.contains(tooth)) newSet.remove(tooth) else newSet.add(tooth)
                                            onTeethChanged(newSet)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Quadrant labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Right (Q1)", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = TextMuted)
                        Text("Midline", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = DentalBlue)
                        Text("Left (Q2)", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lower Arch Container
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceVariantLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MANDIBULAR (LOWER ARCH)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Navy700,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Q4: Right (48 to 41)
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            LOWER_RIGHT_TEETH.forEach { tooth ->
                                ToothItem(
                                    fdi = tooth,
                                    isSelected = selectedTeeth.contains(tooth),
                                    onToggle = {
                                        if (!readOnly) {
                                            val newSet = selectedTeeth.toMutableSet()
                                            if (newSet.contains(tooth)) newSet.remove(tooth) else newSet.add(tooth)
                                            onTeethChanged(newSet)
                                        }
                                    }
                                )
                            }
                        }

                        // Midline Divider
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(56.dp)
                                .background(DentalBlue.copy(alpha = 0.4f))
                        )

                        // Q3: Left (31 to 38)
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            LOWER_LEFT_TEETH.forEach { tooth ->
                                ToothItem(
                                    fdi = tooth,
                                    isSelected = selectedTeeth.contains(tooth),
                                    onToggle = {
                                        if (!readOnly) {
                                            val newSet = selectedTeeth.toMutableSet()
                                            if (newSet.contains(tooth)) newSet.remove(tooth) else newSet.add(tooth)
                                            onTeethChanged(newSet)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Quadrant labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Right (Q4)", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = TextMuted)
                        Text("Midline", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = DentalBlue)
                        Text("Left (Q3)", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = TextMuted)
                    }
                }
            }

            // Summary Tags display (quadrant-based single-digit format, never FDI)
            if (selectedTeeth.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Selected: ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = ToothFormat.formatLong(selectedTeeth.sorted().joinToString(",")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DentalBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ToothItem(
    fdi: Int,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) DentalBlue else Color.White,
        label = "toothBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else TextPrimary,
        label = "toothText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) DentalBlueLight else OutlineLight,
        label = "toothBorder"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onToggle)
            .testTag("tooth_$fdi")
    ) {
        // Tooth Box - single-digit label (1-8); FDI value kept internally for storage
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(18.dp)
                .height(36.dp)
                .background(bgColor, RoundedCornerShape(4.dp))
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
        ) {
            Text(
                text = "${fdi % 10}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }

        // Type Initial
        Text(
            text = getToothTypeLabel(fdi),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 7.sp,
            color = if (isSelected) DentalBlue else TextMuted,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun QuickSelectChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFE2E8F0),
        modifier = Modifier.height(26.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Navy800
            )
        }
    }
}
