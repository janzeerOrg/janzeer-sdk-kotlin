package org.janzeer.sdk.client

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.janzeer.sdk.Account
import org.janzeer.sdk.tx.TxBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigInteger

class ClientTest {
    private lateinit var server: MockWebServer
    private fun env(payload: String) = """{"timestamp":1757800000000,"version":"1.1.0","payload":$payload}"""
    @BeforeEach fun up() { server = MockWebServer(); server.start() }
    @AfterEach fun down() { server.shutdown() }

    @Test fun `normalizes url, unwraps envelope losslessly, 404 defaults`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when (request.path) {
                "/api/v1/wallets/0xabc" -> MockResponse().setBody(env("123456789.12345678"))
                else -> MockResponse().setResponseCode(404).setBody(env("""{"status":404,"message":"no"}"""))
            }
        }
        val c = JanzeerClient(server.url("/").toString().trimEnd('/'))
        assertTrue(c.baseUrl.endsWith("/api/v1/"))
        assertEquals("123456789.12345678", c.balance("0xabc").toPlainString())
        assertEquals("1.1.0", c.lastEnvelope?.version)
        assertEquals(0, c.balance("0xnew").signum()); assertEquals(0L, c.nonce("0xnew")); assertNull(c.transfers.get("dead"))
    }

    @Test fun `pages, typed models, query params`() {
        var seen = ""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse { seen = request.path ?: ""; return if (seen.startsWith("/api/v1/tokens")) MockResponse().setBody(env("""{"total":1,"page":0,"pageSize":5,"totalPages":1,"list":[{"tokenId":"ab","symbol":"JZT","name":"T","decimals":0,"cap":340282366920938463463374607431768211456,"totalSupply":1,"issuer":"0x1"}]}""")) else MockResponse().setBody(env("""{"total":1,"list":[{"hash":"h","timestamp":1,"fee":0.01,"senderAddress":"0xa","senderSignature":"s","senderPublicKey":"p","status":true,"amount":1.5,"recipientAddress":"0xb","data":null,"results":null,"blockHash":null}]}""")) }
        }
        val c = JanzeerClient(server.url("/").toString())
        assertEquals(BigInteger("340282366920938463463374607431768211456"), c.tokens.list(PageQuery(size = 5)).list.single().cap)
        assertTrue(seen.contains("size=5"))
        val t = c.transfers.list(TxListQuery(address = "0xa", unconfirmed = true)).list.single()
        assertEquals("0.01", t.fee.toPlainString()); assertEquals("1.5", t.amount.toPlainString()); assertFalse(t.isFinal)
        assertTrue(seen.contains("unconfirmed=true"))
    }

    @Test fun `submit posts the body and maps rejections`() {
        val acct = Account.fromPrivateKey("6deadaf98b65c46612b57db1551da40d79ed6a759f23f9cf137612f8ef238efc")
        val tx = acct.signTx(TxBuilder.transfer(from = acct.address, to = "0x598b1301acef3baba6ce25e38dd17b723f7b98b1", amount = "1", nonce = 3, timestamp = 1))
        var n = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertEquals("/api/v1/transactions/transfers", request.path)
                val body = JsonUtil.reqObj(JsonUtil.parse(request.body.readUtf8()))
                assertEquals(tx.hash, JsonUtil.str(body["hash"])); assertEquals("1", JsonUtil.str(body["amount"])); assertEquals(3L, JsonUtil.long(body["nonce"]))
                n++
                return when (n) {
                    1 -> MockResponse().setResponseCode(400).setBody(env("""{"status":400,"message":"Invalid nonce for ${acct.address}: expected 4, got 3","type":"INVALID_NONCE"}"""))
                    2 -> MockResponse().setResponseCode(400).setBody(env("""{"status":400,"message":"Incorrect signature","type":"INCORRECT_SIGNATURE"}"""))
                    3 -> MockResponse().setResponseCode(400).setBody(env("""[{"message":"amount must not be null"}]"""))
                    else -> MockResponse().setResponseCode(201).setBody(env(JsonUtil.stringify(tx.toRestBody() + mapOf("status" to true, "blockHash" to null))))
                }
            }
        }
        val c = JanzeerClient(server.url("/").toString())
        val nm = assertThrows(NonceMismatchException::class.java) { c.submit(tx) }
        assertEquals(4L, nm.expected); assertEquals(3L, nm.got)
        assertEquals("INCORRECT_SIGNATURE", assertThrows(TxRejectedException::class.java) { c.submit(tx) }.type)
        assertThrows(ValidationException::class.java) { c.submit(tx) }
        val ok = c.submit(tx) as TransferTx
        assertEquals(tx.hash, ok.hash); assertEquals("1", ok.amount.toPlainString())
    }

    @Test fun `retries once on 429 honouring Retry-After`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0").setBody("""{"status":429,"message":"Rate limit exceeded"}"""))
        server.enqueue(MockResponse().setBody(env("42")))
        assertEquals(42L, JanzeerClient(server.url("/").toString()).uptime())
        assertEquals(2, server.requestCount)
    }
}
