package org.janzeer.sdk.tx

import org.janzeer.sdk.Amounts
import org.janzeer.sdk.Janzeer
import org.janzeer.sdk.TokenOp
import org.janzeer.sdk.TxType
import org.janzeer.sdk.crypto.Address
import org.janzeer.sdk.crypto.Bytes
import java.math.BigInteger

/**
 * Transaction builders. Each returns an [UnsignedTx]; sign with `account.signTx(tx)`. The builders validate what
 * the node validates at intake (amounts, memo size, exact validator fees) so a mistake fails locally with an
 * [IllegalArgumentException]. `nonce` comes from the node (`client.nonce(address)` / `rpc.getAccount(address).nextNonce`).
 */
public object TxBuilder {
    private val KEY_RE = Regex("^0[23][0-9a-fA-F]{64}$")
    private val TOKEN_ID_RE = Regex("^[0-9a-fA-F]{64}$")

    /** A native-coin transfer. Fee defaults to [Janzeer.MIN_FEE]. With a memo ([data]) the amount may be below [Janzeer.MIN_TRANSFER]. */
    @JvmStatic @JvmOverloads public fun transfer(from: String, to: String, amount: Any, nonce: Long, data: String? = null, fee: Any = Janzeer.MIN_FEE, timestamp: Long = System.currentTimeMillis(), networkId: String = Janzeer.NETWORK_ID): UnsignedTx {
        val b = base(from, nonce, fee)
        val a = Amounts.normalize(amount)
        require(Amounts.isValid(a) && !a.startsWith("-")) { "amount must be a decimal with ≤ 8 fractional digits, got $a" }
        val memo = data?.takeIf { it.isNotEmpty() }
        require(memo == null || Bytes.utf8(memo).size <= Janzeer.MAX_MEMO_BYTES) { "memo exceeds ${Janzeer.MAX_MEMO_BYTES} UTF-8 bytes" }
        require(memo != null || Amounts.compare(a, Janzeer.MIN_TRANSFER) >= 0) { "amount must be ≥ ${Janzeer.MIN_TRANSFER} unless the transfer carries a memo" }
        val fields = TransferFields(a, Address.normalize(to), memo)
        return UnsignedTx(TxType.TRANSFER, timestamp, b.fee, nonce, b.from, fields, Payloads.transfer(a, fields.recipientAddress, memo), networkId)
    }

    /** Register a validator node: pays exactly [Janzeer.VALIDATOR_FEE] and locks the NON-REFUNDABLE [Janzeer.VALIDATOR_DEPOSIT]. */
    @JvmStatic @JvmOverloads public fun registerValidator(from: String, validatorKey: String, nonce: Long, amount: Any = Janzeer.VALIDATOR_DEPOSIT, fee: Any = Janzeer.VALIDATOR_FEE, timestamp: Long = System.currentTimeMillis(), networkId: String = Janzeer.NETWORK_ID): UnsignedTx {
        val b = base(from, nonce, fee)
        require(Amounts.compare(b.fee, Janzeer.VALIDATOR_FEE) == 0) { "validator registration fee must be exactly ${Janzeer.VALIDATOR_FEE}" }
        val a = Amounts.normalize(amount)
        require(Amounts.compare(a, Janzeer.VALIDATOR_DEPOSIT) == 0) { "validator deposit must be exactly ${Janzeer.VALIDATOR_DEPOSIT}" }
        val fields = RegisterValidatorFields(a, checkKey(validatorKey))
        return UnsignedTx(TxType.REGISTER_VALIDATOR, timestamp, b.fee, nonce, b.from, fields, Payloads.registerValidator(a, fields.validatorKey), networkId)
    }

    /** Gracefully exit a validator (removed at the next epoch; the deposit stays locked). */
    @JvmStatic @JvmOverloads public fun exitValidator(from: String, validatorKey: String, nonce: Long, fee: Any = Janzeer.MIN_FEE, timestamp: Long = System.currentTimeMillis(), networkId: String = Janzeer.NETWORK_ID): UnsignedTx {
        val b = base(from, nonce, fee)
        val fields = ExitValidatorFields(checkKey(validatorKey))
        return UnsignedTx(TxType.EXIT_VALIDATOR, timestamp, b.fee, nonce, b.from, fields, Payloads.exitValidator(fields.validatorKey), networkId)
    }

    /** JZT-1 token operations (`TxBuilder.Token.create(...)` …). Token amounts are integer base units ([BigInteger], [Long] or digit [String]). */
    public object Token {
        /** Create a token; the new `tokenId` is the transaction hash. Fee is exactly [Janzeer.TOKEN_CREATE_FEE]. Sender must be a validator wallet. */
        @JvmStatic @JvmOverloads public fun create(from: String, symbol: String, name: String, nonce: Long, decimals: Int = 0, cap: Any? = null, amount: Any? = null, fee: Any = Janzeer.TOKEN_CREATE_FEE, timestamp: Long = System.currentTimeMillis(), networkId: String = Janzeer.NETWORK_ID): UnsignedTx {
            val b = base(from, nonce, fee)
            require(Amounts.compare(b.fee, Janzeer.TOKEN_CREATE_FEE) == 0) { "token create fee must be exactly ${Janzeer.TOKEN_CREATE_FEE}" }
            require(decimals in 0..18) { "decimals must be 0–18" }
            require(symbol.isNotBlank() && name.isNotBlank()) { "symbol and name are required" }
            return tokenTx(b, TokenFields(TokenOp.CREATE, symbol = symbol.trim(), name = name.trim(), decimals = decimals, cap = cap?.let { units(it, "cap") }, amount = amount?.let { units(it, "amount") }), timestamp, networkId)
        }
        /** Mint units (issuer only); [recipient] defaults to the sender. */
        @JvmStatic @JvmOverloads public fun mint(from: String, tokenId: String, amount: Any, nonce: Long, recipient: String? = null, fee: Any = Janzeer.MIN_FEE, timestamp: Long = System.currentTimeMillis(), networkId: String = Janzeer.NETWORK_ID): UnsignedTx =
            tokenTx(base(from, nonce, fee), TokenFields(TokenOp.MINT, tokenId = checkTokenId(tokenId), amount = units(amount, "amount"), recipient = recipient?.let(Address::normalize)), timestamp, networkId)
        /** Burn the sender's units (issuer only). */
        @JvmStatic @JvmOverloads public fun burn(from: String, tokenId: String, amount: Any, nonce: Long, fee: Any = Janzeer.MIN_FEE, timestamp: Long = System.currentTimeMillis(), networkId: String = Janzeer.NETWORK_ID): UnsignedTx =
            tokenTx(base(from, nonce, fee), TokenFields(TokenOp.BURN, tokenId = checkTokenId(tokenId), amount = units(amount, "amount")), timestamp, networkId)
        /** Change the cap (issuer only). */
        @JvmStatic @JvmOverloads public fun setCap(from: String, tokenId: String, cap: Any, nonce: Long, fee: Any = Janzeer.MIN_FEE, timestamp: Long = System.currentTimeMillis(), networkId: String = Janzeer.NETWORK_ID): UnsignedTx =
            tokenTx(base(from, nonce, fee), TokenFields(TokenOp.SETCAP, tokenId = checkTokenId(tokenId), cap = units(cap, "cap")), timestamp, networkId)
        /** Move units to [recipient]. */
        @JvmStatic @JvmOverloads public fun transfer(from: String, tokenId: String, amount: Any, recipient: String, nonce: Long, fee: Any = Janzeer.MIN_FEE, timestamp: Long = System.currentTimeMillis(), networkId: String = Janzeer.NETWORK_ID): UnsignedTx =
            tokenTx(base(from, nonce, fee), TokenFields(TokenOp.TRANSFER, tokenId = checkTokenId(tokenId), amount = units(amount, "amount"), recipient = Address.normalize(recipient)), timestamp, networkId)
        /** Escape hatch: any op with raw fields (no validation beyond the payload layout). */
        @JvmStatic @JvmOverloads public fun raw(from: String, fields: TokenFields, nonce: Long, fee: Any = if (fields.op == TokenOp.CREATE) Janzeer.TOKEN_CREATE_FEE else Janzeer.MIN_FEE, timestamp: Long = System.currentTimeMillis(), networkId: String = Janzeer.NETWORK_ID): UnsignedTx =
            tokenTx(base(from, nonce, fee), fields, timestamp, networkId)
    }

    private class Base(val from: String, val fee: String, val nonce: Long)

    private fun base(from: String, nonce: Long, fee: Any): Base {
        val f = Amounts.normalize(fee)
        require(Amounts.isValid(f) && Amounts.compare(f, Janzeer.MIN_FEE) >= 0) { "fee must be a decimal ≥ ${Janzeer.MIN_FEE}, got $f" }
        require(nonce >= 0) { "nonce must be a non-negative integer, got $nonce" }
        return Base(Address.normalize(from), f, nonce)
    }

    private fun tokenTx(b: Base, f: TokenFields, timestamp: Long, networkId: String): UnsignedTx =
        UnsignedTx(TxType.TOKEN, timestamp, b.fee, b.nonce, b.from, f, Payloads.token(f.op.code, f.tokenId, f.symbol, f.name, f.decimals, f.cap, f.amount, f.recipient), networkId)

    private fun units(v: Any, what: String): String {
        val s = when (v) { is BigInteger, is Long, is Int -> v.toString(); else -> v.toString().trim() }
        require(s.matches(Regex("^\\d+$"))) { "$what must be a non-negative integer of base units, got $v" }
        return s
    }

    private fun checkKey(key: String): String {
        require(KEY_RE.matches(key)) { "validatorKey must be a compressed secp256k1 public key (66 hex chars)" }
        return key.lowercase()
    }

    private fun checkTokenId(id: String): String {
        require(TOKEN_ID_RE.matches(id)) { "tokenId must be a 64-hex transaction hash" }
        return id.lowercase()
    }
}
