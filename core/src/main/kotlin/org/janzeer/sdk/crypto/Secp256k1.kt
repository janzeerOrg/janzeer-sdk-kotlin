package org.janzeer.sdk.crypto

import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DLSequence
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import java.math.BigInteger
import java.security.SecureRandom

/** ECDSA secp256k1 exactly as the node's `ECKey` / `SignatureUtils`: RFC-6979 deterministic, canonical low-S, DER, Base64, over the RAW 32-byte digest. */
public object Secp256k1 {
    private val curve = SECNamedCurves.getByOID(SECObjectIdentifiers.secp256k1)
    private val params = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)

    /** group order `n` */
    @JvmStatic public val order: BigInteger get() = params.n

    /** Compressed (33-byte) public key for a private scalar. */
    @JvmStatic public fun publicKey(priv: BigInteger): ByteArray = params.g.multiply(priv).getEncoded(true)

    /** Compressed public key (hex) from a private key (hex). */
    @JvmStatic public fun privateToPublic(privateKeyHex: String): String = Bytes.toHex(publicKey(BigInteger(1, Bytes.fromHex(privateKeyHex))))

    /** True when [privateKeyHex] is a valid secp256k1 scalar (32 bytes, 0 < k < n). */
    @JvmStatic public fun isValidPrivateKey(privateKeyHex: String): Boolean = try {
        val b = Bytes.fromHex(privateKeyHex)
        val d = BigInteger(1, b)
        b.size == 32 && d.signum() > 0 && d < params.n
    } catch (e: IllegalArgumentException) {
        false
    }

    /** A fresh random private key (32 bytes, 0 < k < n). */
    @JvmStatic public fun randomPrivateKey(): ByteArray {
        val rnd = SecureRandom()
        while (true) {
            val b = ByteArray(32).also(rnd::nextBytes)
            val d = BigInteger(1, b)
            if (d.signum() > 0 && d < params.n) return b
        }
    }

    /** Sign a 32-byte digest with a private key: RFC-6979 (HMAC-SHA256 k), low-S, DER, Base64. */
    @JvmStatic public fun sign(hash: ByteArray, priv: BigInteger): String {
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest())).apply { init(true, ECPrivateKeyParameters(priv, params)) }
        val sig = signer.generateSignature(hash)
        val r = sig[0]
        var s = sig[1]
        if (s > params.n.shiftRight(1)) s = params.n.subtract(s)
        return Bytes.toBase64(DERSequence(arrayOf(ASN1Integer(r), ASN1Integer(s))).encoded)
    }

    /** Sign a digest (hex) with a private key (hex). */
    @JvmStatic public fun signHash(hashHex: String, privateKeyHex: String): String = sign(Bytes.fromHex(hashHex), BigInteger(1, Bytes.fromHex(privateKeyHex)))

    /** Verify a Base64 DER signature over a digest (hex) with a compressed public key (hex). Never throws. */
    @JvmStatic public fun verifyHash(hashHex: String, signatureBase64: String, publicKeyHex: String): Boolean = try {
        val seq = ASN1InputStream(Bytes.fromBase64(signatureBase64)).use { it.readObject() as DLSequence }
        val r = (seq.getObjectAt(0) as ASN1Integer).value
        val s = (seq.getObjectAt(1) as ASN1Integer).value
        val half = params.n.shiftRight(1)
        val ok = r >= BigInteger.ONE && r < params.n && s >= BigInteger.ONE && s <= half
        ok && ECDSASigner().apply { init(false, ECPublicKeyParameters(curve.curve.decodePoint(Bytes.fromHex(publicKeyHex)), params)) }.verifySignature(Bytes.fromHex(hashHex), r, s)
    } catch (e: Exception) {
        false
    }
}
