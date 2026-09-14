package org.janzeer.sdk.crypto

import java.math.BigInteger
import java.text.Normalizer

/** An extended private key node: 32-byte private key + 32-byte chain code. */
public class HdNode(public val priv: ByteArray, public val chainCode: ByteArray)

/**
 * Key derivation — BIP39/BIP32-SHAPED but with Janzeer's own constants (`SeedConstant.SALT`), so stock bip32
 * libraries will NOT reproduce it. Mirrors `SeedCalculator`, `ExtendedKey`, `DerivationKeysHelper`.
 */
public object Hd {
    /** Used BOTH as the PBKDF2 salt base (instead of BIP39's `"mnemonic"`) AND as the root HMAC key. */
    public const val SALT: String = "@_Janzeer_Blockchain_@"
    /** The only derivation path wallets use; all three levels are non-hardened. */
    public const val DEFAULT_PATH: String = "m/0/0/0"

    /** `SeedCalculator.calculateSeed` — PBKDF2-HMAC-SHA512, 2048 rounds, 64 bytes, salt `SALT + passphrase`, NFKD inputs. */
    @JvmStatic @JvmOverloads public fun mnemonicToSeed(mnemonic: String, passphrase: String = ""): ByteArray =
        Hashes.pbkdf2Sha512(Bytes.utf8(Normalizer.normalize(mnemonic, Normalizer.Form.NFKD)), Bytes.utf8(SALT + Normalizer.normalize(passphrase, Normalizer.Form.NFKD)), 2048, 64)

    /** `ExtendedKey.root` — `I = HMAC-SHA512(key = SALT, msg = seed)`; `IL` = private key, `IR` = chain code. */
    @JvmStatic public fun seedToMasterKey(seed: ByteArray): HdNode {
        val i = Hashes.hmacSha512(Bytes.utf8(SALT), seed)
        return HdNode(i.copyOfRange(0, 32), i.copyOfRange(32, 64))
    }

    /** `ExtendedKey.getChild` — standard BIP32 non-hardened CKDpriv (`index` < 2^31). */
    @JvmStatic public fun deriveChild(node: HdNode, index: Int): HdNode {
        require(index >= 0) { "non-hardened index expected" }
        val parent = BigInteger(1, node.priv)
        val i = Hashes.hmacSha512(node.chainCode, Bytes.concat(Secp256k1.publicKey(parent), Bytes.ser32(index)))
        val child = BigInteger(1, i.copyOfRange(0, 32)).add(parent).mod(Secp256k1.order)
        return HdNode(to32(child), i.copyOfRange(32, 64))
    }

    /** Derive a path like `m/0/0/0` (non-hardened segments only). */
    @JvmStatic @JvmOverloads public fun derivePath(seed: ByteArray, path: String = DEFAULT_PATH): HdNode {
        val parts = path.split('/')
        require(parts.first() == "m") { "path must start with m/: $path" }
        var node = seedToMasterKey(seed)
        for (p in parts.drop(1)) {
            require(!(p.endsWith("'") || p.endsWith("h") || p.endsWith("H"))) { "hardened derivation is not part of the Janzeer scheme" }
            node = deriveChild(node, p.toInt())
        }
        return node
    }

    /** The wallet key: seed → `m/0/0/0`. */
    @JvmStatic public fun deriveDefault(seed: ByteArray): HdNode = derivePath(seed)

    internal fun to32(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return when {
            b.size == 32 -> b
            b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
            else -> ByteArray(32 - b.size) + b
        }
    }
}
