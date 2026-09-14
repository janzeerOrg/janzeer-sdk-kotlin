package org.janzeer.sdk.crypto

import org.bouncycastle.crypto.digests.KeccakDigest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter

/** Hash primitives exactly as the node uses them (`HashUtils`). BouncyCastle lightweight API only (Android-safe). */
public object Hashes {
    /** SHA-256 */
    @JvmStatic public fun sha256(data: ByteArray): ByteArray = ByteArray(32).also { SHA256Digest().apply { update(data, 0, data.size) }.doFinal(it, 0) }

    /** `HashUtils.doubleSha256` — the transaction hash function. */
    @JvmStatic public fun doubleSha256(data: ByteArray): ByteArray = sha256(sha256(data))

    /** Double-SHA256 as lowercase hex — what the node calls the transaction `hash`. */
    @JvmStatic public fun hashPreimage(preimage: ByteArray): String = Bytes.toHex(doubleSha256(preimage))

    /** Keccak-256 (the Ethereum variant, NOT NIST SHA3-256) — addresses and the EIP-55 checksum. */
    @JvmStatic public fun keccak256(data: ByteArray): ByteArray = ByteArray(32).also { KeccakDigest(256).apply { update(data, 0, data.size) }.doFinal(it, 0) }

    /** HMAC-SHA512 */
    @JvmStatic public fun hmacSha512(key: ByteArray, msg: ByteArray): ByteArray {
        val mac = HMac(SHA512Digest()).apply { init(KeyParameter(key)); update(msg, 0, msg.size) }
        return ByteArray(mac.macSize).also { mac.doFinal(it, 0) }
    }

    /** PBKDF2-HMAC-SHA512 */
    @JvmStatic public fun pbkdf2Sha512(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        val gen = PKCS5S2ParametersGenerator(SHA512Digest()).apply { init(password, salt, iterations) }
        return (gen.generateDerivedMacParameters(dkLen * 8) as KeyParameter).key
    }

    /** PBKDF2-HMAC-SHA256 */
    @JvmStatic public fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        val gen = PKCS5S2ParametersGenerator(SHA256Digest()).apply { init(password, salt, iterations) }
        return (gen.generateDerivedMacParameters(dkLen * 8) as KeyParameter).key
    }
}
