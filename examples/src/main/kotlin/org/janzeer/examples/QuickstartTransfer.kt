package org.janzeer.examples

import org.janzeer.sdk.Account
import org.janzeer.sdk.Amounts
import org.janzeer.sdk.client.Helpers
import org.janzeer.sdk.client.JanzeerClient
import org.janzeer.sdk.rpc.JanzeerRpc
import org.janzeer.sdk.tx.TxBuilder

/** Send 1.25 JNZ and wait until it is final. Needs a node with a funded wallet (sdk_conformance/e2e/node-up.sh). */
fun main() {
    val env = System.getenv()
    val client = JanzeerClient(env["JANZEER_NODE_URL"] ?: "http://localhost:7019")
    val rpc = JanzeerRpc(env["JANZEER_RPC_URL"] ?: "http://localhost:7019")
    val account = Account.fromMnemonic(env["JANZEER_E2E_MNEMONIC"] ?: "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about")
    val to = env["JANZEER_E2E_RECIPIENT"] ?: "0x598b1301acef3baba6ce25e38dd17b723f7b98b1"

    println("wallet ${account.checksumAddress} balance ${Amounts.formatJnz(client.balance(account.address))}")
    val tx = account.signTx(TxBuilder.transfer(from = account.address, to = to, amount = "1.25", nonce = client.nonce(account.address), data = "hello from janzeer-sdk"))
    client.submit(tx)
    println("submitted ${tx.hash}")
    val fin = Helpers.waitForFinality(rpc, tx.hash)
    println("FINAL in block ${fin.blockHeight} receipt ok: ${fin.receipt?.successful}")
    println("balance now ${Amounts.formatJnz(client.balance(account.address))}")
    client.close(); rpc.close()
}
