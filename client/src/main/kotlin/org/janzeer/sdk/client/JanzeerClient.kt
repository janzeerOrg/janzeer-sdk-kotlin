package org.janzeer.sdk.client

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.janzeer.sdk.tx.ExitValidatorFields
import org.janzeer.sdk.tx.RegisterValidatorFields
import org.janzeer.sdk.tx.SignedTx
import org.janzeer.sdk.tx.TokenFields
import org.janzeer.sdk.tx.TransferFields
import java.io.Closeable
import java.io.IOException
import java.math.BigDecimal
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** The REST envelope metadata of the last successful call. */
public data class Envelope(val timestamp: Long, val version: String)

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/**
 * Typed client of the node's REST API (`/api/v1/`). Blocking (call it off the main thread on Android); suspend
 * variants are in `org.janzeer.sdk.client.coroutines`.
 *
 * @param baseUrl e.g. `http://localhost:7019/api/v1/` (a missing `/api/v1/` suffix is added)
 * @param httpClient share one [OkHttpClient] per app; a default with a 15 s call timeout is created otherwise
 * @param retryOnRateLimit retry once after a 429 honouring `Retry-After` (safe: acceptance is idempotent by hash)
 */
public class JanzeerClient @JvmOverloads constructor(
    baseUrl: String,
    httpClient: OkHttpClient? = null,
    private val headers: Map<String, String> = emptyMap(),
    private val retryOnRateLimit: Boolean = true,
) : Closeable {
    /** normalized base, ends with `/api/v1/` */
    public val baseUrl: String = baseUrl.trim().trimEnd('/').let { if (it.endsWith("/api/v1")) it else "$it/api/v1" } + "/"
    /** envelope of the last successful response */
    @Volatile public var lastEnvelope: Envelope? = null
        private set
    private val http: OkHttpClient = httpClient ?: OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    override fun close() { http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll() }

    // ---- low level ----------------------------------------------------------------------------------------

    /** `GET path?query` → the envelope `payload` (lossless tree), or [notFound] on a 404 when given. */
    @JvmOverloads public fun get(path: String, query: Map<String, Any?>? = null, notFound: JsonElement? = null, notFoundEnabled: Boolean = false): JsonElement? =
        request("GET", path + qs(query), null, notFoundEnabled, notFound)

    /** `GET` that returns [notFound] (may be null) on a 404. */
    public fun getOrNotFound(path: String, query: Map<String, Any?>?, notFound: JsonElement?): JsonElement? = request("GET", path + qs(query), null, true, notFound)

    /** `POST path` with a JSON body → the envelope `payload`. */
    public fun post(path: String, body: Any?): JsonElement? = request("POST", path, JsonUtil.stringify(body), false, null)

    private fun request(method: String, path: String, body: String?, notFoundEnabled: Boolean, notFound: JsonElement?, attempt: Int = 0): JsonElement? {
        val req = Request.Builder().url(baseUrl + path.trimStart('/')).apply {
            header("Accept", "application/json")
            headers.forEach { (k, v) -> header(k, v) }
            if (body == null) method(method, null) else method(method, body.toRequestBody(JSON_MEDIA))
        }.build()
        val res = try { http.newCall(req).execute() } catch (e: IOException) { throw NetworkException("Cannot reach the node at $baseUrl: ${e.message}", e) }
        res.use { r ->
            if (r.code == 404 && notFoundEnabled) return notFound
            val text = r.body?.string().orEmpty()
            val json: JsonElement? = if (text.isEmpty()) null else try { JsonUtil.parse(text) } catch (e: Exception) { if (r.isSuccessful) throw NetworkException("Node returned non-JSON (${r.code})", e) else null }
            if (!r.isSuccessful) {
                val err = Errors.fromRest(r.code, json, r.header("Retry-After"))
                if (err is RateLimitedException && retryOnRateLimit && attempt == 0) {
                    Thread.sleep(err.retryAfterMs)
                    return request(method, path, body, notFoundEnabled, notFound, 1)
                }
                throw err
            }
            if (json is JsonObject && json.containsKey("payload")) {
                lastEnvelope = Envelope(JsonUtil.optLong(json["timestamp"]) ?: 0, JsonUtil.optStr(json["version"]) ?: "")
                return json["payload"]
            }
            return json
        }
    }

    // ---- node ---------------------------------------------------------------------------------------------

    public fun info(): NodeInfo = NodeInfo.from(get("info"))
    public fun version(): NodeVersion = NodeVersion.from(get("info/version"))
    /** ms since the node started */
    public fun uptime(): Long = JsonUtil.long(get("info/uptime"))
    public fun explorerInfo(): ExplorerInfo = ExplorerInfo.from(get("explorer/info"))

    // ---- wallet -------------------------------------------------------------------------------------------

    /** Spendable balance (committed − pending debits); `0` for an unknown address. */
    public fun balance(address: String): BigDecimal = JsonUtil.dec(getOrNotFound("wallets/$address", null, JsonPrimitive(0)))
    /** The next nonce to sign with (committed nonce + pending count); `0` for an unknown address. */
    public fun nonce(address: String): Long = JsonUtil.long(getOrNotFound("wallets/$address/nonce", null, JsonPrimitive(0)))
    /** Server-side address validation (shape + EIP-55). */
    public fun validateAddress(address: String): Boolean = try { post("wallets/validateAddress", mapOf("address" to address)); true } catch (e: NetworkException) { throw e } catch (e: JanzeerException) { false }

    // ---- transactions -------------------------------------------------------------------------------------

    /** Submit a signed transaction (HTTP 201). Throws [TxRejectedException] / [NonceMismatchException] on refusal. Returns the node's echo. */
    public fun submit(tx: SignedTx): BaseTx {
        val v = post(tx.restPath, tx.toRestBody())
        return when (tx.fields) {
            is TransferFields -> TransferTx.from(v)
            is TokenFields -> TokenTx.from(v)
            is RegisterValidatorFields -> RegisterValidatorTx.from(v)
            is ExitValidatorFields -> ExitValidatorTx.from(v)
        }
    }

    /** transfers (`address` = sender or recipient; `unconfirmed` = the mempool) */
    public val transfers: TxApi<TransferTx> = TxApi(this, "transactions/transfers", TransferTx::from)
    /** validator registrations */
    public val validatorTxs: TxApi<RegisterValidatorTx> = TxApi(this, "transactions/validators", RegisterValidatorTx::from)
    /** validator exits */
    public val exitValidatorTxs: TxApi<ExitValidatorTx> = TxApi(this, "transactions/exit-validators", ExitValidatorTx::from)
    /** token transactions */
    public val tokenTxs: TxApi<TokenTx> = TxApi(this, "transactions/tokens", TokenTx::from)
    /** block rewards (`address` = recipient) */
    public val rewards: TxApi<RewardTx> = TxApi(this, "transactions/rewards", RewardTx::from)

    /** Execution receipt of a committed transaction (`null` if unknown or still pending). */
    public fun receipt(hash: String): Receipt? = getOrNotFound("transactions/$hash/receipt", null, null)?.let(Receipt::from)

    // ---- blocks / validators / tokens ----------------------------------------------------------------------

    public val blocks: BlocksApi = BlocksApi(this)
    public val validators: ValidatorsApi = ValidatorsApi(this)
    public val tokens: TokensApi = TokensApi(this)

    /** `client.transfers` / `validatorTxs` / `exitValidatorTxs` / `tokenTxs` / `rewards`. */
    public class TxApi<T : BaseTx> internal constructor(private val c: JanzeerClient, private val path: String, private val map: (JsonElement?) -> T) {
        /** List (paged). */
        @JvmOverloads public fun list(q: TxListQuery = TxListQuery()): Page<T> = Page.from(c.getOrNotFound(path, q.toQuery(), null), map)
        /** One by hash, or `null`. */
        public fun get(hash: String): T? = c.getOrNotFound("$path/$hash", null, null)?.let(map)
    }

    /** `client.blocks` */
    public class BlocksApi internal constructor(c: JanzeerClient) {
        public val main: BlockApi<MainBlock> = BlockApi(c, "blocks/main", MainBlock::from)
        public val genesis: BlockApi<GenesisBlock> = BlockApi(c, "blocks/genesis", GenesisBlock::from)
    }

    /** `client.blocks.main` / `client.blocks.genesis`. */
    public class BlockApi<T : BaseBlock> internal constructor(private val c: JanzeerClient, private val path: String, private val map: (JsonElement?) -> T) {
        @JvmOverloads public fun list(q: PageQuery = PageQuery()): Page<T> = Page.from(c.getOrNotFound(path, q.toQuery(), null), map)
        public fun get(hash: String): T? = c.getOrNotFound("$path/$hash", null, null)?.let(map)
        public fun previous(hash: String): T? = c.getOrNotFound("$path/$hash/previous", null, null)?.let(map)
        public fun next(hash: String): T? = c.getOrNotFound("$path/$hash/next", null, null)?.let(map)
    }

    /** `client.validators` */
    public class ValidatorsApi internal constructor(private val c: JanzeerClient) {
        /** every registered validator, registration order */
        @JvmOverloads public fun list(q: PageQuery = PageQuery()): Page<Validator> = Page.from(c.getOrNotFound("validators", q.toQuery(), null), Validator::from)
        /** the current epoch's producer set */
        @JvmOverloads public fun active(q: PageQuery = PageQuery()): Page<Validator> = Page.from(c.getOrNotFound("validators/active", q.toQuery(), null), Validator::from)
        @JvmOverloads public fun view(q: PageQuery = PageQuery()): Page<ValidatorView> = Page.from(c.getOrNotFound("validators/view", q.toQuery(), null), ValidatorView::from)
    }

    /** `client.tokens` */
    public class TokensApi internal constructor(private val c: JanzeerClient) {
        @JvmOverloads public fun list(q: PageQuery = PageQuery()): Page<Token> = Page.from(c.getOrNotFound("tokens", q.toQuery(), null), Token::from)
        public fun get(tokenId: String): Token? = c.getOrNotFound("tokens/$tokenId", null, null)?.let(Token::from)
        @JvmOverloads public fun holders(tokenId: String, q: PageQuery = PageQuery()): Page<TokenBalance> = Page.from(c.getOrNotFound("tokens/$tokenId/holders", q.toQuery(), null), TokenBalance::from)
        @JvmOverloads public fun balancesOf(address: String, q: PageQuery = PageQuery()): Page<TokenBalance> = Page.from(c.getOrNotFound("tokens/balances/$address", q.toQuery(), null), TokenBalance::from)
    }

    private fun qs(params: Map<String, Any?>?): String {
        if (params == null) return ""
        val parts = params.entries.filter { it.value != null && it.value != "" }.map { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value.toString(), "UTF-8")}" }
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }
}
