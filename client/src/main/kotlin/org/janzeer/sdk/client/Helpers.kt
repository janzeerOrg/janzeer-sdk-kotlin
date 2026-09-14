package org.janzeer.sdk.client

import org.janzeer.sdk.Limits
import org.janzeer.sdk.rpc.RpcMethods
import org.janzeer.sdk.rpc.TxStatus
import org.janzeer.sdk.rpc.TxView
import org.janzeer.sdk.tx.SignedTx

/** Thrown by [Helpers.waitForFinality] when the deadline passes; [last] is the most recent view (may be PENDING/UNKNOWN). */
public class FinalityTimeoutException(public val hash: String, public val last: TxView?) : JanzeerException("transaction $hash not final within the deadline (last status: ${last?.status?.wireName ?: "unknown"})")

/** High-level flows composed from the clients. */
public object Helpers {
    /**
     * Wait until [hash] is in a committed block. With an RPC transport ([RpcMethods]: HTTP or WS) it uses the node's
     * long-poll `janzeer_waitForFinality` in ≤ 60 s slices; with a [JanzeerClient] it polls the transaction endpoints.
     * Returns the FINAL [TxView]; throws [FinalityTimeoutException] on deadline.
     */
    @JvmStatic @JvmOverloads public fun waitForFinality(source: Any, hash: String, timeoutMs: Long = 180_000, pollMs: Long = 1500): TxView {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: TxView? = null
        while (System.currentTimeMillis() < deadline) {
            when (source) {
                is JanzeerClient -> {
                    last = restView(source, hash)
                    if (last?.isFinal == true) return last
                    Thread.sleep(pollMs)
                }
                is RpcMethods -> {
                    val slice = (deadline - System.currentTimeMillis()).coerceIn(1000, Limits.WAIT_FOR_FINALITY_MS)
                    try { last = source.waitForFinality(hash, slice) } catch (e: RpcNotFoundException) { last = null } catch (e: NetworkException) { Thread.sleep(1000); continue }
                    if (last?.isFinal == true) return last
                }
                else -> throw IllegalArgumentException("source must be a JanzeerClient or an RPC transport")
            }
        }
        throw FinalityTimeoutException(hash, last)
    }

    private fun restView(client: JanzeerClient, hash: String): TxView? {
        val t: BaseTx = client.transfers.get(hash) ?: client.tokenTxs.get(hash) ?: client.validatorTxs.get(hash) ?: client.exitValidatorTxs.get(hash) ?: return null
        return TxView(t.hash, null, if (t.isFinal) TxStatus.FINAL else TxStatus.PENDING, t.timestamp, t.fee, null, t.senderAddress, t.senderPublicKey, t.senderSignature, t.blockHash, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false)
    }

    /** The sender's next nonce from either transport. */
    @JvmStatic public fun nextNonce(source: Any, address: String): Long = when (source) {
        is JanzeerClient -> source.nonce(address)
        is RpcMethods -> source.getNonce(address)
        else -> throw IllegalArgumentException("source must be a JanzeerClient or an RPC transport")
    }

    /** Submit a signed transaction and wait for finality. [via] is used for both unless [submitVia] is given. */
    @JvmStatic @JvmOverloads public fun sendAndWait(via: Any, tx: SignedTx, submitVia: Any? = null, timeoutMs: Long = 180_000): TxView {
        when (val s = submitVia ?: via) {
            is JanzeerClient -> s.submit(tx)
            is RpcMethods -> s.send(tx)
            else -> throw IllegalArgumentException("submitVia must be a JanzeerClient or an RPC transport")
        }
        return waitForFinality(via, tx.hash, timeoutMs)
    }
}
