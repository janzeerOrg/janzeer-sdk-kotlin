# janzeer-sdk (Kotlin / JVM / Android)

Official Kotlin SDK for the [Janzeer](https://janzeer.org) blockchain — a stake-free, permissionless Layer-1
with instant finality. Wallet keys and signing, transaction builders, a typed REST client, JSON-RPC over HTTP
and WebSocket subscriptions. Java-friendly (static factories, overloads, no Kotlin-only types in signatures),
Android-safe (BouncyCastle lightweight API, minSdk 26). Non-custodial by construction: keys never leave your
process, the node only verifies.

[![Maven Central](https://img.shields.io/maven-central/v/org.janzeer/janzeer-sdk-client)](https://central.sonatype.com/namespace/org.janzeer)
![license](https://img.shields.io/badge/license-Apache--2.0-blue)

```kotlin
dependencies {
    implementation("org.janzeer:janzeer-sdk-client:0.1.1")   // REST + JSON-RPC + WS (pulls janzeer-sdk-core)
    // implementation("org.janzeer:janzeer-sdk-core:0.1.1")  // keys, signing, builders only (no networking)
}
```

> **Not on Maven Central yet.** The artifacts follow shortly after the mainnet launch. Until then, build them into your
> local Maven repository (JDK 17) and add `mavenLocal()` to your repositories:
>
> ```bash
> git clone https://github.com/janzeerorg/janzeer-sdk-kotlin && cd janzeer-sdk-kotlin
> ./gradlew publishToMavenLocal
> ```

## 60-second quickstart

```kotlin
import org.janzeer.sdk.*; import org.janzeer.sdk.client.*; import org.janzeer.sdk.rpc.*; import org.janzeer.sdk.tx.*

val client = JanzeerClient("http://localhost:7019")            // REST  (/api/v1)
val rpc    = JanzeerRpc("http://localhost:7019")               // JSON-RPC (/rpc)
val me     = Account.fromMnemonic("abandon abandon … about")   // 12/24-word BIP39 phrase

println("${me.checksumAddress} ${Amounts.formatJnz(client.balance(me.address))}")

val tx = me.signTx(TxBuilder.transfer(
    from = me.address, to = "0x598b1301acef3baba6ce25e38dd17b723f7b98b1",
    amount = "1.25", data = "hello", nonce = client.nonce(me.address),
))
client.submit(tx)                                              // HTTP 201, or a typed exception
val fin = Helpers.waitForFinality(rpc, tx.hash)                // one or two 15-second slots
println("final in block ${fin.blockHeight}")
```

From Java: `Account.fromMnemonic(phrase)`, `TxBuilder.transfer(from, to, "1.25", nonce, "hello")`,
`client.submit(tx)`, `Helpers.waitForFinality(rpc, tx.getHash())` — see
[`examples/src/main/java`](examples/src/main/java/org/janzeer/examples/JavaQuickstart.java).

That is the whole integration: derive → nonce → build → sign → submit → wait. A committed block is final
(2f+1 BFT commit), so there is no confirmation count.

## What is in the box

| Area | Entry points | Guide |
|---|---|---|
| Keys & addresses | `Mnemonic`, `Account`, `Address` | [wallet-and-keys](docs/wallet-and-keys.md) |
| Transactions | `TxBuilder.transfer / registerValidator / exitValidator / Token.*` → `UnsignedTx` → `SignedTx` | [sending-transactions](docs/sending-transactions.md) |
| Amounts | `Amounts.toScaledLong / fromScaledLong / formatJnz / parseJnz / add / sub / compare` | [sending-transactions](docs/sending-transactions.md#amounts) |
| REST | `JanzeerClient`: balances, nonces, transactions, blocks, validators, tokens (typed, paged) | [reading-chain-data](docs/reading-chain-data.md) |
| JSON-RPC | `JanzeerRpc` (HTTP, batches) and `JanzeerRpcWs` (WebSocket + `newBlocks` / `addressActivity`) | [json-rpc-and-subscriptions](docs/json-rpc-and-subscriptions.md) |
| Flows | `Helpers.waitForFinality / sendAndWait / nextNonce`; `suspend` variants in `client.coroutines` | [json-rpc-and-subscriptions](docs/json-rpc-and-subscriptions.md#finality) |
| Errors | `TxRejectedException`, `NonceMismatchException`, `RateLimitedException`, `RpcException`… | [errors](docs/errors.md) |
| Vault | `Vault.encrypt / decrypt` (PBKDF2 + AES-GCM) | [vault](docs/vault.md) |
| Android / Java | threading, R8, Java usage | [android](docs/android.md), [java](docs/java.md) |

Modules: `janzeer-sdk-core` (crypto, builders, vault — depends only on BouncyCastle) and `janzeer-sdk-client`
(REST, RPC, WS — OkHttp + kotlinx-serialization). Blocking API; coroutine extensions on top.

## Platforms

JVM 11+ (built with JDK 17), Android API 26+. Kotlin 2.x or plain Java.

## Compatibility

| SDK | Node | REST envelope `version` | Wire protocol | Vectors |
|---|---|---|---|---|
| 0.1.x | 0.1.0 | 1.1.0 | 3.4.0 | v2 |

`SpecVersion` carries these values; the e2e test checks them against the node on the first call.

## Examples

`examples/`: [quickstart transfer](examples/src/main/kotlin/org/janzeer/examples/QuickstartTransfer.kt),
address-activity subscription, token create + transfer, validator registration (dry run), vault round trip,
and the Java quickstart. Run one with
`./gradlew :examples:run -PmainClass=org.janzeer.examples.QuickstartTransferKt` against the network in
`JANZEER_NODE_URL`.

## Conformance

Every release reproduces the shared [conformance vectors](docs/conformance.md) byte-for-byte and passes the
14-step end-to-end flow against a real network. `./gradlew test` runs the vectors;
`./gradlew :janzeer-sdk-client:e2eTest -Djanzeer.node.url=…` the flow.

## Documentation

Guides in `docs/` (start with [wallet-and-keys](docs/wallet-and-keys.md)) · API reference: `./gradlew dokkaGenerate`
→ `build/dokka/html` · chain documentation (developer guide, API reference, yellow paper): https://janzeer.org

## License

Apache-2.0. See [SECURITY.md](SECURITY.md) for reporting vulnerabilities.
