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
}
