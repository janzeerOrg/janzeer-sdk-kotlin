package org.janzeer.sdk.rpc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.janzeer.sdk.client.Errors
import org.janzeer.sdk.client.JanzeerException
import org.janzeer.sdk.client.JsonUtil
import org.janzeer.sdk.client.NetworkException
import org.janzeer.sdk.client.RateLimitedException
import org.janzeer.sdk.client.RpcCodes
import org.janzeer.sdk.client.RpcException
import org.janzeer.sdk.client.Transport
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** One batch request. */
public data class BatchRequest @JvmOverloads constructor(val method: String, val params: Any? = null)

/** One batch outcome: [result] on success, [error] on failure. */
public data class BatchResult(val result: JsonElement?, val error: JanzeerException?) { public val ok: Boolean get() = error == null }

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/**
 * JSON-RPC 2.0 over HTTP (`POST /rpc`). [url] may be a bare origin or an `/api/v1` base — it is rewritten to `/rpc`.
 * The default client has a 65 s call timeout (above the node's 60 s `waitForFinality` cap).
 */
public class JanzeerRpc @JvmOverloads constructor(url: String, httpClient: OkHttpClient? = null, private val headers: Map<String, String> = emptyMap(), private val retryOnRateLimit: Boolean = true) : RpcMethods(), Closeable {
    public val url: String = normalizeRpcUrl(url)
    private val http: OkHttpClient = httpClient ?: OkHttpClient.Builder().callTimeout(65, TimeUnit.SECONDS).readTimeout(65, TimeUnit.SECONDS).build()
    private val ids = AtomicLong()

    override fun close() { http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll() }

    override fun call(method: String, params: Any?): JsonElement? {
        val id = ids.incrementAndGet()
        val body = post(buildMap { put("jsonrpc", "2.0"); put("id", id); put("method", method); if (params != null) put("params", params) })
        val o = body as? JsonObject ?: throw NetworkException("malformed JSON-RPC response")
        (o["error"] as? JsonObject)?.let { throw Errors.fromRpc(it) }
        return o["result"]
    }

    /** Send several requests in one HTTP round trip (node cap: 50). Results come back in request order. */
    public fun batch(requests: List<BatchRequest>): List<BatchResult> {
        if (requests.isEmpty()) return emptyList()
        val reqIds = requests.map { ids.incrementAndGet() }
        val body = post(requests.mapIndexed { i, r -> buildMap { put("jsonrpc", "2.0"); put("id", reqIds[i]); put("method", r.method); if (r.params != null) put("params", r.params) } })
        if (body is JsonObject) throw (body["error"] as? JsonObject)?.let(Errors::fromRpc) ?: NetworkException("malformed JSON-RPC batch response")
        val byId = (body as? JsonArray)?.filterIsInstance<JsonObject>()?.associateBy { JsonUtil.optLong(it["id"]) } ?: emptyMap()
        return reqIds.map { id ->
            val r = byId[id] ?: return@map BatchResult(null, RpcException(RpcCodes.INTERNAL, "missing batch response"))
            (r["error"] as? JsonObject)?.let { BatchResult(null, Errors.fromRpc(it)) } ?: BatchResult(r["result"], null)
        }
    }

    private fun post(payload: Any, attempt: Int = 0): JsonElement? {
        val req = Request.Builder().url(url).post(JsonUtil.stringify(payload).toRequestBody(JSON_MEDIA)).header("Accept", "application/json").apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        val res = try { http.newCall(req).execute() } catch (e: IOException) { throw NetworkException("Cannot reach the node at $url: ${e.message}", e) }
        res.use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 204 || text.isEmpty()) return null
            val json = try { JsonUtil.parse(text) } catch (e: Exception) { throw NetworkException("Node returned non-JSON (${r.code})", e) }
            if (r.code == 429) {
                val err = RateLimitedException("Rate limit exceeded", (r.header("Retry-After")?.toLongOrNull() ?: 1L) * 1000, Transport.RPC)
                if (retryOnRateLimit && attempt == 0) { Thread.sleep(err.retryAfterMs); return post(payload, 1) }
                throw err
            }
            return json
        }
    }

    public companion object {
        /** Rewrite an origin / `/api/v1` base / `/rpc` url to the `/rpc` endpoint. */
        @JvmStatic public fun normalizeRpcUrl(url: String): String {
            var u = url.trim().trimEnd('/').removeSuffix("/api/v1")
            if (!u.endsWith("/rpc")) u += "/rpc"
            return u
        }
    }
}
