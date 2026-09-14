package org.janzeer.sdk.crypto

import java.nio.ByteBuffer
import java.util.Base64

/** Byte helpers shared by the preimage builders (all integers big-endian, like Java's ByteBuffer). */
public object Bytes {
    private const val HEX = "0123456789abcdef"

    /** bytes → lowercase hex */
    @JvmStatic public fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) { sb.append(HEX[(b.toInt() shr 4) and 0x0f]); sb.append(HEX[b.toInt() and 0x0f]) }
        return sb.toString()
    }

    /** Lenient hex → bytes: accepts an optional `0x` prefix and either case. */
    @JvmStatic public fun fromHex(hex: String): ByteArray {
        val h = if (hex.startsWith("0x") || hex.startsWith("0X")) hex.substring(2) else hex
        require(h.length % 2 == 0 && h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "invalid hex: $hex" }
        return ByteArray(h.length / 2) { i -> ((Character.digit(h[i * 2], 16) shl 4) or Character.digit(h[i * 2 + 1], 16)).toByte() }
    }

    /** UTF-8 encode */
    @JvmStatic public fun utf8(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)

    /** Concatenate byte arrays. */
    @JvmStatic public fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var o = 0
        for (p in parts) { System.arraycopy(p, 0, out, o, p.size); o += p.size }
        return out
    }

    /** 8-byte big-endian (Java `putLong`). */
    @JvmStatic public fun i64be(value: Long): ByteArray = ByteBuffer.allocate(8).putLong(value).array()

    /** 4-byte big-endian (BIP32 `ser32` / Java `putInt`). */
    @JvmStatic public fun ser32(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()

    /** `int32(len) ‖ utf8(s)` — the node's length-prefixed string (`TokenPayload`). `null` → empty. */
    @JvmStatic public fun lenPrefixed(s: String?): ByteArray {
        val b = utf8(s ?: "")
        return concat(ser32(b.size), b)
    }

    /** bytes → base64 */
    @JvmStatic public fun toBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /** base64 → bytes */
    @JvmStatic public fun fromBase64(b64: String): ByteArray = Base64.getDecoder().decode(b64)
}
