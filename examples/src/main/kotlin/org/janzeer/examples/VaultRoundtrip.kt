package org.janzeer.examples

import org.janzeer.sdk.Account
import org.janzeer.sdk.client.JsonUtil
import org.janzeer.sdk.crypto.Mnemonic
import org.janzeer.sdk.vault.Vault
import org.janzeer.sdk.vault.VaultException

/** Keep a mnemonic at rest encrypted under a password. */
fun main() {
    val mnemonic = Mnemonic.generate()
    val blob = Vault.encrypt(mnemonic, "correct horse battery staple")
    println("stored blob ${JsonUtil.stringify(blob.toMap())}")
    val restored = Vault.decrypt(blob, "correct horse battery staple")
    println("same wallet: ${Account.fromMnemonic(restored).address == Account.fromMnemonic(mnemonic).address}")
    try { Vault.decrypt(blob, "wrong") } catch (e: VaultException) { println("wrong password → ${e::class.simpleName}") }
}
