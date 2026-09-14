# JSON-RPC and subscriptions

The node serves JSON-RPC 2.0 at `POST /rpc` and over WebSocket at `/rpc/ws` (same methods, plus
subscriptions). `JanzeerRpc` and `JanzeerRpcWs` expose **one typed method per `janzeer_*` method**; `call()`
sends anything else.

```kotlin
val rpc = JanzeerRpc("http://localhost:7019")            // "/rpc" appended automatically
rpc.getInfo();          // chainId, versions, tip, syncStatus, peers, finality: 'instant'
rpc.getChainSpec();     // consensus parameters, fees, deposit, max supply
rpc.getStats(); rpc.getEpoch()
rpc.getAccount(addr); rpc.getBalance(addr); rpc.getNonce(addr)
rpc.getTransactionsByAddress(addr, PageParams(size = 20))        // page + pending
rpc.getTransactionByHash(hash);                          // status FINAL | PENDING | UNKNOWN
rpc.getTip(); rpc.getBlockByNumber(12, true); rpc.getBlocks(1, 100)
rpc.listValidators(); rpc.getActiveValidators(); rpc.getValidator(key)
rpc.listTokens(); rpc.getToken(id); rpc.getTokenHolders(id)
rpc.estimateFee("transfer")                             // fee, rule: "minimum" | "exact"
rpc.send(signedTx);                                      // or sendTransfer(body) etc.
rpc.call('janzeer_methods');                             // raw
```

## Batches

```kotlin
val r = rpc.batch(listOf(BatchRequest("janzeer_getNonce", mapOf("address" to address)), BatchRequest("janzeer_getTip")))
if (r[0].ok) println(r[0].result)      // each entry: result or error
```

Up to 50 requests per batch, 512 KiB per body. Results keep request order; one failing entry does not throw.

## Finality

`rpc.waitForFinality(hash, timeoutMs)` is the node's long-poll (≤ 60 s per call). The helper
`Helpers.waitForFinality(rpc | ws | client, hash, { timeoutMs })` chains calls until a client-side deadline (default 3
minutes), tolerates a dropped connection, and throws `FinalityTimeoutException` (with the last view) on expiry.
Given a REST client instead of an RPC transport it polls the transaction endpoints.

A `FINAL` view carries `blockHash`, `blockHeight` and `receipt.successful`. The chain has instant finality
(2f+1 BFT commit), so no confirmation counting exists anywhere in the API.

## WebSocket

```kotlin
val ws = JanzeerRpcWs.connect("ws://localhost:7019/rpc/ws")   // http(s) urls are converted
ws.getTip();                                                     // every method works over the socket

val blocks = ws.subscribeNewBlocks(NewBlocksParams(fromHeight = 100, includeTransactions = true)) { block -> … }
val watch  = ws.subscribeAddressActivity(listOf(addr1, addr2)) { ev -> … }
watch.unsubscribe()
ws.close()
```

- `newBlocks` replays committed blocks from `fromHeight`, then streams live ones — exactly once per height.
- `addressActivity` fires for every **final** transaction whose sender, recipient or token recipient is in the
  list (≤ 1000 addresses, ≤ 16 subscriptions per socket).
- **Reconnect**: on a dropped socket the client reconnects with exponential backoff (1 s → 30 s) and re-issues
  every subscription; `subscription.id` changes, handlers stay. Pass `reconnect = false` to opt out, and
  `onEvent` to log `WsEvent.Open` / `Closed` / `Error` / `Resubscribed`.
- Slow consumers are disconnected by the node (2 MiB send buffer, 5 s send timeout) — keep handlers fast.
- Notifications arrive on OkHttp's reader thread; hop to your own executor / main thread before touching UI.

## Errors

JSON-RPC errors map to the same classes as REST — see [errors](errors.md). A `-32001` rejection is a
`TxRejectedException` (or `NonceMismatchException`), `-32602` a `RpcInvalidParamsException` whose `data` lists the field
messages, `-32000` a `RpcNotFoundException`.
