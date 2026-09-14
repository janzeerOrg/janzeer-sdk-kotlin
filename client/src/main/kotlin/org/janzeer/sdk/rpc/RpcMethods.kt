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
    public fun getStats(): StatsView = StatsView.from(call("janzeer_getStats"))
    public fun getEpoch(): EpochView = EpochView.from(call("janzeer_getEpoch"))

    public fun getAccount(address: String): AccountView = AccountView.from(call("janzeer_getAccount", mapOf("address" to address)))
    /** spendable balance */
    public fun getBalance(address: String): BigDecimal = JsonUtil.dec(call("janzeer_getBalance", mapOf("address" to address)))
    /** next nonce (committed + pending) */
    public fun getNonce(address: String): Long = JsonUtil.long(call("janzeer_getNonce", mapOf("address" to address)))
    @JvmOverloads public fun getTokenBalances(address: String, p: PageParams = PageParams()): RpcPage<TokenBalance> = RpcPage.from(call("janzeer_getTokenBalances", p.params(mapOf("address" to address))), TokenBalance::from)
    @JvmOverloads public fun getTransactionsByAddress(address: String, p: PageParams = PageParams()): AddressTransactions = AddressTransactions.from(call("janzeer_getTransactionsByAddress", p.params(mapOf("address" to address))))

    /** Submit a signed transaction through its type's `janzeer_send*` method. */
    public fun send(tx: SignedTx): SendResult = SendResult.from(call(tx.rpcMethod, tx.toRpcParams()))
    public fun sendTransfer(body: Map<String, Any?>): SendResult = SendResult.from(call("janzeer_sendTransfer", body))
    public fun sendToken(body: Map<String, Any?>): SendResult = SendResult.from(call("janzeer_sendToken", body))
    public fun sendRegisterValidator(body: Map<String, Any?>): SendResult = SendResult.from(call("janzeer_sendRegisterValidator", body))
    public fun sendExitValidator(body: Map<String, Any?>): SendResult = SendResult.from(call("janzeer_sendExitValidator", body))
    public fun sendRawTransaction(type: TxType, tx: Map<String, Any?>): SendResult = SendResult.from(call("janzeer_sendRawTransaction", mapOf("type" to type.wireName, "tx" to tx)))

    /** Unified view of any transaction; status UNKNOWN when the node has never seen the hash. */
    public fun getTransactionByHash(hash: String): TxView = TxView.from(call("janzeer_getTransactionByHash", mapOf("hash" to hash)))
    /** Server-side wait (≤ 60 000 ms per call). Prefer [org.janzeer.sdk.client.Helpers.waitForFinality]. */
    @JvmOverloads public fun waitForFinality(hash: String, timeoutMs: Long = 30_000): TxView = TxView.from(call("janzeer_waitForFinality", mapOf("hash" to hash, "timeoutMs" to timeoutMs)))
    /** kind: `transfer` | `token` | `tokenCreate` | `registerValidator` | `exitValidator` */
    public fun estimateFee(kind: String): FeeEstimate = FeeEstimate.from(call("janzeer_estimateFee", mapOf("kind" to kind)))

    public fun getTip(): BlockView = BlockView.from(call("janzeer_getTip"))
    @JvmOverloads public fun getBlockByNumber(height: Long, includeTransactions: Boolean = false): BlockView = BlockView.from(call("janzeer_getBlockByNumber", mapOf("height" to height, "includeTransactions" to includeTransactions)))
    @JvmOverloads public fun getBlockByHash(hash: String, includeTransactions: Boolean = false): BlockView = BlockView.from(call("janzeer_getBlockByHash", mapOf("hash" to hash, "includeTransactions" to includeTransactions)))
    /** ascending, at most 100 blocks per call */
    @JvmOverloads public fun getBlocks(fromHeight: Long, toHeight: Long, includeTransactions: Boolean = false): List<BlockView> = JsonUtil.reqArr(call("janzeer_getBlocks", mapOf("fromHeight" to fromHeight, "toHeight" to toHeight, "includeTransactions" to includeTransactions))).map(BlockView::from)

    public fun getToken(tokenId: String): Token = Token.from(call("janzeer_getToken", mapOf("tokenId" to tokenId)))
    @JvmOverloads public fun listTokens(p: PageParams = PageParams()): RpcPage<Token> = RpcPage.from(call("janzeer_listTokens", p.params()), Token::from)
    @JvmOverloads public fun getTokenHolders(tokenId: String, p: PageParams = PageParams()): RpcPage<TokenBalance> = RpcPage.from(call("janzeer_getTokenHolders", p.params(mapOf("tokenId" to tokenId))), TokenBalance::from)

    @JvmOverloads public fun listValidators(p: PageParams = PageParams()): RpcPage<ValidatorInfo> = RpcPage.from(call("janzeer_listValidators", p.params()), ValidatorInfo::from)
    public fun getActiveValidators(): List<ValidatorInfo> = JsonUtil.reqArr(call("janzeer_getActiveValidators")).map(ValidatorInfo::from)
    public fun getValidator(key: String): ValidatorInfo = ValidatorInfo.from(call("janzeer_getValidator", mapOf("key" to key)))
}
