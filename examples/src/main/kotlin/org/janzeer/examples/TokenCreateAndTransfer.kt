package org.janzeer.examples

import org.janzeer.sdk.Account
import org.janzeer.sdk.client.Helpers
import org.janzeer.sdk.client.JanzeerClient
import org.janzeer.sdk.rpc.JanzeerRpc
import org.janzeer.sdk.tx.TxBuilder

/** Create a JZT-1 token, then move some units. Only a VALIDATOR's wallet may create tokens (node-up.sh exports one on the dev net). */
fun main() {
    val env = System.getenv()
    val issuerMnemonic = env["JANZEER_E2E_VALIDATOR_MNEMONIC"] ?: run { println("JANZEER_E2E_VALIDATOR_MNEMONIC not set — token CREATE needs a validator wallet; skipping"); return }
    val client = JanzeerClient(env["JANZEER_NODE_URL"] ?: "http://localhost:7019")
    val rpc = JanzeerRpc(env["JANZEER_RPC_URL"] ?: "http://localhost:7019")
    val issuer = Account.fromMnemonic(issuerMnemonic)
    val holder = env["JANZEER_E2E_RECIPIENT"] ?: "0x598b1301acef3baba6ce25e38dd17b723f7b98b1"

    val create = issuer.signTx(TxBuilder.Token.create(from = issuer.address, symbol = "DEMO", name = "Demo Token", nonce = client.nonce(issuer.address), decimals = 2, cap = 1_000_000L, amount = 500_000L))
    val created = Helpers.sendAndWait(rpc, create, submitVia = client)
    val tokenId = created.tokenId!! // for CREATE the tokenId is the transaction hash
    println("token $tokenId created in block ${created.blockHeight}")
    val def = client.tokens.get(tokenId)
    println("definition ${def?.symbol} ${def?.name} decimals ${def?.decimals} cap ${def?.cap} supply ${def?.totalSupply}")
    val move = issuer.signTx(TxBuilder.Token.transfer(from = issuer.address, tokenId = tokenId, amount = 12_345L, recipient = holder, nonce = client.nonce(issuer.address)))
    Helpers.sendAndWait(rpc, move, submitVia = client)
    for (h in client.tokens.holders(tokenId).list) println("holder ${h.holder} balance ${h.balance}")
    client.close(); rpc.close()
}
