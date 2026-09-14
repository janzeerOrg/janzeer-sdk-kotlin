package org.janzeer.sdk.tx

import org.janzeer.sdk.Amounts
import org.janzeer.sdk.Janzeer
import org.janzeer.sdk.TokenOp
import org.janzeer.sdk.TxType
import org.janzeer.sdk.crypto.Hashes

/** Type-specific fields of a transaction as they appear in the REST/RPC request body. */
public sealed class TxFields {
    /** The type-specific part of the JSON body. */
    public abstract fun toBody(): Map<String, Any?>
}

/** Native-coin transfer fields. */
public data class TransferFields(val amount: String, val recipientAddress: String, val data: String? = null) : TxFields() {
    override fun toBody(): Map<String, Any?> = buildMap { put("amount", amount); put("recipientAddress", recipientAddress); if (data != null) put("data", data) }
}

/** Validator registration fields. */
public data class RegisterValidatorFields(val amount: String, val validatorKey: String) : TxFields() {
    override fun toBody(): Map<String, Any?> = mapOf("amount" to amount, "validatorKey" to validatorKey)
}

/** Validator exit fields. */
public data class ExitValidatorFields(val validatorKey: String) : TxFields() {
    override fun toBody(): Map<String, Any?> = mapOf("validatorKey" to validatorKey)
}

/** JZT-1 token operation fields. Amounts are integer base units as decimal strings. */
public data class TokenFields(
    val op: TokenOp,
    val tokenId: String = "",
    val symbol: String = "",
    val name: String = "",
    val decimals: Int = 0,
    val cap: String? = null,
    val amount: String? = null,
    val recipient: String? = null,
) : TxFields() {
    override fun toBody(): Map<String, Any?> = buildMap {
        put("op", op.code)
        if (tokenId.isNotEmpty()) put("tokenId", tokenId)
        if (symbol.isNotEmpty()) put("symbol", symbol)
        if (name.isNotEmpty()) put("name", name)
        put("decimals", decimals)
        if (cap != null) put("cap", cap)
        if (amount != null) put("amount", amount)
        if (recipient != null) put("recipient", recipient)
    }
}

/**
 * A transaction that is fully specified but not yet signed. Produced by [TxBuilder]; sign it with
 * `account.signTx(tx)`, or sign [hash] elsewhere and attach it with [withSignature].
 */
public class UnsignedTx(
    public val type: TxType,
    public val timestamp: Long,
    fee: Any,
    public val nonce: Long,
    public val senderAddress: String,
    public val fields: TxFields,
    /** the type-specific payload bytes */
    public val payload: ByteArray,
    public val networkId: String = Janzeer.NETWORK_ID,
) {
    /** decimal string */
    public val fee: String = Amounts.normalize(fee)

    /** The exact bytes that are hashed and signed. */
    public fun preimage(): ByteArray = Payloads.transactionBytes(timestamp, fee, nonce, senderAddress, payload, networkId)

    /** Double-SHA256 of the preimage, lowercase hex — the transaction id. */
    public fun hash(): String = Hashes.hashPreimage(preimage())

    /** Attach a signature (+ the signer's public key). */
    public fun withSignature(signature: String, senderPublicKey: String): SignedTx = SignedTx(this, hash(), signature, senderPublicKey)
}

/** A signed transaction ready for `client.submit(tx)` / `rpc.send(tx)`. */
public class SignedTx(
    public val unsigned: UnsignedTx,
    /** transaction id (double-SHA256 of the preimage, hex) */
    public val hash: String,
    /** Base64 DER ECDSA signature over [hash] */
    public val signature: String,
    /** compressed secp256k1 public key, hex */
    public val senderPublicKey: String,
) {
    public val type: TxType get() = unsigned.type
    /** REST path relative to the API base */
    public val restPath: String get() = type.restPath
    /** the `janzeer_send*` method */
    public val rpcMethod: String get() = type.rpcMethod
    public val fields: TxFields get() = unsigned.fields

    /** The JSON body the node's REST endpoints and `janzeer_send*` methods accept. Amounts are strings. */
    public fun toRestBody(): Map<String, Any?> = linkedMapOf<String, Any?>(
        "timestamp" to unsigned.timestamp, "fee" to unsigned.fee, "nonce" to unsigned.nonce, "hash" to hash,
        "senderAddress" to unsigned.senderAddress, "senderPublicKey" to senderPublicKey, "senderSignature" to signature,
    ).apply { putAll(unsigned.fields.toBody()) }

    /** Same as [toRestBody] (the RPC methods take the REST body as their single object parameter). */
    public fun toRpcParams(): Map<String, Any?> = toRestBody()
}
