package org.janzeer.sdk.crypto

/** Addresses (`AddressUtils`, `ECKey.getAddress`). */
public object Address {
    private val RE = Regex("^0x[0-9a-fA-F]{40}$")

    /** CANONICAL address = `"0x"` + lowercase hex of the first 20 bytes of keccak256(compressed public key). */
    @JvmStatic public fun fromPublicKey(compressedPublicKey: ByteArray): String {
        require(compressedPublicKey.size == 33) { "compressed (33-byte) public key expected" }
        return "0x" + Bytes.toHex(Hashes.keccak256(compressedPublicKey).copyOfRange(0, 20))
    }

    /** EIP-55 mixed-case display form (`AddressUtils.toChecksumAddress`). Presentation only — never sign or store it. */
    @JvmStatic public fun toChecksum(address: String): String {
        val body = address.removePrefix("0x").lowercase()
        val h = Bytes.toHex(Hashes.keccak256(Bytes.utf8(body)))
        val sb = StringBuilder("0x")
        for (i in body.indices) {
            val c = body[i]
            sb.append(if (c in 'a'..'f' && Character.digit(h[i], 16) >= 8) c.uppercaseChar() else c)
        }
        return sb.toString()
    }

    /** `AddressUtils.isChecksumValid`: all-lowercase (canonical), all-uppercase, or a correctly cased EIP-55 address. */
    @JvmStatic public fun isChecksumValid(address: String): Boolean {
        if (!RE.matches(address)) return false
        val body = address.substring(2)
        if (body == body.lowercase() || body == body.uppercase()) return true
        return toChecksum(address) == address
    }

    /** Shape + checksum check — exactly what the node's `@AddressChecksum` validator accepts. */
    @JvmStatic public fun isValid(address: String?): Boolean = address != null && isChecksumValid(address)

    /** Canonical lowercase form; throws [IllegalArgumentException] on an invalid address. */
    @JvmStatic public fun normalize(address: String): String {
        require(isValid(address)) { "Invalid address: $address" }
        return address.lowercase()
    }
}
