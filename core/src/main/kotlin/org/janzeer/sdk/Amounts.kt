package org.janzeer.sdk

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Native-coin amount handling. Amounts cross this SDK as decimal strings / [BigDecimal] — never doubles — and are
 * scaled to an int64 exactly the way the node's `ByteUtils.toScaledLong` does.
 */
public object Amounts {
    private val SCALE: BigInteger = BigInteger.TEN.pow(Janzeer.DECIMALS)
    private val DECIMAL_RE = Regex("^-?\\d+(\\.\\d+)?$")

    /** Canonical decimal string (no exponent, no `+`). Throws [IllegalArgumentException] on anything else. */
    @JvmStatic public fun normalize(amount: Any): String {
        val s = when (amount) {
            is BigDecimal -> amount.toPlainString()
            is BigInteger, is Int, is Long -> amount.toString()
            is Double, is Float -> BigDecimal(amount.toString()).toPlainString()
            else -> amount.toString().trim()
        }
        require(DECIMAL_RE.matches(s)) { "Not a decimal amount: $amount" }
        return s
    }

    /** True when [amount] is a plain decimal with at most 8 fractional digits (what the node accepts). */
    @JvmStatic public fun isValid(amount: Any): Boolean = try {
        BigDecimal(normalize(amount)).scale() <= Janzeer.DECIMALS
    } catch (e: IllegalArgumentException) {
        false
    }

    /** `ByteUtils.toScaledLong`: `BigDecimal(amount).setScale(8, HALF_UP) × 10^8` as a long. Pinned by the vectors. */
    @JvmStatic public fun toScaledLong(amount: Any): Long =
        BigDecimal(normalize(amount)).setScale(Janzeer.DECIMALS, RoundingMode.HALF_UP).unscaledValue().longValueExact()

    /** Inverse of [toScaledLong]: base units → decimal string with exactly 8 fractional digits (`"1.50000000"`). */
    @JvmStatic public fun fromScaledLong(scaled: Long): String = BigDecimal.valueOf(scaled, Janzeer.DECIMALS).toPlainString()

    /** Exact [BigDecimal] of an amount. */
    @JvmStatic public fun toBigDecimal(amount: Any): BigDecimal = BigDecimal(normalize(amount))

    /** Human-readable JNZ amount: `formatJnz("1.50000000")` → `"1.5 JNZ"`. Truncates beyond [decimals]. */
    @JvmStatic @JvmOverloads public fun formatJnz(amount: Any, decimals: Int = Janzeer.DECIMALS, trim: Boolean = true, ticker: Boolean = true, group: Boolean = false): String {
        val s = normalize(amount)
        val neg = s.startsWith("-")
        val t = if (neg) s.substring(1) else s
        val dot = t.indexOf('.')
        var intPart = if (dot < 0) t else t.substring(0, dot)
        var frac = if (dot < 0) "" else t.substring(dot + 1)
        if (frac.length > decimals) frac = frac.substring(0, decimals)
        if (trim) frac = frac.trimEnd('0') else if (frac.length < decimals) frac = frac.padEnd(decimals, '0')
        if (group) intPart = intPart.reversed().chunked(3).joinToString(",").reversed()
        val out = (if (neg) "-" else "") + intPart + (if (frac.isEmpty()) "" else ".$frac")
        return if (ticker) "$out ${Janzeer.TICKER}" else out
    }

    /** Parse user input like `"1,234.5 JNZ"` into a canonical amount string. */
    @JvmStatic public fun parseJnz(input: String): String =
        normalize(input.replace(Regex("\\s*${Janzeer.TICKER}\\s*$", RegexOption.IGNORE_CASE), "").replace(",", "").trim())

    /** Exact decimal comparison: -1, 0 or 1. */
    @JvmStatic public fun compare(a: Any, b: Any): Int = toScaledLong(a).compareTo(toScaledLong(b)).coerceIn(-1, 1)

    /** Exact `a + b` as a canonical 8-decimal string. */
    @JvmStatic public fun add(a: Any, b: Any): String = fromScaledLong(toScaledLong(a) + toScaledLong(b))

    /** Exact `a - b` as a canonical 8-decimal string. */
    @JvmStatic public fun sub(a: Any, b: Any): String = fromScaledLong(toScaledLong(a) - toScaledLong(b))
}
