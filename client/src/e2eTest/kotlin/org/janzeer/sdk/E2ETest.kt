package org.janzeer.sdk

import org.janzeer.sdk.client.Helpers
import org.janzeer.sdk.client.JanzeerClient
import org.janzeer.sdk.client.NonceMismatchException
import org.janzeer.sdk.client.RpcInvalidParamsException
import org.janzeer.sdk.client.TxRejectedException
import org.janzeer.sdk.rpc.AddressActivityEvent
import org.janzeer.sdk.rpc.JanzeerRpc
import org.janzeer.sdk.rpc.JanzeerRpcWs
import org.janzeer.sdk.rpc.TxStatus
import org.janzeer.sdk.tx.TxBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/** The conformance flow (sdk_conformance/e2e/SPEC.md) against a running network; skipped unless `janzeer.node.url` is set. */
class E2ETest {
    private val nodeUrl = System.getProperty("janzeer.node.url")
    private val rpcUrl = System.getProperty("janzeer.rpc.url") ?: nodeUrl
    private val wsUrl = System.getProperty("janzeer.ws.url") ?: nodeUrl
    private val mnemonic = System.getProperty("janzeer.e2e.mnemonic") ?: "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val recipient = System.getProperty("janzeer.e2e.recipient") ?: "0x598b1301acef3baba6ce25e38dd17b723f7b98b1"

    @Test fun `SPEC flow (Kotlin)`() {
        assumeTrue(nodeUrl != null, "janzeer.node.url not set")
        val client = JanzeerClient(nodeUrl!!); val rpc = JanzeerRpc(rpcUrl!!)
        val acct = Account.fromMnemonic(mnemonic) // 1
        assertEquals("0x06e1c0fa9955a700876f8cb0acc7f13fba9fb8ba", acct.address)
        val info = client.info() // 2: version + network gate
        assertEquals(SpecVersion.NODE, info.version); assertEquals(SpecVersion.API, client.lastEnvelope?.version)
        val networkId = info.networkId
        (System.getProperty("janzeer.e2e.networkId") ?: System.getenv("JANZEER_NETWORK_ID"))?.let { assertEquals(it, networkId) }
        assertTrue(Regex("^[0-9a-f]{64}$").matches(info.genesisHash))
        val a0 = rpc.getAccount(acct.address); assertTrue(a0.balance.signum() > 0); val nonce = a0.nextNonce // 3
        val ws = JanzeerRpcWs.connect(wsUrl!!); val seen = CopyOnWriteArrayList<AddressActivityEvent>() // 4
        val sub = ws.subscribeAddressActivity(listOf(recipient)) { seen.add(it) }
        assertTrue(Regex("^[0-9a-f]{16}$").matches(sub.id))
        val tx = acct.signTx(TxBuilder.transfer(from = acct.address, to = recipient, amount = "1.25", nonce = nonce, data = "sdk-e2e-kotlin", fee = "0.01", networkId = networkId)) // 5
        assertTrue(acct.verify(tx.hash, tx.signature))
        assertEquals(tx.hash, client.submit(tx).hash) // 6
        assertEquals(tx.hash, rpc.send(tx).hash); assertTrue(rpc.getAccount(acct.address).pendingCount <= 1) // 7
        assertTrue(rpc.getTransactionByHash(tx.hash).status in listOf(TxStatus.PENDING, TxStatus.FINAL)) // 8
        val fin = Helpers.waitForFinality(ws, tx.hash, 120_000) // 9
        assertEquals(TxStatus.FINAL, fin.status); assertTrue(fin.blockHeight!! > 0); assertEquals(true, fin.receipt?.successful)
        assertEquals(Amounts.sub(Amounts.sub(a0.balance, "1.25"), "0.01"), Amounts.sub(client.balance(acct.address), "0")) // 10
        val deadline = System.currentTimeMillis() + 60_000 // 11
        while (seen.none { it.transaction.hash == tx.hash } && System.currentTimeMillis() < deadline) Thread.sleep(250)
        assertEquals(fin.blockHeight, seen.first { it.transaction.hash == tx.hash }.blockHeight)
        assertTrue(sub.unsubscribe()); ws.close()
        val gap = acct.signTx(TxBuilder.transfer(from = acct.address, to = recipient, amount = "1", nonce = nonce + 10, networkId = networkId)) // 12
        val nm = assertThrows(NonceMismatchException::class.java) { client.submit(gap) }; assertEquals("INVALID_NONCE", nm.type); assertEquals(nonce + 10, nm.got)
        assertEquals(-32001, assertThrows(NonceMismatchException::class.java) { rpc.send(gap) }.rpcCode)
        val next = client.nonce(acct.address) // 13
        val good = acct.signTx(TxBuilder.transfer(from = acct.address, to = recipient, amount = "1", nonce = next, networkId = networkId))
        val forged = TxBuilder.transfer(from = acct.address, to = recipient, amount = "2", nonce = next, networkId = networkId).withSignature(good.signature, acct.publicKeyHex)
        assertEquals("INCORRECT_SIGNATURE", assertThrows(TxRejectedException::class.java) { client.submit(forged) }.type)
        assertThrows(RpcInvalidParamsException::class.java) { rpc.getBalance("0x12") } // 14
        client.close(); rpc.close()
    }
}
