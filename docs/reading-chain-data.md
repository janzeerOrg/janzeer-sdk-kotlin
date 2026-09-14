# Reading chain data

`JanzeerClient` wraps every `/api/v1` endpoint with typed results. Amounts come back as decimal strings, token
quantities as `BigInteger`, timestamps as milliseconds. Responses are parsed losslessly — a 128-bit token cap or an
8-decimal balance never passes through a `Double`.

```kotlin
val client = JanzeerClient("https://node.example.org")   // "/api/v1/" is appended automatically
```

## Node

```kotlin
client.info();          // { nodeKey, host, port, networkId, genesisHash, chainSpecDigest, version, apiVersion, protocolVersion, syncStatus, faucet }
client.version();       // { version: '0.0.2', protocolVersion: '1.1.0' }
client.uptime();        // ms
client.explorerInfo();  // counts, TPS, epoch, supply
client.lastEnvelope;          // { timestamp, version } of the last response
```

## Accounts

```kotlin
client.balance(addr);   // spendable balance (committed − pending debits); '0' for an unknown address
client.nonce(addr);     // next nonce to sign with; 0 for an unknown address
client.validateAddress(addr)
client.tokens.balancesOf(addr)
```

The JSON-RPC `rpc.getAccount(addr)` returns all of it in one call (`balance`, `committedBalance`, `nextNonce`,
`committedNonce`, `pendingCount`, `isValidatorWallet`).

## Pagination

Every list takes `{ page, size, sortBy, sortDirection }` (`page` 0-based, `size` 1–100, node default 10) and
returns `Page<T> = { total, list, page, pageSize, totalPages }`.

```kotlin
val p = client.transfers.list(TxListQuery(address = addr, page = 0, size = 20))
for (t in p.list) println("${t.hash} ${t.amount} ${t.blockHash ?: "pending"}")
```

## Transactions

```kotlin
client.transfers.list(TxListQuery(address = a, unconfirmed = true))   // unconfirmed → the mempool
client.transfers.get(hash)                        // null when unknown
client.validatorTxs.list(TxListQuery(nodeKey = k))    // registrations
client.exitValidatorTxs.list(TxListQuery(address = a))
client.tokenTxs.list(TxListQuery(tokenId = id))
client.rewards.list(TxListQuery(address = a))                  // block rewards paid to `a`
client.receipt(hash)                              // execution receipt of a committed tx
```

For a transport-independent view of *any* transaction with its status (`FINAL | PENDING | UNKNOWN`), block and
receipt, use `rpc.getTransactionByHash(hash)`.

## Blocks

```kotlin
client.blocks.main.list(PageQuery(size = 5))      // newest first
client.blocks.main.get(hash); client.blocks.main.previous(hash); client.blocks.main.next(hash)
client.blocks.genesis.list()              // one per epoch: pins the epoch's validator set
```

`rpc.getTip()`, `rpc.getBlockByNumber(h, true)` and `rpc.getBlocks(from, to)` (≤ 100) give richer views with
the transactions inline.

## Validators and tokens

```kotlin
client.validators.list()      // every registered validator { address, nodeKey }
client.validators.active()    // the current epoch's producer set
client.validators.view()      // with registration timestamps
client.tokens.list(); client.tokens.get(tokenId); client.tokens.holders(tokenId)
```

## Choosing REST or JSON-RPC

Both talk to the same services. REST is convenient for explorers and dashboards (paged, cacheable GETs).
JSON-RPC adds batches, unified transaction/block views, `waitForFinality`, and the WebSocket feeds. Wallets
typically use REST for reads and writes and JSON-RPC (or WS) for finality and notifications.
