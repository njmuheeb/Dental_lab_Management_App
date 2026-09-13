package com.example.data.util

/**
 * Tooth numbering presentation rules.
 *
 * The internal model stores teeth as FDI two-digit values (11-18, 21-28, 31-38, 41-48) so
 * quadrants stay unambiguous, but the USER-FACING display everywhere in the app (odontogram,
 * work entry, work history, statement, bill PDF, Excel export) shows ONLY the single digit
 * 1-8 grouped per quadrant, e.g. "UR: 1, 2 | UL: 4, 5 | LL: 6 | LR: 7, 8".
 *
 * Quadrant codes: UR = Upper Right (FDI 1x), UL = Upper Left (2x), LL = Lower Left (3x),
 * LR = Lower Right (4x).
 */
object ToothFormat {

    fun isValidFdi(fdi: Int): Boolean =
        (fdi in 11..18) || (fdi in 21..28) || (fdi in 31..38) || (fdi in 41..48)

    fun quadrantOf(fdi: Int): Int = fdi / 10

    /** Single-digit tooth number shown to users (1-8). */
    fun digitOf(fdi: Int): Int = fdi % 10

    fun parse(selectedTeeth: String): List<Int> =
        selectedTeeth.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { isValidFdi(it) }
            .sorted()

    /**
     * Readable quadrant format for dialogs and summaries:
     * "Upper Right: 1, 2 | Upper Left: 4, 5" (only non-empty quadrants).
     */
    fun formatLong(selectedTeeth: String): String {
        val groups = parse(selectedTeeth).groupBy { quadrantOf(it) }
        if (groups.isEmpty()) return ""
        return groups.entries
            .sortedBy { it.key }
            .joinToString(" | ") { (q, teeth) ->
                "${fullLabel(q)}: ${teeth.joinToString(", ") { digitOf(it).toString() }}"
            }
    }

    /**
     * Compact format for narrow table cells (bill/work history):
     * "UR:1,2 UL:4,5 LL:6 LR:7,8" (only non-empty quadrants).
     */
    fun formatCompact(selectedTeeth: String): String {
        val groups = parse(selectedTeeth).groupBy { quadrantOf(it) }
        if (groups.isEmpty()) return ""
        return groups.entries
            .sortedBy { it.key }
            .joinToString(" ") { (q, teeth) ->
                "${shortLabel(q)}:${teeth.joinToString(",") { digitOf(it).toString() }}"
            }
    }

    fun shortLabel(quadrant: Int): String = when (quadrant) {
        1 -> "UR"; 2 -> "UL"; 3 -> "LL"; 4 -> "LR"; else -> "?"
    }

    fun fullLabel(quadrant: Int): String = when (quadrant) {
        1 -> "Upper Right"; 2 -> "Upper Left"; 3 -> "Lower Left"; 4 -> "Lower Right"; else -> "?"
    }

    /** Display value for table cells: compact quadrant format or "—" when not specified. */
    fun displayOrDash(selectedTeeth: String): String =
        formatCompact(selectedTeeth).ifBlank { "—" }
}
