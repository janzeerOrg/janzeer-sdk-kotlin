package org.janzeer.sdk.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ErrorsTest {
    private fun j(s: String) = JsonUtil.parse(s)

    @Test fun `enveloped nonce mismatch`() {
        val e = Errors.fromRest(400, j("""{"timestamp":1,"version":"1.1.0","payload":{"status":400,"message":"Invalid nonce for 0x06e1c0fa9955a700876f8cb0acc7f13fba9fb8ba: expected 4, got 7","type":"INVALID_NONCE"}}"""))
        assertTrue(e is NonceMismatchException); e as NonceMismatchException
        assertEquals(4L, e.expected); assertEquals(7L, e.got); assertEquals("0x06e1c0fa9955a700876f8cb0acc7f13fba9fb8ba", e.address); assertEquals(400, e.httpStatus); assertEquals("INVALID_NONCE", e.type)
    }
    @Test fun `typed rejection, unknown type, validation array, 429, 404, sync`() {
        val r = Errors.fromRest(400, j("""{"payload":{"status":400,"message":"Incorrect signature","type":"INCORRECT_SIGNATURE"}}""")) as TxRejectedException
        assertEquals("INCORRECT_SIGNATURE", r.type); assertEquals(Transport.REST, r.transport)
        assertEquals("SOMETHING_NEW", (Errors.fromRest(400, j("""{"payload":{"status":400,"message":"x","type":"SOMETHING_NEW"}}""")) as TxRejectedException).rawType)
        val v = Errors.fromRest(400, j("""{"payload":[{"message":"must not be null"},{"message":"must not be blank"}]}""")) as ValidationException
        assertEquals(listOf("must not be null", "must not be blank"), v.messages)
        val rl = Errors.fromRest(429, j("""{"status":429,"message":"Rate limit exceeded"}"""), "2") as RateLimitedException
        assertEquals(2000L, rl.retryAfterMs)
        assertTrue(Errors.fromRest(404, j("""{"payload":{"status":404,"message":"nope"}}""")) is NotFoundException)
        assertTrue(Errors.fromRest(400, j("""{"payload":{"status":400,"message":"Blockchain is synchronizing"}}""")) is NotSynchronizedException)
        assertEquals(500, (Errors.fromRest(500, null) as ApiException).status)
    }
    @Test fun `rpc codes`() {
        assertTrue(Errors.fromRpc(JsonUtil.reqObj(j("""{"code":-32001,"message":"Incorrect signature","data":{"type":"INCORRECT_SIGNATURE"}}"""))) is TxRejectedException)
        val n = Errors.fromRpc(JsonUtil.reqObj(j("""{"code":-32001,"message":"Invalid nonce for 0x06e1c0fa9955a700876f8cb0acc7f13fba9fb8ba: expected 1, got 5","data":{"type":"INVALID_NONCE"}}"""))) as NonceMismatchException
        assertEquals(1L, n.expected); assertEquals(-32001, n.rpcCode); assertEquals(Transport.RPC, n.transport)
        assertTrue(Errors.fromRpc(JsonUtil.reqObj(j("""{"code":-32602,"message":"bad","data":["amount: must not be null"]}"""))) is RpcInvalidParamsException)
        assertTrue(Errors.fromRpc(JsonUtil.reqObj(j("""{"code":-32000,"message":"missing"}"""))) is RpcNotFoundException)
        assertTrue(Errors.fromRpc(JsonUtil.reqObj(j("""{"code":-32002,"message":"sync"}"""))) is NotSynchronizedException)
        assertTrue(Errors.fromRpc(JsonUtil.reqObj(j("""{"code":-32004,"message":"slow"}"""))) is RateLimitedException)
    }
    @Test fun `lossless json`() {
        val o = JsonUtil.reqObj(j("""{"balance":123456789.12345678,"cap":340282366920938463463374607431768211456,"n":5}"""))
        assertEquals("123456789.12345678", JsonUtil.dec(o["balance"]).toPlainString())
        assertEquals("340282366920938463463374607431768211456", JsonUtil.big(o["cap"]).toString())
        assertEquals(5L, JsonUtil.long(o["n"]))
        assertEquals("""{"a":"1.5","b":[1,true,null]}""", JsonUtil.stringify(mapOf("a" to "1.5", "b" to listOf(1, true, null))))
    }
}
