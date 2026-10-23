package org.janzeer.sdk

/** Chain constants — every value mirrors the node and is pinned by the conformance vectors or the e2e flow. */
public object Janzeer {
    /** `consensus.network-id`, bound into every signed transaction preimage (cross-network replay protection). */
    public const val NETWORK_ID: String = "janzeer"
    /** Native coin decimals: amounts are scaled ×10^8 into an int64 on the wire. */
    public const val DECIMALS: Int = 8
    /** Display ticker of the native coin. */
    public const val TICKER: String = "JNZ"
    /** Minimum fee of every transaction, in JNZ. */
    public const val MIN_FEE: String = "0.01"
    /** Minimum transfer amount unless the transfer carries a memo. */
    public const val MIN_TRANSFER: String = "0.1"
    /** Maximum memo (`data`) length in UTF-8 bytes. */
    public const val MAX_MEMO_BYTES: Int = 256
    /** Exact fee of a validator registration. */
    public const val VALIDATOR_FEE: String = "3"
    /** Exact, NON-REFUNDABLE deposit of a validator registration. */
    public const val VALIDATOR_DEPOSIT: String = "2000"
    /** Exact fee of a token CREATE; other token ops pay [MIN_FEE]. */
    public const val TOKEN_CREATE_FEE: String = "5"
}

/** The node / API / wire-protocol versions this SDK release was built and tested against. */
public object SpecVersion {
    /** `GET info/version` → `version` */
    public const val NODE: String = "0.1.8"
    /** REST envelope `version` and JSON-RPC `apiVersion` */
    public const val API: String = "1.1.0"
    /** peer-to-peer wire protocol (informational) */
    public const val PROTOCOL: String = "3.2.0"
    /** conformance vectors format consumed by this SDK's tests */
    public const val VECTORS: Int = 2
}

/** Server-side limits (informational; the node enforces them). */
public object Limits {
    /** max `size` of a paged list */
    public const val PAGE_SIZE: Int = 100
    /** max `toHeight - fromHeight` of `janzeer_getBlocks` */
    public const val BLOCK_RANGE: Int = 100
    /** max `timeoutMs` of one `janzeer_waitForFinality` call */
    public const val WAIT_FOR_FINALITY_MS: Long = 60_000
    /** subscriptions per WebSocket session */
    public const val SUBSCRIPTIONS_PER_SESSION: Int = 16
    /** addresses per `addressActivity` subscription */
    public const val WATCHED_ADDRESSES: Int = 1000
    /** requests per `/rpc` batch */
    public const val RPC_BATCH: Int = 50
}

/** JZT-1 token operation codes carried in the `op` byte. */
public enum class TokenOp(public val code: Int, public val wireName: String) {
    /** create a token (the tokenId is the tx hash) */
    CREATE(0, "CREATE"),
    /** mint units to a recipient (issuer only) */
    MINT(1, "MINT"),
    /** burn the sender's units (issuer only) */
    BURN(2, "BURN"),
    /** change the cap (issuer only) */
    SETCAP(3, "SETCAP"),
    /** move units to a recipient */
    TRANSFER(4, "TRANSFER");

    public companion object {
        /** From the wire byte. */
        @JvmStatic public fun fromCode(code: Int): TokenOp = entries.firstOrNull { it.code == code } ?: throw IllegalArgumentException("unknown token op $code")
        /** From the node's name, or null. */
        @JvmStatic public fun fromWireName(name: String?): TokenOp? = entries.firstOrNull { it.wireName == name }
    }
}

/** Client-submittable transaction types (the RPC `txView.type` strings). */
public enum class TxType(public val wireName: String, public val restPath: String, public val rpcMethod: String) {
    /** native-coin transfer */
    TRANSFER("transfer", "transactions/transfers", "janzeer_sendTransfer"),
    /** JZT-1 token operation */
    TOKEN("token", "transactions/tokens", "janzeer_sendToken"),
    /** validator registration (fee 3, deposit 2000) */
    REGISTER_VALIDATOR("registerValidator", "transactions/validators", "janzeer_sendRegisterValidator"),
    /** validator exit */
    EXIT_VALIDATOR("exitValidator", "transactions/exit-validators", "janzeer_sendExitValidator");
}
