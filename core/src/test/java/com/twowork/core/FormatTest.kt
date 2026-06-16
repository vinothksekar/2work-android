package com.twowork.core

import com.twowork.core.ui.formatMoney
import com.twowork.core.ui.prettyStatus
import com.twowork.core.ui.rupeesToPaise
import com.twowork.core.ui.shortDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

    @Test
    fun `money formats paise with grouping`() {
        assertEquals("₹1,499.00", formatMoney(149900, "INR"))
        assertEquals("₹0.00", formatMoney(0, "INR"))
        assertEquals("₹85,000.00", formatMoney(8500000, "INR"))
        assertEquals("$12.34", formatMoney(1234, "USD"))
    }

    @Test
    fun `rupees parse to paise or null`() {
        assertEquals(150000L, rupeesToPaise("1500"))
        assertEquals(150050L, rupeesToPaise("1500.50"))
        assertEquals(150005L, rupeesToPaise("1500.05"))
        assertNull(rupeesToPaise("abc"))
        assertNull(rupeesToPaise("12.345"))
        assertNull(rupeesToPaise(""))
    }

    @Test
    fun `short date takes the calendar portion`() {
        assertEquals("2026-06-14", shortDate("2026-06-14T16:34:17.000Z"))
        assertEquals("", shortDate(null))
    }

    @Test
    fun `pretty status title-cases status codes`() {
        assertEquals("Awaiting Funding", prettyStatus("awaiting_funding"))
        assertEquals("Open", prettyStatus("open"))
        assertEquals("", prettyStatus(null))
    }
}
