package org.janzeer.sdk

import org.janzeer.sdk.crypto.Address
import org.janzeer.sdk.crypto.Bytes
import org.janzeer.sdk.crypto.Hd
import org.janzeer.sdk.crypto.Mnemonic
import org.janzeer.sdk.crypto.Secp256k1
import org.janzeer.sdk.tx.SignedTx
import org.janzeer.sdk.tx.UnsignedTx
import java.math.BigInteger

/** A signing identity: private key → public key → address. The node never sees the private key. */
public class Account private constructor(private val priv: ByteArray) {
    private val d: BigInteger = BigInteger(1, priv)
    /** compressed secp256k1 public key, hex (66 chars) */
    public val publicKeyHex: String = Bytes.toHex(Secp256k1.publicKey(d))
    /** canonical lowercase address */
    public val address: String = Address.fromPublicKey(Secp256k1.publicKey(d))
    /** EIP-55 display form of [address] */
    public val checksumAddress: String = Address.toChecksum(address)

    /** The private key as hex. Handle with care: never log or send it. */
    public val privateKeyHex: String get() = Bytes.toHex(priv)

    /** RFC-6979 low-S DER signature (Base64) over a 32-byte digest given as hex. */
    public fun sign(hashHex: String): String = Secp256k1.sign(Bytes.fromHex(hashHex), d)

    /** Verify a signature made by this account. */
    public fun verify(hashHex: String, signatureBase64: String): Boolean = Secp256k1.verifyHash(hashHex, signatureBase64, publicKeyHex)

    /** Hash and sign an [UnsignedTx]. Throws if the tx names another sender. */
    public fun signTx(tx: UnsignedTx): SignedTx {
        require(tx.senderAddress == address) { "tx sender ${tx.senderAddress} is not this account ($address)" }
        return tx.withSignature(sign(tx.hash()), publicKeyHex)
    }

    override fun toString(): String = "Account($checksumAddress)"

    public companion object {
        /** Wallet account from a BIP39 mnemonic (Janzeer seed, path `m/0/0/0`). */
        @JvmStatic @JvmOverloads public fun fromMnemonic(mnemonic: String, passphrase: String = ""): Account {
            require(Mnemonic.validate(mnemonic)) { "invalid mnemonic (unknown word or bad checksum)" }
            return fromSeed(Mnemonic.toSeed(mnemonic, passphrase))
        }

        /** Account from a 64-byte seed (default path `m/0/0/0`; any non-hardened path accepted). */
        @JvmStatic @JvmOverloads public fun fromSeed(seed: ByteArray, path: String = Hd.DEFAULT_PATH): Account = Account(Hd.derivePath(seed, path).priv)

        /** Account from a raw private key (hex, optional `0x`). */
        @JvmStatic public fun fromPrivateKey(privateKeyHex: String): Account {
            require(Secp256k1.isValidPrivateKey(privateKeyHex)) { "invalid secp256k1 private key" }
            return Account(Bytes.fromHex(privateKeyHex))
        }

        /** A fresh random key (not mnemonic-backed; prefer `Mnemonic.generate()` + [fromMnemonic] for user wallets). */
        @JvmStatic public fun random(): Account = Account(Secp256k1.randomPrivateKey())
    }
}
