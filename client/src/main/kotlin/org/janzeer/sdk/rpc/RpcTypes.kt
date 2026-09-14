package org.janzeer.sdk.rpc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.janzeer.sdk.TokenOp
import org.janzeer.sdk.client.JsonUtil.bool
import org.janzeer.sdk.client.JsonUtil.dec
import org.janzeer.sdk.client.JsonUtil.int
import org.janzeer.sdk.client.JsonUtil.long
import org.janzeer.sdk.client.JsonUtil.optBig
import org.janzeer.sdk.client.JsonUtil.optBool
import org.janzeer.sdk.client.JsonUtil.optDec
import org.janzeer.sdk.client.JsonUtil.optInt
import org.janzeer.sdk.client.JsonUtil.optLong
import org.janzeer.sdk.client.JsonUtil.optStr
import org.janzeer.sdk.client.JsonUtil.reqArr
import org.janzeer.sdk.client.JsonUtil.reqObj
import org.janzeer.sdk.client.JsonUtil.str
import org.janzeer.sdk.client.ReceiptResult
import java.math.BigDecimal
import java.math.BigInteger

/** Transaction status. */
public enum class TxStatus(public val wireName: String) {
    FINAL("FINAL"), PENDING("PENDING"), UNKNOWN("UNKNOWN");
    public companion object { @JvmStatic public fun from(s: String?): TxStatus = entries.firstOrNull { it.wireName == s } ?: UNKNOWN }
}

/** Receipt inside a [TxView]. */
public data class ReceiptView(val successful: Boolean, val results: List<ReceiptResult>) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): ReceiptView = reqObj(e, "receipt").let { ReceiptView(bool(it["successful"]), reqArr(it["results"]).map(ReceiptResult::from)) } }
}

/** One shape for every transaction type; [type] tells them apart, FINAL carries block + receipt. */
public data class TxView(
    val hash: String, val type: String?, val status: TxStatus, val timestamp: Long?, val fee: BigDecimal?, val nonce: Long?,
    val senderAddress: String?, val senderPublicKey: String?, val senderSignature: String?, val blockHash: String?, val blockHeight: Long?, val receipt: ReceiptView?,
    /** transfer / reward / registerValidator */ val amount: BigDecimal?, val recipientAddress: String?, val data: String?, val validatorKey: String?,
    val op: TokenOp?, val tokenId: String?, val symbol: String?, val name: String?, val decimals: Int?, val cap: BigInteger?, val tokenAmount: BigInteger?, val recipient: String?,
    val offenderKey: String?, val slot: Long?, /** set by `janzeer_waitForFinality` when the wait expired */ val timedOut: Boolean,
) {
    public val isFinal: Boolean get() = status == TxStatus.FINAL
    public companion object {
        @JvmStatic public fun from(e: JsonElement?): TxView = reqObj(e, "tx").let { o ->
            val type = optStr(o["type"]); val isToken = type == "token"
            TxView(str(o["hash"], "hash"), type, TxStatus.from(optStr(o["status"])), optLong(o["timestamp"]), optDec(o["fee"]), optLong(o["nonce"]),
                optStr(o["senderAddress"]), optStr(o["senderPublicKey"]), optStr(o["senderSignature"]), optStr(o["blockHash"]), optLong(o["blockHeight"]), (o["receipt"] as? JsonObject)?.let(ReceiptView::from),
                if (isToken) null else optDec(o["amount"]), optStr(o["recipientAddress"]), optStr(o["data"]), optStr(o["validatorKey"]),
                TokenOp.fromWireName(optStr(o["op"])), optStr(o["tokenId"]), optStr(o["symbol"]), optStr(o["name"]), optInt(o["decimals"]), optBig(o["cap"]), if (isToken) optBig(o["amount"]) else null, optStr(o["recipient"]),
                optStr(o["offenderKey"]), optLong(o["slot"]), optBool(o["timedOut"]) == true)
        }
    }
}

/** A block as the RPC reports it. */
public data class BlockView(
    val type: String, val height: Long, val hash: String, val previousHash: String, val timestamp: Long, val producer: String, val signature: String, val epochIndex: Long?, val transactionsCount: Long,
    val transactionMerkleHash: String?, val stateMerkleHash: String?, val receiptMerkleHash: String?, val activeAnchorCount: Int?, val validators: List<String>?, val transactions: List<TxView>?,
) {
    public companion object {
        @JvmStatic public fun from(e: JsonElement?): BlockView = reqObj(e, "block").let { o ->
            BlockView(str(o["type"]), long(o["height"]), str(o["hash"]), str(o["previousHash"]), long(o["timestamp"]), str(o["producer"]), str(o["signature"]), optLong(o["epochIndex"]), optLong(o["transactionsCount"]) ?: 0,
                optStr(o["transactionMerkleHash"]), optStr(o["stateMerkleHash"]), optStr(o["receiptMerkleHash"]), optInt(o["activeAnchorCount"]), (o["validators"] as? JsonArray)?.map { str(it) }, (o["transactions"] as? JsonArray)?.map(TxView::from))
        }
    }
}

/** `janzeer_getAccount` */
public data class AccountView(val address: String, val balance: BigDecimal, val committedBalance: BigDecimal, val nextNonce: Long, val committedNonce: Long, val pendingCount: Int, val isValidatorWallet: Boolean) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): AccountView = reqObj(e, "account").let { AccountView(str(it["address"]), dec(it["balance"]), dec(it["committedBalance"]), long(it["nextNonce"]), long(it["committedNonce"]), int(it["pendingCount"]), bool(it["isValidatorWallet"])) } }
}

/** `janzeer_getInfo` */
public data class NodeInfoView(val chainId: String, val chainSpecDigest: String, val version: String, val apiVersion: String, val wireProtocolVersion: String, val nodeKey: String, val tipHeight: Long, val tipHash: String, val tipTimestamp: Long, val epoch: Long, val syncStatus: String, val synchronized: Boolean, val chainCorrupt: Boolean, val peers: Int, val finality: String) {
    public companion object {
        @JvmStatic public fun from(e: JsonElement?): NodeInfoView = reqObj(e, "info").let { o -> val t = reqObj(o["tip"], "tip")
            NodeInfoView(str(o["chainId"]), str(o["chainSpecDigest"]), str(o["version"]), str(o["apiVersion"]), str(o["wireProtocolVersion"]), str(o["nodeKey"]), long(t["height"]), str(t["hash"]), long(t["timestamp"]), long(o["epoch"]), str(o["syncStatus"]), bool(o["synchronized"]), bool(o["chainCorrupt"]), int(o["peers"]), str(o["finality"])) }
    }
}

/** `janzeer_getChainSpec` */
public data class ChainSpecView(val networkId: String, val digest: String, val epochHeight: Long, val timeSlotDurationMs: Long, val timeSlotIntervalMs: Long, val slotPeriodMs: Long, val validatorsCount: Int, val rotatingProducerSeats: Int, val blockCapacity: Int, val rewardBlock: BigDecimal, val rewardHalvingPeriod: Long, val rewardTailFloor: BigDecimal, val treasuryFeeShareBps: Int, val minimumFee: BigDecimal, val validatorRegistrationFee: BigDecimal, val validatorDeposit: BigDecimal, val tokenCreateFee: BigDecimal, val tokenActivationHeight: Long, val maxSupply: BigDecimal, val treasuryAddress: String, val amountScale: Int) {
    public companion object {
        @JvmStatic public fun from(e: JsonElement?): ChainSpecView = reqObj(e, "chainSpec").let { o ->
            ChainSpecView(str(o["networkId"]), str(o["digest"]), long(o["epochHeight"]), long(o["timeSlotDurationMs"]), long(o["timeSlotIntervalMs"]), long(o["slotPeriodMs"]), int(o["validatorsCount"]), int(o["rotatingProducerSeats"]), int(o["blockCapacity"]), dec(o["rewardBlock"]), long(o["rewardHalvingPeriod"]), dec(o["rewardTailFloor"]), int(o["treasuryFeeShareBps"]), dec(o["minimumFee"]), dec(o["validatorRegistrationFee"]), dec(o["validatorDeposit"]), dec(o["tokenCreateFee"]), long(o["tokenActivationHeight"]), dec(o["maxSupply"]), str(o["treasuryAddress"]), int(o["amountScale"])) }
    }
}

/** `janzeer_getStats` */
public data class StatsView(val nodesCount: Int, val blocksCount: Long, val transactionsCount: Long, val pendingTransactions: Int, val blockProductionTimeMs: Long, val transactionsPerSecond: BigDecimal, val epoch: Long, val epochStart: Long, val validatorsCount: Int, val maxSupply: BigDecimal, val circulatingSupply: BigDecimal) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): StatsView = reqObj(e, "stats").let { o -> StatsView(int(o["nodesCount"]), long(o["blocksCount"]), long(o["transactionsCount"]), int(o["pendingTransactions"]), long(o["blockProductionTimeMs"]), dec(o["transactionsPerSecond"]), long(o["epoch"]), long(o["epochStart"]), int(o["validatorsCount"]), dec(o["maxSupply"]), dec(o["circulatingSupply"])) } }
}

/** `janzeer_getEpoch` */
public data class EpochView(val index: Long, val genesisHeight: Long, val genesisHash: String, val startTimestamp: Long, val epochHeight: Long, val nextGenesisHeight: Long, val slotPeriodMs: Long, val currentSlot: Long, val currentSlotOwner: String?, val activeAnchorCount: Int, val validators: List<String>) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): EpochView = reqObj(e, "epoch").let { o -> EpochView(long(o["index"]), long(o["genesisHeight"]), str(o["genesisHash"]), long(o["startTimestamp"]), long(o["epochHeight"]), long(o["nextGenesisHeight"]), long(o["slotPeriodMs"]), long(o["currentSlot"]), optStr(o["currentSlotOwner"]), int(o["activeAnchorCount"]), reqArr(o["validators"]).map { str(it) }) } }
}

/** `janzeer_listValidators` / `getValidator` */
public data class ValidatorInfo(val key: String, val walletAddress: String?, val registeredAt: Long?, val active: Boolean?, val banned: Boolean?, val exited: Boolean?) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): ValidatorInfo = reqObj(e, "validator").let { ValidatorInfo(str(it["key"]), optStr(it["walletAddress"]), optLong(it["registeredAt"]), optBool(it["active"]), optBool(it["banned"]), optBool(it["exited"])) } }
}

/** `janzeer_estimateFee` */
public data class FeeEstimate(val kind: String, val fee: BigDecimal, val rule: String, val feeMarket: Boolean) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): FeeEstimate = reqObj(e, "fee").let { FeeEstimate(str(it["kind"]), dec(it["fee"]), str(it["rule"]), bool(it["feeMarket"])) } }
}

/** `janzeer_send*` */
public data class SendResult(val hash: String, val status: String = "PENDING") {
    public companion object { @JvmStatic public fun from(e: JsonElement?): SendResult = SendResult(str(reqObj(e, "send")["hash"])) }
}

/** A paged RPC result. */
public data class RpcPage<T>(val total: Long, val page: Int, val pageSize: Int, val totalPages: Int, val list: List<T>) {
    public companion object { @JvmStatic public fun <T> from(e: JsonElement?, item: (JsonElement) -> T): RpcPage<T> = reqObj(e, "page").let { RpcPage(long(it["total"]), int(it["page"]), int(it["pageSize"]), int(it["totalPages"]), reqArr(it["list"]).map(item)) } }
}

/** `janzeer_getTransactionsByAddress` */
public data class AddressTransactions(val page: RpcPage<TxView>, val pending: List<TxView>) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): AddressTransactions = AddressTransactions(RpcPage.from(e, TxView::from), (reqObj(e)["pending"] as? JsonArray)?.map(TxView::from) ?: emptyList()) }
}

/** `janzeer_methods` */
public data class RpcMethodSpec(val name: String, val params: List<String>, val description: String) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): RpcMethodSpec = reqObj(e).let { RpcMethodSpec(str(it["name"]), reqArr(it["params"]).map { p -> str(p) }, str(it["description"])) } }
}

/** Paging parameters of the RPC lists (`size` 1–100, node default 20). */
public data class PageParams @JvmOverloads constructor(val page: Int? = null, val size: Int? = null) {
    internal fun params(extra: Map<String, Any?> = emptyMap()): Map<String, Any?> = buildMap { putAll(extra); if (page != null) put("page", page); if (size != null) put("size", size) }
}
