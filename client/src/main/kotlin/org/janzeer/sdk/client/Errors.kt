package org.janzeer.sdk.client

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Which transport produced an error. */
public enum class Transport { REST, RPC, WS }

/** Base class of every SDK error; [message] is the node's text when it came from the node. */
public open class JanzeerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** The node could not be reached, the response was not JSON, or the socket died. */
public class NetworkException(message: String, cause: Throwable? = null) : JanzeerException(message, cause)

/** The node refused a transaction at intake (validation / mempool rule). Same class on every transport. */
public open class TxRejectedException(
    message: String,
    /** the type string exactly as received (null when the node sent none) */
    public val rawType: String?,
    public val transport: Transport,
    /** HTTP status (REST) */
    public val httpStatus: Int? = null,
    /** JSON-RPC error code (`-32001`) */
    public val rpcCode: Int? = null,
) : JanzeerException(message) {
    /** the node's `ExceptionType` (`INCORRECT_SIGNATURE`, `INVALID_NONCE`, …) or `UNKNOWN` */
    public val type: String get() = rawType ?: "UNKNOWN"
}

/** The transaction's nonce is not the sender's next nonce: refresh it and rebuild. */
public class NonceMismatchException(
    message: String,
    /** the sender (when the node's message carried it) */
    public val address: String?,
    /** the nonce the node expected */
    public val expected: Long?,
    /** the nonce that was sent */
    public val got: Long?,
    transport: Transport,
    httpStatus: Int? = null,
    rpcCode: Int? = null,
) : TxRejectedException(message, "INVALID_NONCE", transport, httpStatus, rpcCode) {
    public companion object {
        private val RE = Regex("Invalid nonce for (0x[0-9a-fA-F]{40}): expected (\\d+), got (\\d+)")
        /** Build from a node message; `null` when the message is not a nonce mismatch. */
        @JvmStatic @JvmOverloads public fun fromMessage(message: String, transport: Transport, httpStatus: Int? = null, rpcCode: Int? = null): NonceMismatchException? =
            RE.find(message)?.let { NonceMismatchException(message, it.groupValues[1].lowercase(), it.groupValues[2].toLong(), it.groupValues[3].toLong(), transport, httpStatus, rpcCode) }
    }
}

/** Any other REST error (`{status, message, type?}` payload). */
public open class ApiException(public val status: Int, message: String, public val type: String? = null, public val body: Any? = null) : JanzeerException(message)

/** Request-body validation failed (400 with a list of `{message}`). */
public class ValidationException(public val messages: List<String>, body: Any?) : ApiException(400, messages.takeIf { it.isNotEmpty() }?.joinToString("; ") ?: "validation failed", null, body)

/** 404 */
public class NotFoundException(status: Int, message: String, type: String? = null, body: Any? = null) : ApiException(status, message, type, body)

/** The node is still syncing (REST 400 "Blockchain is synchronizing" / RPC `-32002`); retry shortly. */
public class NotSynchronizedException(message: String, public val transport: Transport) : JanzeerException(message)

/** HTTP 429 / RPC `-32004`; [retryAfterMs] from the `Retry-After` header (default 1000). */
public class RateLimitedException(message: String, public val retryAfterMs: Long, public val transport: Transport) : JanzeerException(message)

/** JSON-RPC error that is not a transaction rejection. */
public open class RpcException(public val code: Int, message: String, public val data: Any? = null) : JanzeerException(message)
/** `-32602` — the SDK sent something the node could not bind (`data` lists field messages). */
public class RpcInvalidParamsException(code: Int, message: String, data: Any? = null) : RpcException(code, message, data)
/** `-32000` — block / transaction / token / validator does not exist. */
public class RpcNotFoundException(code: Int, message: String, data: Any? = null) : RpcException(code, message, data)
/** `-32003` — a range or timeout exceeds the server cap. */
public class RpcLimitException(code: Int, message: String, data: Any? = null) : RpcException(code, message, data)
/** `-32601` */
public class RpcMethodNotFoundException(code: Int, message: String, data: Any? = null) : RpcException(code, message, data)

/** JSON-RPC error codes. */
public object RpcCodes {
    public const val PARSE_ERROR: Int = -32700
    public const val INVALID_REQUEST: Int = -32600
    public const val METHOD_NOT_FOUND: Int = -32601
    public const val INVALID_PARAMS: Int = -32602
    public const val INTERNAL: Int = -32603
    public const val NOT_FOUND: Int = -32000
    /** transaction rejected (`data.type` names the rule) */
    public const val REJECTED: Int = -32001
    public const val NOT_SYNCHRONIZED: Int = -32002
    public const val LIMIT: Int = -32003
    public const val RATE_LIMITED: Int = -32004
}

/** Mapping of node errors to the hierarchy. */
public object Errors {
    /** Map a JSON-RPC `error` object to the SDK error hierarchy. */
    @JvmStatic @JvmOverloads public fun fromRpc(err: JsonObject, transport: Transport = Transport.RPC): JanzeerException {
        val code = JsonUtil.optInt(err["code"]) ?: RpcCodes.INTERNAL
        val message = JsonUtil.optStr(err["message"]) ?: "RPC error $code"
        val data = JsonUtil.toPlain(err["data"])
        return when (code) {
            RpcCodes.REJECTED -> {
                val type = (err["data"] as? JsonObject)?.let { JsonUtil.optStr(it["type"]) }
                NonceMismatchException.fromMessage(message, transport, rpcCode = code)
                    ?: if (type == "INVALID_NONCE") NonceMismatchException(message, null, null, null, transport, rpcCode = code) else TxRejectedException(message, type, transport, rpcCode = code)
            }
            RpcCodes.NOT_SYNCHRONIZED -> NotSynchronizedException(message, transport)
            RpcCodes.RATE_LIMITED -> RateLimitedException(message, 1000, transport)
            RpcCodes.INVALID_PARAMS -> RpcInvalidParamsException(code, message, data)
            RpcCodes.NOT_FOUND -> RpcNotFoundException(code, message, data)
            RpcCodes.LIMIT -> RpcLimitException(code, message, data)
            RpcCodes.METHOD_NOT_FOUND -> RpcMethodNotFoundException(code, message, data)
            else -> RpcException(code, message, data)
        }
    }

    /**
     * Map a non-2xx REST response to the SDK error hierarchy. Rule: if the body has `payload` use it (the envelope
     * wraps errors too); otherwise the body itself (the rate limiter writes flat `{status, message}` before the
     * envelope layer).
     */
    @JvmStatic @JvmOverloads public fun fromRest(status: Int, body: JsonElement?, retryAfterHeader: String? = null): JanzeerException {
        val p: JsonElement? = (body as? JsonObject)?.let { if (it.containsKey("payload")) it["payload"] else null } ?: body
        val po = p as? JsonObject
        val message = po?.let { JsonUtil.optStr(it["message"]) }?.takeIf { it.isNotEmpty() }
        if (status == 429) return RateLimitedException(message ?: "Rate limit exceeded", (retryAfterHeader?.toLongOrNull() ?: 1L) * 1000, Transport.REST)
        if (p is JsonArray) return ValidationException(p.map { (it as? JsonObject)?.let { o -> JsonUtil.optStr(o["message"]) } ?: it.toString() }, JsonUtil.toPlain(p))
        val msg = message ?: "Request failed ($status)"
        val type = po?.let { JsonUtil.optStr(it["type"]) }
        if (status == 404) return NotFoundException(status, msg, type, JsonUtil.toPlain(p))
        if (status == 400) {
            if (msg == "Blockchain is synchronizing") return NotSynchronizedException(msg, Transport.REST)
            NonceMismatchException.fromMessage(msg, Transport.REST, httpStatus = status)?.let { return it }
            if (type == "INVALID_NONCE") return NonceMismatchException(msg, null, null, null, Transport.REST, httpStatus = status)
            if (type != null) return TxRejectedException(msg, type, Transport.REST, httpStatus = status)
        }
        return ApiException(status, msg, type, JsonUtil.toPlain(p))
    }

    internal fun isTruthy(e: JsonElement?): Boolean = (e as? JsonPrimitive)?.content == "true"
}
