package org.janzeer.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.janzeer.sdk.crypto.Address
import org.janzeer.sdk.crypto.Bytes
import org.janzeer.sdk.crypto.Hashes
import org.janzeer.sdk.crypto.Hd
import org.janzeer.sdk.crypto.Mnemonic
import org.janzeer.sdk.crypto.Secp256k1
import org.janzeer.sdk.tx.Payloads
import org.janzeer.sdk.tx.TxBuilder
import org.janzeer.sdk.tx.UnsignedTx
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Conformance: every entry of sdk_conformance/vectors/wallet-parity-vectors.json must be reproduced byte-for-byte. */
class VectorsTest {
    private val v: JsonObject = Json.parseToJsonElement(javaClass.getResource("/vectors/wallet-parity-vectors.json")!!.readText()).jsonObject
    private val hd = v["hd"]!!.jsonObject
    private val rawKey = v["rawKey"]!!.jsonObject
    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content
    private fun JsonObject.l(k: String) = this[k]!!.jsonPrimitive.content.toLong()
    private fun JsonObject.opt(k: String) = this[k]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content
    private fun signer() = Account.fromPrivateKey(rawKey.s("privHex"))

    @Test fun `vendored vectors match the conformance kit checksums`() {
        for (line in javaClass.getResource("/vectors/SHA256SUMS")!!.readText().trim().lines()) {
            val (sha, name) = line.trim().split(Regex("\\s+"))
            assertEquals(sha, Bytes.toHex(Hashes.sha256(javaClass.getResource("/vectors/$name")!!.readBytes())), name)
        }
        assertEquals(SpecVersion.VECTORS, v.s("vectorsVersion").toInt())
        assertEquals(Janzeer.NETWORK_ID, v.s("networkId"))
    }

    @Test fun `mnemonic to seed to m0-0-0 to address`() {
        val seed = Hd.mnemonicToSeed(hd.s("mnemonic"), hd.s("passphrase"))
        assertEquals(hd.s("seedHex"), Bytes.toHex(seed))
        assertEquals(hd.s("privHex"), Bytes.toHex(Hd.deriveDefault(seed).priv))
        val a = Account.fromMnemonic(hd.s("mnemonic"), hd.s("passphrase"))
        assertEquals(hd.s("privHex"), a.privateKeyHex)
        assertEquals(hd.s("pubHex"), a.publicKeyHex)
        assertEquals(hd.s("address"), a.address)
        assertTrue(Mnemonic.validate(hd.s("mnemonic")))
        assertEquals(hd.s("mnemonic"), Mnemonic.fromEntropy(Mnemonic.toEntropy(hd.s("mnemonic"))))
    }

    @Test fun `raw key to address`() {
        val a = signer()
        assertEquals(rawKey.s("pubHex"), a.publicKeyHex)
        assertEquals(rawKey.s("address"), a.address)
    }

    @Test fun `addresses EIP-55 overlay and scaled table`() {
        for (e in v["addresses"]!!.jsonArray) {
            val o = e.jsonObject
            assertEquals(o.s("checksum"), Address.toChecksum(o.s("lower")))
            assertTrue(Address.isChecksumValid(o.s("checksum")))
        }
        for (e in v["scaled"]!!.jsonArray) {
            val o = e.jsonObject
            assertEquals(o.l("out"), Amounts.toScaledLong(o.s("in")), o.s("in"))
        }
    }

    private fun check(unsigned: UnsignedTx, t: JsonObject) {
        t.opt("preimageHex")?.let { assertEquals(it, Bytes.toHex(unsigned.preimage())) }
        assertEquals(t.s("hash"), unsigned.hash())
        val signed = signer().signTx(unsigned)
        assertEquals(t.s("signature"), signed.signature)
        assertEquals(t.s("publicKey"), signed.senderPublicKey)
        assertTrue(signer().verify(signed.hash, signed.signature))
        assertFalse(Secp256k1.verifyHash(signed.hash, signed.signature, hd.s("pubHex")))
        assertEquals(t.s("hash"), signed.toRestBody()["hash"])
        assertEquals(t.s("signature"), signed.toRestBody()["senderSignature"])
    }

    @Test fun transferTx() {
        val t = v["transferTx"]!!.jsonObject
        check(TxBuilder.transfer(from = t.s("senderAddress"), to = t.s("recipientAddress"), amount = t.s("amount"), nonce = t.l("nonce"), data = t.s("data"), fee = t.s("fee"), timestamp = t.l("timestamp")), t)
    }

    @Test fun transferNoMemoTx() {
        val t = v["transferNoMemoTx"]!!.jsonObject
        val u = TxBuilder.transfer(from = t.s("senderAddress"), to = t.s("recipientAddress"), amount = t.s("amount"), nonce = t.l("nonce"), fee = t.s("fee"), timestamp = t.l("timestamp"))
        check(u, t)
        assertFalse(signer().signTx(u).toRestBody().containsKey("data"))
    }

    @Test fun promoterTx() {
        val t = v["promoterTx"]!!.jsonObject
        check(TxBuilder.registerValidator(from = t.s("senderAddress"), validatorKey = t.s("promoterKey"), nonce = t.l("nonce"), amount = t.s("amount"), fee = t.s("fee"), timestamp = t.l("timestamp")), t)
    }

    @Test fun exitPromoterTx() {
        val t = v["exitPromoterTx"]!!.jsonObject
        check(TxBuilder.exitValidator(from = t.s("senderAddress"), validatorKey = t.s("promoterKey"), nonce = t.l("nonce"), fee = t.s("fee"), timestamp = t.l("timestamp")), t)
    }

    @Test fun tokenCreateTx() {
        val t = v["tokenCreateTx"]!!.jsonObject
        check(TxBuilder.Token.create(from = t.s("senderAddress"), symbol = t.s("symbol"), name = t.s("name"), nonce = t.l("nonce"), decimals = t.s("decimals").toInt(), cap = t.opt("cap"), amount = t.opt("amount"), fee = t.s("fee"), timestamp = t.l("timestamp")), t)
    }

    @Test fun tokenTransferTx() {
        val t = v["tokenTransferTx"]!!.jsonObject
        check(TxBuilder.Token.transfer(from = t.s("senderAddress"), tokenId = t.s("tokenId"), amount = t.s("amount"), recipient = t.s("recipient"), nonce = t.l("nonce"), fee = t.s("fee"), timestamp = t.l("timestamp")), t)
    }

    @Test fun `low-level payload functions`() {
        val t = v["transferTx"]!!.jsonObject
        assertEquals(t.s("preimageHex"), Bytes.toHex(Payloads.transactionBytes(t.l("timestamp"), t.s("fee"), t.l("nonce"), t.s("senderAddress"), Payloads.transfer(t.s("amount"), t.s("recipientAddress"), t.s("data")))))
        val p = v["promoterTx"]!!.jsonObject
        assertEquals(p.s("preimageHex"), Bytes.toHex(Payloads.transactionBytes(p.l("timestamp"), p.s("fee"), p.l("nonce"), p.s("senderAddress"), Payloads.registerValidator(p.s("amount"), p.s("promoterKey")))))
    }
}
