package com.example

import com.example.data.util.MoneyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MoneyUtilsTest {

    @Test
    fun `round2 rounds half up to 2 decimals`() {
        assertEquals(100.0, MoneyUtils.round2(100.004), 1e-9)
        assertEquals(100.01, MoneyUtils.round2(100.005), 1e-9)
        assertEquals(100.02, MoneyUtils.round2(100.015), 1e-9)
        assertEquals(0.1, MoneyUtils.round2(0.1 + 0.2 - 0.2), 1e-9)
    }

    @Test
    fun `unit based total is units times rate`() {
        assertEquals(8100.0, MoneyUtils.totalFor(3, 2700.0, "UNIT_BASED"), 1e-9)
        assertEquals(6400.0, MoneyUtils.totalFor(2, 3200.0, "UNIT_BASED"), 1e-9)
        // floating point safety: 3 x 36.67 must be exactly 110.01 after rounding
        assertEquals(110.01, MoneyUtils.totalFor(3, 36.67, "UNIT_BASED"), 1e-9)
    }

    @Test
    fun `fixed price total ignores units`() {
        assertEquals(13500.0, MoneyUtils.totalFor(1, 13500.0, "FIXED_PRICE"), 1e-9)
        assertEquals(13500.0, MoneyUtils.totalFor(5, 13500.0, "FIXED_PRICE"), 1e-9)
    }

    @Test
    fun `balance due never negative`() {
        assertEquals(3100.0, MoneyUtils.balanceDue(8100.0, 5000.0), 1e-9)
        assertEquals(0.0, MoneyUtils.balanceDue(100.0, 150.0), 1e-9)
    }

    @Test
    fun `formatINR uses indian grouping`() {
        Locale.setDefault(Locale.US) // ensure deterministic separators
        assertEquals("₹12,34,567", MoneyUtils.formatINR(1234567.0))
        assertEquals("₹1,00,000", MoneyUtils.formatINR(100000.0))
        assertEquals("₹8,100", MoneyUtils.formatINR(8100.0))
        assertEquals("₹1,234.50", MoneyUtils.formatINR(1234.5))
        assertEquals("₹0", MoneyUtils.formatINR(0.0))
    }

    @Test
    fun `plain has no currency symbol`() {
        Locale.setDefault(Locale.US)
        assertEquals("8,100", MoneyUtils.plain(8100.0))
        assertTrue(MoneyUtils.plain(12345678.0) == "1,23,45,678")
    }
}
