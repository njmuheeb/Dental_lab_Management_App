package com.example

import com.example.data.util.ToothFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The odontogram and all documents display ONLY single digits 1-8 per quadrant, grouped by
 * quadrant (UR/UL/LL/LR). Raw two-digit FDI numbers must never reach the user.
 */
class ToothFormatTest {

    @Test
    fun `compact format groups single digits by quadrant`() {
        // Upper Right 1,2 + Upper Left 4,5 -> "UR:1,2 UL:4,5"
        assertEquals("UR:1,2 UL:4,5", ToothFormat.formatCompact("11,12,24,25"))
        // All four quadrants
        assertEquals(
            "UR:1 UL:2 LL:3 LR:4",
            ToothFormat.formatCompact("11,22,33,44")
        )
        // Sorting within quadrant and across quadrants regardless of input order
        assertEquals("UR:1,8 UL:3", ToothFormat.formatCompact("18,23,11"))
    }

    @Test
    fun `long format uses full quadrant names`() {
        assertEquals(
            "Upper Right: 1, 2 | Lower Left: 6",
            ToothFormat.formatLong("11,12,36")
        )
    }

    @Test
    fun `empty or invalid input yields blank and dash display`() {
        assertEquals("", ToothFormat.formatCompact(""))
        assertEquals("", ToothFormat.formatCompact("not teeth"))
        assertEquals("—", ToothFormat.displayOrDash(""))
        assertEquals("—", ToothFormat.displayOrDash("abc,xyz"))
    }

    @Test
    fun `invalid fdi values are filtered out`() {
        // 9x / 0x / out-of-range values are not teeth
        assertEquals("UR:1", ToothFormat.formatCompact("11,19,91,1,0"))
    }

    @Test
    fun `digits are always 1-8 and labels never contain two-digit teeth`() {
        val formatted = ToothFormat.formatCompact("18,17,26,35,41,48")
        assertTrue(formatted.contains("UR:7,8"))
        assertTrue(formatted.contains("UL:6"))
        assertTrue(formatted.contains("LL:5"))
        assertTrue(formatted.contains("LR:1,8"))
        // No two-digit tooth numbers anywhere in the output
        assertFalse(Regex("\\b1[1-8]\\b").containsMatchIn(formatted))
        assertFalse(Regex("\\b[2-4][1-8]\\b").containsMatchIn(formatted))
    }

    @Test
    fun `every valid fdi tooth maps to its single digit`() {
        val all = (11..18).map { it to it - 10 } +
            (21..28).map { it to it - 20 } +
            (31..38).map { it to it - 30 } +
            (41..48).map { it to it - 40 }
        all.forEach { (fdi, digit) ->
            assertTrue(ToothFormat.isValidFdi(fdi))
            assertEquals(digit, ToothFormat.digitOf(fdi))
        }
        assertFalse(ToothFormat.isValidFdi(10))
        assertFalse(ToothFormat.isValidFdi(19))
        assertFalse(ToothFormat.isValidFdi(51))
        assertFalse(ToothFormat.isValidFdi(8))
    }

    // ------------------------------------------------------------ quadrant diagram

    @Test
    fun `quadrantDigits maps FDI teeth to the correct quadrants as single digits`() {
        // Reference case: 12,13,14 -> UR "2 3 4"; 21 -> UL "1"; 47 -> LR "7"; LL empty
        val q = ToothFormat.quadrantDigits("12,13,14,21,47")
        assertEquals(listOf(2, 3, 4), q.upperRight)
        assertEquals(listOf(1), q.upperLeft)
        assertEquals(emptyList<Int>(), q.lowerLeft)
        assertEquals(listOf(7), q.lowerRight)
    }

    @Test
    fun `quadrantDigits never shows two-digit numbers and never reverses quadrants`() {
        val q = ToothFormat.quadrantDigits("11,18,21,28,31,38,41,48")
        assertEquals(listOf(1, 8), q.upperRight)   // FDI 1x = Upper Right
        assertEquals(listOf(1, 8), q.upperLeft)    // FDI 2x = Upper Left
        assertEquals(listOf(1, 8), q.lowerLeft)    // FDI 3x = Lower Left
        assertEquals(listOf(1, 8), q.lowerRight)   // FDI 4x = Lower Right
        val allDigits = q.upperRight + q.upperLeft + q.lowerLeft + q.lowerRight
        assertTrue(allDigits.all { it in 1..8 })
    }

    @Test
    fun `quadrantDigits ignores invalid tokens, dedupes and sorts ascending`() {
        val q = ToothFormat.quadrantDigits("18,11,13,11,abc,19,10,51,99,25,22")
        assertEquals(listOf(1, 3, 8), q.upperRight)
        assertEquals(listOf(2, 5), q.upperLeft)
        assertEquals(emptyList<Int>(), q.lowerLeft)
        assertEquals(emptyList<Int>(), q.lowerRight)
    }

    @Test
    fun `quadrant text round-trips back to FDI for legacy cards`() {
        val fdi = "11,12,24,36,41,48"
        val text = ToothFormat.formatLong(fdi)
        assertEquals(fdi, ToothFormat.fdiFromQuadrantText(text))

        // Compact format and user-edited spacing are also tolerated
        assertEquals("12,13,47", ToothFormat.fdiFromQuadrantText("UR: 2, 3 | LR:7"))
        assertEquals("", ToothFormat.fdiFromQuadrantText("no quadrant text here"))
        assertEquals("", ToothFormat.fdiFromQuadrantText(""))
    }
}
