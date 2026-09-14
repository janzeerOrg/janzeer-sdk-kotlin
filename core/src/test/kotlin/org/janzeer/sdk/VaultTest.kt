package org.janzeer.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.janzeer.sdk.vault.Vault
import org.janzeer.sdk.vault.VaultBlob
import org.janzeer.sdk.vault.VaultException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VaultTest {
    private val f = Json.parseToJsonElement(javaClass.getResource("/vectors/vault-fixture.json")!!.readText()).jsonObject
    private fun blob(): VaultBlob { val b = f["blob"]!!.jsonObject; return VaultBlob(b["salt"]!!.jsonPrimitive.content, b["iv"]!!.jsonPrimitive.content, b["ct"]!!.jsonPrimitive.content) }

    @Test fun `decrypts the cross-SDK fixture`() {
        assertEquals(f["secret"]!!.jsonPrimitive.content, Vault.decrypt(blob(), f["password"]!!.jsonPrimitive.content))
        assertThrows(VaultException::class.java) { Vault.decrypt(blob(), f["wrongPassword"]!!.jsonPrimitive.content) }
    }
    @Test fun `round-trips a fresh blob`() {
        val b = Vault.encrypt("top secret", "pw")
        assertEquals("top secret", Vault.decrypt(b, "pw"))
        assertEquals("top secret", Vault.decrypt(VaultBlob.fromMap(b.toMap()), "pw"))
        assertThrows(VaultException::class.java) { Vault.decrypt(b.copy(ct = b.ct.dropLast(4) + "AAAA"), "pw") }
    }
}
