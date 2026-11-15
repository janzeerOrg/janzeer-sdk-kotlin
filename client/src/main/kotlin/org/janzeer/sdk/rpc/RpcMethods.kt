package org.janzeer.sdk.rpc

import kotlinx.serialization.json.JsonElement
import org.janzeer.sdk.TxType
import org.janzeer.sdk.client.JsonUtil
import org.janzeer.sdk.client.Token
import org.janzeer.sdk.client.TokenBalance
import org.janzeer.sdk.tx.SignedTx
import java.math.BigDecimal

/** Anything that can perform a JSON-RPC call. */
public interface RpcTransport {
    /** Raw call: [params] by name (map) or by position (list). Throws the mapped SDK error on a JSON-RPC error. */
    public fun call(method: String, params: Any? = null): JsonElement?
}

/** Every `janzeer_*` method, typed. Blocking; suspend variants in `org.janzeer.sdk.client.coroutines`. */
public abstract class RpcMethods : RpcTransport {
    public fun methods(): List<RpcMethodSpec> = JsonUtil.reqArr(call("janzeer_methods")).map(RpcMethodSpec::from)
    public fun getInfo(): NodeInfoView = NodeInfoView.from(call("janzeer_getInfo"))
    public fun getChainSpec(): ChainSpecView = ChainSpecView.from(call("janzeer_getChainSpec"))
    public fun getGenesis(): JsonElement = call("janzeer_getGenesis")!!
    public fun getStats(): StatsView = StatsView.from(call("janzeer_getStats"))
    public fun getEpoch(): EpochView = EpochView.from(call("janzeer_getEpoch"))

    public fun getAccount(address: String): AccountView = AccountView.from(call("janzeer_getAccount", mapOf("address" to address)))
    /** spendable balance */
    public fun getBalance(address: String): BigDecimal = JsonUtil.dec(call("janzeer_getBalance", mapOf("address" to address)))
    /** next nonce (committed + pending) */
    public fun getNonce(address: String): Long = JsonUtil.long(call("janzeer_getNonce", mapOf("address" to address)))

    public fun getTransactionsByAddress(address: String, page: PageParams = PageParams()): TransactionPageView =
        TransactionPageView.from(call("janzeer_getTransactionsByAddress", mapOf("address" to address) + page.toMap()))

    public fun getTransactionByHash(hash: String): TransactionView =
        TransactionView.from(call("janzeer_getTransactionByHash", mapOf("hash" to hash)))

    public fun getTip(): Long = JsonUtil.long(call("janzeer_getTip"))
    public fun getBlockByNumber(number: Long, full: Boolean = true): JsonElement =
        call("janzeer_getBlockByNumber", mapOf("number" to number, "full" to full))!!
    public fun getBlocks(from: Long, to: Long): List<JsonElement> =
        JsonUtil.reqArr(call("janzeer_getBlocks", mapOf("from" to from, "to" to to)))

    public fun listValidators(): List<ValidatorView> = JsonUtil.reqArr(call("janzeer_listValidators")).map(ValidatorView::from)
    public fun getActiveValidators(): List<String> = JsonUtil.reqArr(call("janzeer_getActiveValidators")).map { it.toString() }
    public fun getValidator(publicKey: String): ValidatorView = ValidatorView.from(call("janzeer_getValidator", mapOf("publicKey" to publicKey)))

    public fun listTokens(): List<Token> = JsonUtil.reqArr(call("janzeer_listTokens")).map(Token::from)
    public fun getToken(id: String): Token = Token.from(call("janzeer_getToken", mapOf("id" to id)))
    public fun getTokenHolders(id: String): List<TokenBalance> = JsonUtil.reqArr(call("janzeer_getTokenHolders", mapOf("id" to id))).map(TokenBalance::from)

    public fun estimateFee(type: String): BigDecimal = JsonUtil.dec(call("janzeer_estimateFee", mapOf("type" to type)))
    public fun send(tx: SignedTx): String = JsonUtil.str(call("janzeer_send", mapOf("tx" to tx.toHex())))
}
