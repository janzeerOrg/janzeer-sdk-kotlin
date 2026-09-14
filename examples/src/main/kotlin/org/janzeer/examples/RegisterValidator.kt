package org.janzeer.examples

import org.janzeer.sdk.Account
import org.janzeer.sdk.Janzeer
import org.janzeer.sdk.client.JanzeerClient
import org.janzeer.sdk.tx.TxBuilder

/** Register (and later exit) a validator node. The 2000 JNZ deposit is NON-refundable — dry run unless RUN_FOR_REAL=1. */
fun main() {
    val env = System.getenv()
    val client = JanzeerClient(env["JANZEER_NODE_URL"] ?: "http://localhost:7019")
    val wallet = Account.fromMnemonic(env["JANZEER_E2E_MNEMONIC"] ?: "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about")
    val validatorKey = env["VALIDATOR_KEY"] ?: client.info().nodeKey
    val nonce = client.nonce(wallet.address)
    val register = wallet.signTx(TxBuilder.registerValidator(from = wallet.address, validatorKey = validatorKey, nonce = nonce))
    println("register ${validatorKey.take(12)}… fee ${Janzeer.VALIDATOR_FEE} deposit ${Janzeer.VALIDATOR_DEPOSIT} ${register.toRestBody()}")
    val exit = wallet.signTx(TxBuilder.exitValidator(from = wallet.address, validatorKey = validatorKey, nonce = nonce + 1))
    println("exit (pipelined nonce) ${exit.toRestBody()}")
    if (env["RUN_FOR_REAL"] == "1") println("submitted ${client.submit(register).hash}") else println("dry run — set RUN_FOR_REAL=1 to submit the registration")
    client.close()
}
