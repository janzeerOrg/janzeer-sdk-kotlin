package org.janzeer.sdk.client

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.janzeer.sdk.rpc.BatchRequest
import org.janzeer.sdk.rpc.JanzeerRpc
import org.janzeer.sdk.rpc.JanzeerRpcWs
import org.janzeer.sdk.rpc.NewBlocksParams
import org.janzeer.sdk.rpc.TxStatus
import org.janzeer.sdk.rpc.WsEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class RpcTest {
    private lateinit var server: MockWebServer
    private val sockets = CopyOnWriteArrayList<WebSocket>()
    @BeforeEach fun up() { server = MockWebServer(); server.start() }
    @AfterEach fun down() {
        // MockWebServer's shutdown waits for its per-connection threads; a server-side WebSocket that the client already
        // dropped can linger past its 5 s grace and throw — test infrastructure, not the SDK.
        for (s in sockets) runCatching { s.close(1000, "bye") }
        Thread.sleep(100)
        runCatching { server.shutdown() }
    }

    private fun one(r: JsonObject): String {
        val id = JsonUtil.optLong(r["id"])
        val p = r["params"] as? JsonObject
        return when (JsonUtil.optStr(r["method"])) {
            "janzeer_getNonce" -> """{"jsonrpc":"2.0","id":$id,"result":7}"""
            "janzeer_getBalance" -> if (JsonUtil.optStr(p?.get("address")) == "0x12") """{"jsonrpc":"2.0","id":$id,"error":{"code":-32602,"message":"Invalid address '0x12'"}}""" else """{"jsonrpc":"2.0","id":$id,"result":1000.00000001}"""
            "janzeer_getTransactionByHash" -> """{"jsonrpc":"2.0","id":$id,"result":{"hash":"${JsonUtil.optStr(p?.get("hash"))}","type":null,"status":"UNKNOWN"}}"""
            "janzeer_getToken" -> """{"jsonrpc":"2.0","id":$id,"error":{"code":-32000,"message":"Token not found"}}"""
            "janzeer_sendTransfer" -> """{"jsonrpc":"2.0","id":$id,"error":{"code":-32001,"message":"Invalid nonce for 0x06e1c0fa9955a700876f8cb0acc7f13fba9fb8ba: expected 1, got 5","data":{"type":"INVALID_NONCE"}}}"""
            else -> """{"jsonrpc":"2.0","id":$id,"error":{"code":-32601,"message":"Method not found"}}"""
        }
    }

    @Test fun `http rpc maps results and errors, batches keep order`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = JsonUtil.parse(request.body.readUtf8())
                val out = if (body is JsonArray) body.joinToString(",", "[", "]") { one(it as JsonObject) } else one(body as JsonObject)
                return MockResponse().setBody(out)
            }
        }
        val rpc = JanzeerRpc(server.url("/api/v1").toString())
        assertTrue(rpc.url.endsWith("/rpc"))
        assertEquals(7L, rpc.getNonce("0xa"))
        assertEquals("1000.00000001", rpc.getBalance("0xa").toPlainString())
        assertThrows(RpcInvalidParamsException::class.java) { rpc.getBalance("0x12") }
        assertThrows(RpcNotFoundException::class.java) { rpc.getToken("x") }
        assertEquals(1L, assertThrows(NonceMismatchException::class.java) { rpc.sendTransfer(emptyMap()) }.expected)
        assertEquals(TxStatus.UNKNOWN, rpc.getTransactionByHash("ab").status)
        val r = rpc.batch(listOf(BatchRequest("janzeer_getNonce", mapOf("address" to "0xa")), BatchRequest("janzeer_nope"), BatchRequest("janzeer_getBalance", mapOf("address" to "0xa"))))
        assertTrue(r[0].ok); assertFalse(r[1].ok); assertEquals("1000.00000001", JsonUtil.dec(r[2].result).toPlainString())
    }

    @Test fun `ws calls, subscribes, notifies, resubscribes after a drop`() {
        var subs = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) { sockets.add(webSocket) }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val m = JsonUtil.reqObj(JsonUtil.parse(text)); val id = JsonUtil.optLong(m["id"])
                    when (JsonUtil.optStr(m["method"])) {
                        "janzeer_getTip" -> webSocket.send("""{"jsonrpc":"2.0","id":$id,"result":{"type":"main","height":10,"hash":"h","previousHash":"p","timestamp":1,"producer":"k","signature":"s","epochIndex":1,"transactionsCount":0}}""")
                        "janzeer_subscribe" -> {
                            val sid = "sub${++subs}"; val kind = JsonUtil.str((m["params"] as JsonObject)["kind"])
                            webSocket.send("""{"jsonrpc":"2.0","id":$id,"result":"$sid"}""")
                            val result = if (kind == "newBlocks") """{"type":"main","height":11,"hash":"h2","previousHash":"h","timestamp":2,"producer":"k","signature":"s"}""" else """{"blockHeight":11,"blockHash":"h2","transaction":{"hash":"tx1","type":"transfer","status":"FINAL","amount":1.25}}"""
                            webSocket.send("""{"jsonrpc":"2.0","method":"janzeer_subscription","params":{"subscription":"$sid","kind":"$kind","result":$result}}""")
                        }
                        "janzeer_unsubscribe" -> webSocket.send("""{"jsonrpc":"2.0","id":$id,"result":true}""")
                        else -> webSocket.send("""{"jsonrpc":"2.0","id":$id,"error":{"code":-32601,"message":"Method not found"}}""")
                    }
                }
            })
        }
        val events = CopyOnWriteArrayList<String>()
        val ws = JanzeerRpcWs.connect(server.url("/").toString(), reconnectDelayMs = 50, onEvent = { events.add(it::class.simpleName!!) })
        assertTrue(ws.url.endsWith("/rpc/ws"))
        assertEquals(10L, ws.getTip().height)
        val blocks = CopyOnWriteArrayList<Long>()
        val sub = ws.subscribeNewBlocks(NewBlocksParams(fromHeight = 1)) { blocks.add(it.height) }
        val activity = CopyOnWriteArrayList<String>()
        ws.subscribeAddressActivity(listOf("0xABC")) { activity.add("${it.transaction.hash}:${it.transaction.amount?.toPlainString()}") }
        Thread.sleep(200)
        assertEquals(listOf(11L), blocks); assertEquals(listOf("tx1:1.25"), activity)
        val firstId = sub.id
        for (s in sockets) s.close(1012, "server restart")
        val until = System.currentTimeMillis() + 5000
        while (!(ws.connected && events.contains("Resubscribed") && blocks.size >= 2) && System.currentTimeMillis() < until) Thread.sleep(50)
        assertTrue(ws.connected, events.toString())
        assertTrue(events.contains("Resubscribed"), events.toString())
        assertNotEquals(firstId, sub.id)
        assertTrue(blocks.size >= 2, "blocks=$blocks activity=$activity events=$events sub=${sub.id} sockets=${sockets.size}")
        assertTrue(sub.unsubscribe())
        ws.close()
        assertFalse(ws.connected)
        Thread.sleep(200)
    }
}
