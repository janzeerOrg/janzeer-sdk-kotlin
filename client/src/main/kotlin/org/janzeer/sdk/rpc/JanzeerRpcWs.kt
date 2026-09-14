package org.janzeer.sdk.rpc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.janzeer.sdk.Limits
import org.janzeer.sdk.client.Errors
import org.janzeer.sdk.client.JanzeerException
import org.janzeer.sdk.client.JsonUtil
import org.janzeer.sdk.client.NetworkException
import org.janzeer.sdk.client.Transport
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/** Diagnostics events of the WebSocket client. */
public sealed class WsEvent {
    public object Open : WsEvent()
    public data class Closed(val code: Int?, val reason: String?, val willReconnect: Boolean) : WsEvent()
    public data class Error(val error: Throwable) : WsEvent()
    public data class Resubscribed(val count: Int) : WsEvent()
}

/** `newBlocks` parameters: replay committed blocks from [fromHeight], then stream live ones (exactly-once by height). */
public data class NewBlocksParams @JvmOverloads constructor(val fromHeight: Long? = null, val includeTransactions: Boolean = false) {
    internal fun toMap(): Map<String, Any?> = buildMap { if (fromHeight != null) put("fromHeight", fromHeight); put("includeTransactions", includeTransactions) }
}

/** An `addressActivity` notification. */
public data class AddressActivityEvent(val blockHeight: Long, val blockHash: String, val transaction: TxView)

/** A live subscription. [id] changes after a reconnect; the handler stays. */
public class Subscription internal constructor(private val live: JanzeerRpcWs.LiveSub, private val ws: JanzeerRpcWs) {
    public val id: String get() = live.id
    public val kind: String get() = live.kind
    /** Cancel. */
    public fun unsubscribe(): Boolean = ws.unsubscribe(live)
}

/**
 * JSON-RPC 2.0 over the node's WebSocket (`/rpc/ws`): the same methods as HTTP plus live subscriptions
 * (`newBlocks`, `addressActivity`). Notifications are delivered on OkHttp's reader thread — keep handlers fast.
 * With [reconnect] a dropped socket reconnects with exponential backoff (from [reconnectDelayMs] up to 30 s) and
 * every live subscription is re-issued.
 */
public class JanzeerRpcWs private constructor(
    public val url: String,
    private val http: OkHttpClient,
    private val reconnect: Boolean,
    private val reconnectDelayMs: Long,
    private val timeoutMs: Long,
    private val onEvent: ((WsEvent) -> Unit)?,
    private val ownsHttp: Boolean,
) : RpcMethods(), Closeable {
    internal class LiveSub(@Volatile var id: String, val kind: String, val params: Any, val handler: (JsonElement?) -> Unit)

    private val ids = AtomicLong()
    private val pending = ConcurrentHashMap<Long, CompletableFuture<JsonElement?>>()
    private val subs = ConcurrentHashMap<String, LiveSub>()
    /** subscribe calls in flight: the id is registered on the reader thread BEFORE the call completes, so a notification that follows the response immediately is never dropped */
    private val pendingSubs = ConcurrentHashMap<Long, LiveSub>()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var closedByUser = false
    @Volatile private var backoff = reconnectDelayMs
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "janzeer-ws").apply { isDaemon = true } }

    /** true while the socket is open */
    public val connected: Boolean get() = socket != null

    override fun call(method: String, params: Any?): JsonElement? = callInternal(method, params, null)

    private fun callInternal(method: String, params: Any?, sub: LiveSub?): JsonElement? {
        val ws = socket ?: throw NetworkException(if (closedByUser) "socket closed by client" else "socket not connected (reconnecting)")
        val id = ids.incrementAndGet()
        val f = CompletableFuture<JsonElement?>()
        pending[id] = f
        if (sub != null) pendingSubs[id] = sub
        if (!ws.send(JsonUtil.stringify(buildMap { put("jsonrpc", "2.0"); put("id", id); put("method", method); if (params != null) put("params", params) }))) {
            pending.remove(id); pendingSubs.remove(id); throw NetworkException("send failed")
        }
        return try { f.get(timeoutMs, TimeUnit.MILLISECONDS) } catch (e: TimeoutException) { pending.remove(id); pendingSubs.remove(id); throw NetworkException("RPC $method timed out after $timeoutMs ms") } catch (e: java.util.concurrent.ExecutionException) { throw e.cause as? JanzeerException ?: NetworkException(e.cause?.message ?: "call failed", e.cause) }
    }

    /** Stream committed blocks (optionally replaying from `fromHeight`). */
    public fun subscribeNewBlocks(params: NewBlocksParams, handler: (BlockView) -> Unit): Subscription = subscribe("newBlocks", params.toMap()) { handler(BlockView.from(it)) }

    /** Notify when a final transaction involves any of [addresses] (sender, recipient or token recipient). */
    public fun subscribeAddressActivity(addresses: List<String>, handler: (AddressActivityEvent) -> Unit): Subscription {
        require(addresses.isNotEmpty() && addresses.size <= Limits.WATCHED_ADDRESSES) { "1–${Limits.WATCHED_ADDRESSES} addresses" }
        return subscribe("addressActivity", mapOf("addresses" to addresses.map { it.lowercase() })) { p ->
            val o = JsonUtil.reqObj(p, "notification")
            handler(AddressActivityEvent(JsonUtil.long(o["blockHeight"]), JsonUtil.str(o["blockHash"]), TxView.from(o["transaction"])))
        }
    }

    /** Close the socket; no reconnect. Pending calls fail, subscriptions are dropped. */
    override fun close() {
        closedByUser = true
        subs.clear()
        socket?.close(1000, "client closed")
        socket = null
        failPending(NetworkException("socket closed by client"))
        scheduler.shutdownNow()
        if (ownsHttp) { http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll() }
    }

    private fun subscribe(kind: String, params: Any, handler: (JsonElement?) -> Unit): Subscription {
        check(subs.size < Limits.SUBSCRIPTIONS_PER_SESSION) { "at most ${Limits.SUBSCRIPTIONS_PER_SESSION} subscriptions per session" }
        val live = LiveSub("", kind, params, handler)
        callInternal("janzeer_subscribe", mapOf("kind" to kind, "params" to params), live)
        return Subscription(live, this)
    }

    internal fun unsubscribe(live: LiveSub): Boolean {
        subs.remove(live.id)
        if (!connected) return true
        return try { JsonUtil.bool(call("janzeer_unsubscribe", mapOf("subscription" to live.id))) } catch (e: JanzeerException) { false }
    }

    private fun open(): CompletableFuture<Unit> {
        val ready = CompletableFuture<Unit>()
        http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket; backoff = reconnectDelayMs
                onEvent?.invoke(WsEvent.Open)
                ready.complete(Unit)
                scheduler.execute { resubscribe() }
            }
            override fun onMessage(webSocket: WebSocket, text: String) { onText(text) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { dropped(webSocket, code, reason, null) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onEvent?.invoke(WsEvent.Error(t))
                if (!ready.isDone) ready.completeExceptionally(NetworkException("WebSocket connect to $url failed: ${t.message}", t))
                dropped(webSocket, null, t.message, t)
            }
        })
        return ready
    }

    private fun dropped(ws: WebSocket, code: Int?, reason: String?, t: Throwable?) {
        if (socket !== ws && socket != null) return
        socket = null
        val willReconnect = reconnect && !closedByUser
        onEvent?.invoke(WsEvent.Closed(code, reason, willReconnect))
        failPending(NetworkException("socket closed (${code ?: t?.message ?: "?"})"))
        if (willReconnect) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        val delay = backoff
        backoff = (backoff * 2).coerceAtMost(30_000)
        if (scheduler.isShutdown) return
        scheduler.schedule({ if (!closedByUser && !connected) open().exceptionally { null } }, delay, TimeUnit.MILLISECONDS)
    }

    private fun resubscribe() {
        val old = subs.values.toList()
        if (old.isEmpty()) return
        subs.clear()
        var count = 0
        for (s in old) {
            try {
                callInternal("janzeer_subscribe", mapOf("kind" to s.kind, "params" to s.params), s)
                count++
            } catch (e: Exception) { onEvent?.invoke(WsEvent.Error(e)) }
        }
        if (count > 0) onEvent?.invoke(WsEvent.Resubscribed(count))
    }

    private fun onText(text: String) {
        val msg = try { JsonUtil.parse(text) } catch (e: Exception) { return }
        val items = if (msg is JsonArray) msg.toList() else listOf(msg)
        for (m in items) {
            val o = m as? JsonObject ?: continue
            if (JsonUtil.optStr(o["method"]) == "janzeer_subscription") {
                val p = o["params"] as? JsonObject ?: continue
                val sub = subs[JsonUtil.optStr(p["subscription"])] ?: continue
                if (p.containsKey("result")) try { sub.handler(p["result"]) } catch (e: Exception) { onEvent?.invoke(WsEvent.Error(e)) }
                continue
            }
            val id = JsonUtil.optLong(o["id"]) ?: continue
            val f = pending.remove(id) ?: continue
            val sub = pendingSubs.remove(id)
            val err = o["error"] as? JsonObject
            if (err == null && sub != null) { sub.id = JsonUtil.str(o["result"]); subs[sub.id] = sub } // register before completing
            if (err != null) f.completeExceptionally(Errors.fromRpc(err, Transport.WS)) else f.complete(o["result"])
        }
    }

    private fun failPending(err: JanzeerException) {
        for ((_, f) in pending) f.completeExceptionally(err)
        pending.clear(); pendingSubs.clear()
    }

    public companion object {
        /** Open a socket; returns once connected. [url] may be `http(s)://…` (converted) with or without `/rpc/ws`. */
        @JvmStatic @JvmOverloads public fun connect(
            url: String,
            httpClient: OkHttpClient? = null,
            reconnect: Boolean = true,
            reconnectDelayMs: Long = 1000,
            timeoutMs: Long = 65_000,
            connectTimeoutMs: Long = 10_000,
            onEvent: ((WsEvent) -> Unit)? = null,
        ): JanzeerRpcWs {
            val http = httpClient ?: OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build()
            val ws = JanzeerRpcWs(normalizeWsUrl(url), http, reconnect, reconnectDelayMs, timeoutMs, onEvent, httpClient == null)
            try { ws.open().get(connectTimeoutMs, TimeUnit.MILLISECONDS) } catch (e: TimeoutException) { ws.close(); throw NetworkException("WebSocket connect to ${ws.url} timed out") } catch (e: java.util.concurrent.ExecutionException) { ws.close(); throw e.cause as? JanzeerException ?: NetworkException(e.cause?.message ?: "connect failed", e.cause) }
            return ws
        }

        /** Rewrite an origin / `/api/v1` / `/rpc` url (http or ws scheme) to the `/rpc/ws` endpoint. */
        @JvmStatic public fun normalizeWsUrl(url: String): String {
            var u = url.trim().trimEnd('/').replaceFirst(Regex("^http:"), "ws:").replaceFirst(Regex("^https:"), "wss:").removeSuffix("/api/v1").removeSuffix("/rpc")
            if (!u.endsWith("/rpc/ws")) u += "/rpc/ws"
            return u
        }
    }
}
