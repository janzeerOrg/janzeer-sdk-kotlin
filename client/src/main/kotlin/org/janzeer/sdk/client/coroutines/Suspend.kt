package org.janzeer.sdk.client.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.janzeer.sdk.client.Helpers
import org.janzeer.sdk.client.JanzeerClient
import org.janzeer.sdk.rpc.RpcMethods
import org.janzeer.sdk.rpc.TxView
import org.janzeer.sdk.tx.SignedTx
import java.math.BigDecimal

/** `suspend` conveniences over the blocking clients: they run on [Dispatchers.IO]. Any blocking call can be wrapped the same way. */
public suspend fun JanzeerClient.balanceAsync(address: String): BigDecimal = withContext(Dispatchers.IO) { balance(address) }
public suspend fun JanzeerClient.nonceAsync(address: String): Long = withContext(Dispatchers.IO) { nonce(address) }
public suspend fun JanzeerClient.submitAsync(tx: SignedTx): org.janzeer.sdk.client.BaseTx = withContext(Dispatchers.IO) { submit(tx) }
public suspend fun RpcMethods.sendAsync(tx: SignedTx): org.janzeer.sdk.rpc.SendResult = withContext(Dispatchers.IO) { send(tx) }
public suspend fun RpcMethods.getAccountAsync(address: String): org.janzeer.sdk.rpc.AccountView = withContext(Dispatchers.IO) { getAccount(address) }
/** [Helpers.waitForFinality] on the IO dispatcher. */
public suspend fun waitForFinalityAsync(source: Any, hash: String, timeoutMs: Long = 180_000): TxView = withContext(Dispatchers.IO) { Helpers.waitForFinality(source, hash, timeoutMs) }
/** [Helpers.sendAndWait] on the IO dispatcher. */
public suspend fun sendAndWaitAsync(via: Any, tx: SignedTx, submitVia: Any? = null, timeoutMs: Long = 180_000): TxView = withContext(Dispatchers.IO) { Helpers.sendAndWait(via, tx, submitVia, timeoutMs) }
