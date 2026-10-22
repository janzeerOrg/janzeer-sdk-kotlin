# Sending transactions

Every transaction is built locally, signed locally, and submitted as JSON. The node re-checks hash, signature,
sender/public-key match, nonce, balance and fees, and either accepts it into the mempool (HTTP 201 / RPC
`{hash, status: "PENDING"}`) or rejects it with a typed reason.

```kotlin
val unsigned = TxBuilder.transfer(from = me.address, to = to, amount = "1.25", nonce = nonce)
val signed   = me.signTx(unsigned)       // SignedTx: hash, signature, senderPublicKey
client.submit(signed)                    // REST
rpc.send(signed)                         // or JSON-RPC — same body, same result
```

## Nonces

Each sender has a nonce that must be consecutive. `client.nonce(address)` (or
`rpc.getAccount(address).nextNonce`) returns **committed nonce + pending count**, so you can pipeline several
transactions before the first is final: nonce `n`, `n+1`, `n+2`… A gap or a replay is refused with
`NonceMismatchException { expected, got }` — read `expected`, rebuild, resend.

Sending the *same* signed transaction twice is harmless: the node answers `PENDING` again (idempotent by hash).

## Network id — mainnet vs a private test network

Every transaction is signed for one network: `consensus.network-id` is the first field of the preimage, so a
transaction built for `janzeer` (mainnet) is rejected by a node of a private test network (`janzeer-testnet`) and vice versa — there is no public testnet. The builders default
to `NETWORK_ID` (`"janzeer"`); when your app can point at a private test network, read the id from the node once and pass it to
every builder:

```kotlin
val info = client.info()                         // networkId, genesisHash, version, syncStatus, faucet, …
val tx = acct.signTx(TxBuilder.transfer(from = acct.address, to = to, amount = "1.25", nonce = nonce, networkId = info.networkId))
```

`info.faucet` is `true` on a testnet node that runs a faucet (`POST /api/v1/faucet`).

## Fees and limits

| Transaction | Fee | Other rule |
|---|---|---|
| transfer | ≥ `Janzeer.MIN_FEE` (0.01) | amount ≥ `Janzeer.MIN_TRANSFER` (0.1) unless a memo is present; memo ≤ 256 UTF-8 bytes |
| registerValidator | exactly `Janzeer.VALIDATOR_FEE` (3) | deposit exactly `Janzeer.VALIDATOR_DEPOSIT` (2000), **non-refundable** |
| exitValidator | ≥ 0.01 | — |
| token.create | exactly `Janzeer.TOKEN_CREATE_FEE` (5) | sender must be a validator wallet |
| token.mint / burn / setCap / transfer | ≥ 0.01 | issuer-only for mint/burn/setCap |

`TxBuilder` fills the default fee and refuses locally anything the node would refuse for these reasons, so you
get a `RangeError` with a plain message instead of a 400. `rpc.estimateFee(kind)` returns the same numbers from
the node (`rule: "minimum" | "exact"`). There is no fee market.

## Amounts

Coin amounts are **decimal strings** (`'1.25'`). Internally they are scaled by 10^8 to an integer
(`Amounts.toScaledLong('1.25') === 125_000_000n`), which is what gets signed. Helpers:

```kotlin
Amounts.formatJnz('1.50000000')                  // '1.5 JNZ'
Amounts.formatJnz('1234.5', { group: true })     // '1,234.5 JNZ'
Amounts.parseJnz('1,234.5 JNZ')                  // '1234.5'
Amounts.add('1.25', '0.01')               // '1.26000000'
Amounts.compare('0.1', '0.10')            // 0
```

Never do float arithmetic on amounts; a `Double` with more than 15 significant digits is already wrong.

## Transfer

```kotlin
TxBuilder.transfer(from = from, to = to, amount = "1.25", nonce = nonce, data = "invoice 42", fee = "0.01")
```

`data` is an optional memo (≤ 256 bytes). With a memo the amount may be below 0.1, even `'0'`, which makes a
transfer a cheap on-chain note.

## Validator registration and exit

```kotlin
val validatorKey = client.info().nodeKey              // the node's public key
TxBuilder.registerValidator(from = from, validatorKey = validatorKey, nonce = nonce);       // fee 3, deposit 2000 (non-refundable)
TxBuilder.exitValidator(from = from, validatorKey = validatorKey, nonce = nonce + 1); // removed at the next epoch
```

The wallet that registers a node becomes its *validator wallet* (rewards go there; only it can create tokens).

## Tokens (JZT-1)

Token amounts are **integer base units** (`BigInteger` or digit string) — the token's `decimals` is display-only.

```kotlin
val create = TxBuilder.Token.create(from = from, symbol = "DEMO", name = "Demo", nonce = nonce, decimals = 2, cap = 1_000_000L, amount = 500_000L)
val tokenId = me.signTx(create).hash                            // for CREATE the tokenId IS the tx hash
TxBuilder.Token.mint(from = from, tokenId = tokenId, amount = 100L, nonce = nonce, recipient = recipient)
TxBuilder.Token.burn(from = from, tokenId = tokenId, amount = 100L, nonce = nonce)
TxBuilder.Token.setCap(from = from, tokenId = tokenId, cap = 2_000_000L, nonce = nonce)
TxBuilder.Token.transfer(from = from, tokenId = tokenId, amount = 12_345L, recipient = recipient, nonce = nonce)
```

## Waiting for finality

```kotlin
val view = Helpers.waitForFinality(rpc, signed.hash)       // FINAL TxView with blockHeight and receipt
val view = Helpers.sendAndWait(rpc, signed, submitVia = client) // submit + wait in one call
```

A transaction in a committed block is final; there is nothing to wait for beyond that. See
[json-rpc-and-subscriptions](json-rpc-and-subscriptions.md#finality) for how the wait works and its limits.

## Signing elsewhere (hardware wallet, KMS)

```kotlin
val unsigned = TxBuilder.transfer(…)
val digest   = unsigned.hash()                        // 32-byte hex to sign (RFC-6979 low-S DER expected)
val signed   = unsigned.withSignature(base64Der, publicKeyHex)
```
