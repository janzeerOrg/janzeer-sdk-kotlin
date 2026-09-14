package org.janzeer.sdk.client

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.janzeer.sdk.Janzeer
import org.janzeer.sdk.TokenOp
import org.janzeer.sdk.client.JsonUtil.big
import org.janzeer.sdk.client.JsonUtil.bool
import org.janzeer.sdk.client.JsonUtil.dec
import org.janzeer.sdk.client.JsonUtil.int
import org.janzeer.sdk.client.JsonUtil.long
import org.janzeer.sdk.client.JsonUtil.optBig
import org.janzeer.sdk.client.JsonUtil.optDec
import org.janzeer.sdk.client.JsonUtil.optInt
import org.janzeer.sdk.client.JsonUtil.optLong
import org.janzeer.sdk.client.JsonUtil.optStr
import org.janzeer.sdk.client.JsonUtil.reqArr
import org.janzeer.sdk.client.JsonUtil.reqObj
import org.janzeer.sdk.client.JsonUtil.str
import java.math.BigDecimal
import java.math.BigInteger

/** A page of results (`{total, list, page, pageSize, totalPages}`). */
public data class Page<T>(val total: Long, val list: List<T>, val page: Int, val pageSize: Int, val totalPages: Int) {
    public companion object {
        /** Map a paged payload (null → empty page). */
        @JvmStatic public fun <T> from(e: JsonElement?, item: (JsonElement) -> T): Page<T> {
            val o = e as? JsonObject ?: return Page(0, emptyList(), 0, 0, 0)
            val list = (o["list"] as? JsonArray)?.map(item) ?: emptyList()
            return Page(optLong(o["total"]) ?: list.size.toLong(), list, optInt(o["page"]) ?: 0, optInt(o["pageSize"]) ?: list.size, optInt(o["totalPages"]) ?: 0)
        }
    }
}

/** Query of every paged endpoint. [page] is 0-based; [size] 1–100 (node default 10). */
public data class PageQuery @JvmOverloads constructor(val page: Int? = null, val size: Int? = null, val sortBy: List<String>? = null, val sortDirection: String? = null) {
    /** As query parameters. */
    public open fun toQuery(): Map<String, Any?> = mapOf("page" to page, "size" to size, "sortBy" to sortBy?.joinToString(","), "sortDirection" to sortDirection)
}

/** Filters of the transaction lists (`address` = sender or recipient; `unconfirmed` = the mempool). */
public data class TxListQuery @JvmOverloads constructor(
    val address: String? = null, val unconfirmed: Boolean = false, val nodeKey: String? = null, val tokenId: String? = null,
    val page: Int? = null, val size: Int? = null, val sortBy: List<String>? = null, val sortDirection: String? = null,
) {
    /** As query parameters. */
    public fun toQuery(): Map<String, Any?> = mapOf("page" to page, "size" to size, "sortBy" to sortBy?.joinToString(","), "sortDirection" to sortDirection, "address" to address, "unconfirmed" to if (unconfirmed) true else null, "nodeKey" to nodeKey, "tokenId" to tokenId)
}

/** `GET info` */
public data class NodeInfo(
    val nodeKey: String,
    val host: String,
    val port: Int,
    /** `consensus.network-id` — pass it as `networkId` to every builder on a non-mainnet node. */
    val networkId: String = Janzeer.NETWORK_ID,
    /** Hash of the epoch-1 genesis block: the chain's identity. */
    val genesisHash: String = "",
    /** Consensus rule-set + genesis digest exchanged in the P2P handshake. */
    val chainSpecDigest: String = "",
    /** Node software version. */
    val version: String = "",
    /** REST/JSON-RPC API version. */
    val apiVersion: String = "",
    /** P2P wire protocol version. */
    val protocolVersion: String = "",
    /** `SYNCHRONIZED`, `PROCESSING` or `NOT_SYNCHRONIZED`. */
    val syncStatus: String = "",
    /** True when the node runs a testnet faucet (`POST /api/v1/faucet`). */
    val faucet: Boolean = false
) {
    public companion object {
        @JvmStatic public fun from(e: JsonElement?): NodeInfo = reqObj(e).let {
            NodeInfo(
                str(it["nodeKey"], "nodeKey"), str(it["host"]), int(it["port"]),
                // fields added by node 0.0.3 — tolerate an older node
                networkId = it["networkId"]?.let { v -> str(v) } ?: Janzeer.NETWORK_ID,
                genesisHash = it["genesisHash"]?.let { v -> str(v) } ?: "",
                chainSpecDigest = it["chainSpecDigest"]?.let { v -> str(v) } ?: "",
                version = it["version"]?.let { v -> str(v) } ?: "",
                apiVersion = it["apiVersion"]?.let { v -> str(v) } ?: "",
                protocolVersion = it["protocolVersion"]?.let { v -> str(v) } ?: "",
                syncStatus = it["syncStatus"]?.let { v -> str(v) } ?: "",
                faucet = it["faucet"]?.let { v -> bool(v) } ?: false
            )
        }
    }
}

/** `GET info/version` */
public data class NodeVersion(val version: String, val protocolVersion: String) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): NodeVersion = reqObj(e).let { NodeVersion(str(it["version"]), str(it["protocolVersion"])) } }
}

/** `GET explorer/info` */
public data class ExplorerInfo(
    val nodesCount: Int, val blocksCount: Long, val transactionsCount: Long, val blockProductionTime: Long, val transactionsPerSecond: BigDecimal,
    val currentEpochNumber: Long, val currentEpochDate: Long, val validatorsCount: Int, val maxSupply: BigDecimal, val circulatingSupply: BigDecimal,
) {
    public companion object {
        @JvmStatic public fun from(e: JsonElement?): ExplorerInfo = reqObj(e).let {
            ExplorerInfo(int(it["nodesCount"]), long(it["blocksCount"]), long(it["transactionsCount"]), long(it["blockProductionTime"]), dec(it["transactionsPerSecond"]), long(it["currentEpochNumber"]), long(it["currentEpochDate"]), int(it["validatorsCount"]), dec(it["maxSupply"]), dec(it["circulatingSupply"]))
        }
    }
}

/** Common block fields. */
public sealed class BaseBlock {
    public abstract val timestamp: Long
    public abstract val height: Long
    public abstract val previousHash: String
    public abstract val hash: String
    public abstract val signature: String
    public abstract val publicKey: String
}

/** A main (transaction) block. */
public data class MainBlock(override val timestamp: Long, override val height: Long, override val previousHash: String, override val hash: String, override val signature: String, override val publicKey: String, val merkleHash: String, val transactionsCount: Long, val epochIndex: Long) : BaseBlock() {
    public companion object { @JvmStatic public fun from(e: JsonElement?): MainBlock = reqObj(e).let { MainBlock(long(it["timestamp"]), long(it["height"]), str(it["previousHash"]), str(it["hash"]), str(it["signature"]), str(it["publicKey"]), str(it["merkleHash"]), long(it["transactionsCount"]), long(it["epochIndex"])) } }
}

/** An epoch genesis block. */
public data class GenesisBlock(override val timestamp: Long, override val height: Long, override val previousHash: String, override val hash: String, override val signature: String, override val publicKey: String, val epochIndex: Long, val validatorsCount: Int) : BaseBlock() {
    public companion object { @JvmStatic public fun from(e: JsonElement?): GenesisBlock = reqObj(e).let { GenesisBlock(long(it["timestamp"]), long(it["height"]), str(it["previousHash"]), str(it["hash"]), str(it["signature"]), str(it["publicKey"]), long(it["epochIndex"]), int(it["validatorsCount"])) } }
}

/** One line of an execution receipt. */
public data class ReceiptResult(val from: String, val to: String, val amount: BigDecimal, val data: String?, val error: String?) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): ReceiptResult = reqObj(e).let { ReceiptResult(str(it["from"]), str(it["to"]), dec(it["amount"]), optStr(it["data"]), optStr(it["error"])) } }
}

/** `GET transactions/{hash}/receipt` */
public data class Receipt(val transactionHash: String, val results: List<ReceiptResult>) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): Receipt = reqObj(e).let { Receipt(str(it["transactionHash"]), reqArr(it["results"]).map(ReceiptResult::from)) } }
}

/** Common transaction fields. */
public sealed class BaseTx {
    public abstract val hash: String
    public abstract val timestamp: Long
    public abstract val fee: BigDecimal
    public abstract val senderAddress: String
    public abstract val senderSignature: String
    public abstract val senderPublicKey: String
    /** `null` while pending */
    public abstract val blockHash: String?
    /** true once in a committed block */
    public val isFinal: Boolean get() = blockHash != null
}

/** A native-coin transfer. */
public data class TransferTx(override val hash: String, override val timestamp: Long, override val fee: BigDecimal, override val senderAddress: String, override val senderSignature: String, override val senderPublicKey: String, override val blockHash: String?, val status: Boolean, val amount: BigDecimal, val recipientAddress: String?, val data: String?, val results: List<ReceiptResult>?) : BaseTx() {
    public companion object { @JvmStatic public fun from(e: JsonElement?): TransferTx = reqObj(e, "tx").let { TransferTx(str(it["hash"]), long(it["timestamp"]), dec(it["fee"]), str(it["senderAddress"]), str(it["senderSignature"]), str(it["senderPublicKey"]), optStr(it["blockHash"]), JsonUtil.optBool(it["status"]) ?: true, dec(it["amount"]), optStr(it["recipientAddress"]), optStr(it["data"]), (it["results"] as? JsonArray)?.map(ReceiptResult::from)) } }
}

/** A validator registration (the node reports the key as `nodeKey`). */
public data class RegisterValidatorTx(override val hash: String, override val timestamp: Long, override val fee: BigDecimal, override val senderAddress: String, override val senderSignature: String, override val senderPublicKey: String, override val blockHash: String?, val validatorKey: String, val amount: BigDecimal) : BaseTx() {
    public companion object { @JvmStatic public fun from(e: JsonElement?): RegisterValidatorTx = reqObj(e, "tx").let { RegisterValidatorTx(str(it["hash"]), long(it["timestamp"]), dec(it["fee"]), str(it["senderAddress"]), str(it["senderSignature"]), str(it["senderPublicKey"]), optStr(it["blockHash"]), str(it["nodeKey"] ?: it["validatorKey"], "nodeKey"), dec(it["amount"])) } }
}

/** A validator exit. */
public data class ExitValidatorTx(override val hash: String, override val timestamp: Long, override val fee: BigDecimal, override val senderAddress: String, override val senderSignature: String, override val senderPublicKey: String, override val blockHash: String?, val validatorKey: String) : BaseTx() {
    public companion object { @JvmStatic public fun from(e: JsonElement?): ExitValidatorTx = reqObj(e, "tx").let { ExitValidatorTx(str(it["hash"]), long(it["timestamp"]), dec(it["fee"]), str(it["senderAddress"]), str(it["senderSignature"]), str(it["senderPublicKey"]), optStr(it["blockHash"]), str(it["nodeKey"] ?: it["validatorKey"], "nodeKey")) } }
}

/** A block reward (system transaction). */
public data class RewardTx(override val hash: String, override val timestamp: Long, override val fee: BigDecimal, override val senderAddress: String, override val senderSignature: String, override val senderPublicKey: String, override val blockHash: String?, val reward: BigDecimal, val recipientAddress: String) : BaseTx() {
    public companion object { @JvmStatic public fun from(e: JsonElement?): RewardTx = reqObj(e, "tx").let { RewardTx(str(it["hash"]), long(it["timestamp"]), dec(it["fee"]), str(it["senderAddress"]), str(it["senderSignature"]), str(it["senderPublicKey"]), optStr(it["blockHash"]), dec(it["reward"]), str(it["recipientAddress"])) } }
}

/** A JZT-1 token transaction. */
public data class TokenTx(override val hash: String, override val timestamp: Long, override val fee: BigDecimal, override val senderAddress: String, override val senderSignature: String, override val senderPublicKey: String, override val blockHash: String?, val op: TokenOp, val tokenId: String, val symbol: String, val name: String, val decimals: Int, val cap: BigInteger?, val amount: BigInteger?, val recipient: String?) : BaseTx() {
    public companion object { @JvmStatic public fun from(e: JsonElement?): TokenTx = reqObj(e, "tx").let { TokenTx(str(it["hash"]), long(it["timestamp"]), dec(it["fee"]), str(it["senderAddress"]), str(it["senderSignature"]), str(it["senderPublicKey"]), optStr(it["blockHash"]), TokenOp.fromCode(int(it["op"])), str(it["tokenId"]), str(it["symbol"]), str(it["name"]), int(it["decimals"]), optBig(it["cap"]), optBig(it["amount"]), optStr(it["recipient"])) } }
}

/** `GET validators` */
public data class Validator(val address: String, val nodeKey: String) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): Validator = reqObj(e).let { Validator(str(it["address"]), str(it["nodeKey"])) } }
}

/** `GET validators/view` */
public data class ValidatorView(val address: String, val publicKey: String, val timestamp: Long) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): ValidatorView = reqObj(e).let { ValidatorView(str(it["address"]), str(it["publicKey"]), long(it["timestamp"])) } }
}

/** A token definition. */
public data class Token(val tokenId: String, val symbol: String, val name: String, val decimals: Int, val cap: BigInteger, val totalSupply: BigInteger, val issuer: String) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): Token = reqObj(e).let { Token(str(it["tokenId"]), str(it["symbol"]), str(it["name"]), int(it["decimals"]), big(it["cap"]), big(it["totalSupply"]), str(it["issuer"])) } }
}

/** A token balance. */
public data class TokenBalance(val tokenId: String, val holder: String, val balance: BigInteger) {
    public companion object { @JvmStatic public fun from(e: JsonElement?): TokenBalance = reqObj(e).let { TokenBalance(str(it["tokenId"]), str(it["holder"]), big(it["balance"])) } }
}

internal fun optDecOf(e: JsonElement?): BigDecimal? = optDec(e)
internal fun boolOf(e: JsonElement?): Boolean = bool(e)
