package org.janzeer.sdk.vault

import org.janzeer.sdk.crypto.Bytes
import org.janzeer.sdk.crypto.Hashes
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The persisted blob `{v: 1, salt, iv, ct}` (all base64). */
public data class VaultBlob(val salt: String, val iv: String, val ct: String) {
    /** format version */
    public val v: Int get() = Vault.VERSION

    /** JSON-safe map. */
    public fun toMap(): Map<String, Any> = linkedMapOf("v" to v, "salt" to salt, "iv" to iv, "ct" to ct)

    public companion object {
        /** From a stored map (throws [VaultException] on an unsupported version). */
        @JvmStatic public fun fromMap(m: Map<String, Any?>): VaultBlob {
            if ((m["v"] as? Number)?.toInt() != Vault.VERSION) throw VaultException("unsupported vault version ${m["v"]}")
            return VaultBlob(m["salt"] as String, m["iv"] as String, m["ct"] as String)
        }
    }
}

/** Wrong password or corrupted blob. */
public class VaultException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * A secret at rest, sealed with a password: PBKDF2-HMAC-SHA256 (250 000 rounds) → AES-256-GCM. The blob is
 * byte-compatible with the TypeScript and Dart SDK vaults (conformance fixture `vault-fixture.json`).
 */
public object Vault {
    /** blob format version */
    public const val VERSION: Int = 1
    /** PBKDF2 rounds */
    public const val ITERATIONS: Int = 250_000
    private val rnd = SecureRandom()

    private fun key(password: String, salt: ByteArray): SecretKeySpec = SecretKeySpec(Hashes.pbkdf2Sha256(Bytes.utf8(password), salt, ITERATIONS, 32), "AES")

    /** Seal [secret] under [password]. Randomized (fresh salt + IV each call). */
    @JvmStatic public fun encrypt(secret: String, password: String): VaultBlob {
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
        return VaultBlob(Bytes.toBase64(salt), Bytes.toBase64(iv), Bytes.toBase64(cipher.doFinal(Bytes.utf8(secret))))
    }

    /** Open a blob. Throws [VaultException] when the password is wrong or the blob was tampered with. */
    @JvmStatic public fun decrypt(blob: VaultBlob, password: String): String = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(password, Bytes.fromBase64(blob.salt)), GCMParameterSpec(128, Bytes.fromBase64(blob.iv))) }
        String(cipher.doFinal(Bytes.fromBase64(blob.ct)), Charsets.UTF_8)
    } catch (e: Exception) {
        throw VaultException("wrong password or corrupted vault", e)
    }
}
