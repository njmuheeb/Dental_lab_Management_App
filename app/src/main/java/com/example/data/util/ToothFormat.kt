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

    /**
     * Teeth grouped per quadrant as single digits (1-8), for the four-quadrant diagram
     * on the warranty card. FDI quadrant mapping (standard charting, patient front view):
     *
     *   Upper Right (UR) = FDI 1x -> drawn top-LEFT,   Upper Left (UL) = 2x -> top-RIGHT
     *   Lower Right (LR) = FDI 4x -> drawn bottom-LEFT, Lower Left (LL) = 3x -> bottom-RIGHT
     *
     * Each list is distinct and sorted ascending; invalid tokens are ignored.
     */
    data class ToothQuadrants(
        val upperRight: List<Int>,
        val upperLeft: List<Int>,
        val lowerLeft: List<Int>,
        val lowerRight: List<Int>
    )

    fun quadrantDigits(selectedTeeth: String): ToothQuadrants {
        val ur = mutableListOf<Int>()
        val ul = mutableListOf<Int>()
        val ll = mutableListOf<Int>()
        val lr = mutableListOf<Int>()
        parse(selectedTeeth).forEach { fdi ->
            val digit = digitOf(fdi)
            if (digit in 1..8) when (quadrantOf(fdi)) {
                1 -> ur.add(digit)
                2 -> ul.add(digit)
                3 -> ll.add(digit)
                4 -> lr.add(digit)
            }
        }
        return ToothQuadrants(
            upperRight = ur.distinct().sorted(),
            upperLeft = ul.distinct().sorted(),
            lowerLeft = ll.distinct().sorted(),
            lowerRight = lr.distinct().sorted()
        )
    }

    /**
     * Best-effort reverse of [formatLong] / [formatCompact]: turns a human-edited
     * quadrant text ("Upper Right: 1, 2 | Lower Left: 6") back into an FDI string
     * ("11,12,36"). Used as a fallback for warranty cards stored before the raw
     * FDI list was kept on the card.
     */
    fun fdiFromQuadrantText(text: String): String {
        val fdi = mutableListOf<Int>()
        text.split("|").forEach { part ->
            val cleaned = part.trim()
            if (cleaned.isEmpty()) return@forEach
            val quadrant = when {
                cleaned.startsWith("Upper Right", ignoreCase = true) ||
                    cleaned.startsWith("UR:", ignoreCase = true) -> 1
                cleaned.startsWith("Upper Left", ignoreCase = true) ||
                    cleaned.startsWith("UL:", ignoreCase = true) -> 2
                cleaned.startsWith("Lower Left", ignoreCase = true) ||
                    cleaned.startsWith("LL:", ignoreCase = true) -> 3
                cleaned.startsWith("Lower Right", ignoreCase = true) ||
                    cleaned.startsWith("LR:", ignoreCase = true) -> 4
                else -> return@forEach
            }
            val digits = cleaned.substringAfter(":", "").ifBlank { return@forEach }
            digits.split(",").forEach { tok ->
                val d = tok.trim().toIntOrNull()
                if (d != null && d in 1..8) fdi.add(quadrant * 10 + d)
            }
        }
        return fdi.distinct().sorted().joinToString(",")
    }
}
