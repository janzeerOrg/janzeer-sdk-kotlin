package org.janzeer.sdk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AmountsTest {
    @Test fun `toScaledLong matches BigDecimal setScale HALF_UP`() {
        assertEquals(1_000_000L, Amounts.toScaledLong("0.01"))
        assertEquals(150_000_000L, Amounts.toScaledLong("1.5"))
        assertEquals(12_345_679L, Amounts.toScaledLong("0.123456785"))
        assertEquals(12_345_678L, Amounts.toScaledLong("0.123456784"))
        assertEquals(-200_000_000L, Amounts.toScaledLong("-2"))
        assertEquals(10L, Amounts.toScaledLong(BigDecimal("1e-7")))
    }
    @Test fun `format parse arithmetic`() {
        assertEquals("1.50000000", Amounts.fromScaledLong(150_000_000L))
        assertEquals("1.5 JNZ", Amounts.formatJnz("1.50000000"))
        assertEquals("1,234.5", Amounts.formatJnz("1234.5", group = true, ticker = false))
        assertEquals("2.00 JNZ", Amounts.formatJnz("2", decimals = 2, trim = false))
        assertEquals("1234.5", Amounts.parseJnz("1,234.5 JNZ"))
        assertThrows(IllegalArgumentException::class.java) { Amounts.parseJnz("abc") }
        assertTrue(Amounts.isValid("0.00000001"))
        assertFalse(Amounts.isValid("0.000000001"))
        assertEquals(0, Amounts.compare("1.10", "1.1"))
        assertEquals(-1, Amounts.compare("0.01", "0.1"))
        assertEquals("1.26000000", Amounts.add("1.25", "0.01"))
        assertEquals("98.74000000", Amounts.sub("100", "1.26"))
    }
}
