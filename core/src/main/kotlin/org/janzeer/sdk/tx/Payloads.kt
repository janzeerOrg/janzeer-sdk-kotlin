package org.janzeer.sdk.tx

import org.janzeer.sdk.Amounts
import org.janzeer.sdk.Janzeer
import org.janzeer.sdk.crypto.Bytes

/** Signed-bytes (preimage) layouts — byte-for-byte `Transaction.bytes()` of the node. */
public object Payloads {
    /** `networkId ‖ timestamp(8) ‖ toScaledLong(fee)(8) ‖ nonce(8) ‖ senderAddress ‖ payload`. */
    @JvmStatic @JvmOverloads public fun transactionBytes(timestamp: Long, fee: Any, nonce: Long, senderAddress: String, payload: ByteArray, networkId: String = Janzeer.NETWORK_ID): ByteArray =
        Bytes.concat(Bytes.utf8(networkId), Bytes.i64be(timestamp), Bytes.i64be(Amounts.toScaledLong(fee)), Bytes.i64be(nonce), Bytes.utf8(senderAddress), payload)

    /** Transfer payload: `toScaledLong(amount)(8) ‖ recipientAddress ‖ (data ?? "")`. */
    @JvmStatic @JvmOverloads public fun transfer(amount: Any, recipientAddress: String, data: String? = null): ByteArray =
        Bytes.concat(Bytes.i64be(Amounts.toScaledLong(amount)), Bytes.utf8(recipientAddress), Bytes.utf8(data ?: ""))

    /** Validator registration payload: `toScaledLong(amount)(8) ‖ validatorKey`. */
    @JvmStatic public fun registerValidator(amount: Any, validatorKey: String): ByteArray = Bytes.concat(Bytes.i64be(Amounts.toScaledLong(amount)), Bytes.utf8(validatorKey))

    /** Validator exit payload: `validatorKey` only. */
    @JvmStatic public fun exitValidator(validatorKey: String): ByteArray = Bytes.utf8(validatorKey)

    /** Token payload: `op(1) ‖ LP(tokenId) ‖ LP(symbol) ‖ LP(name) ‖ int32(decimals) ‖ LP(cap) ‖ LP(amount) ‖ LP(recipient)`. */
    @JvmStatic public fun token(op: Int, tokenId: String?, symbol: String?, name: String?, decimals: Int, cap: String?, amount: String?, recipient: String?): ByteArray =
        Bytes.concat(byteArrayOf((op and 0xff).toByte()), Bytes.lenPrefixed(tokenId), Bytes.lenPrefixed(symbol), Bytes.lenPrefixed(name), Bytes.ser32(decimals), Bytes.lenPrefixed(cap), Bytes.lenPrefixed(amount), Bytes.lenPrefixed(recipient))
}
