package org.janzeer.sdk.client.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import org.janzeer.sdk.rpc.*
import java.math.BigDecimal

/**
 * Suspend variants of [RpcMethods].
 */
public suspend fun RpcMethods.methodsAsync(): List<RpcMethodSpec> = withContext(Dispatchers.IO) { methods() }
public suspend fun RpcMethods.getInfoAsync(): NodeInfoView = withContext(Dispatchers.IO) { getInfo() }
public suspend fun RpcMethods.getChainSpecAsync(): ChainSpecView = withContext(Dispatchers.IO) { getChainSpec() }
public suspend fun RpcMethods.getGenesisAsync(): JsonElement = withContext(Dispatchers.IO) { getGenesis() }
public suspend fun RpcMethods.getStatsAsync(): StatsView = withContext(Dispatchers.IO) { getStats() }
public suspend fun RpcMethods.getEpochAsync(): EpochView = withContext(Dispatchers.IO) { getEpoch() }
public suspend fun RpcMethods.getAccountAsync(address: String): AccountView = withContext(Dispatchers.IO) { getAccount(address) }
public suspend fun RpcMethods.getBalanceAsync(address: String): BigDecimal = withContext(Dispatchers.IO) { getBalance(address) }
public suspend fun RpcMethods.getNonceAsync(address: String): Long = withContext(Dispatchers.IO) { getNonce(address) }
public suspend fun RpcMethods.getTipAsync(): Long = withContext(Dispatchers.IO) { getTip() }
