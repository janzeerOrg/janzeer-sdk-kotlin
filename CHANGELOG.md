# Changelog

All notable changes to `janzeer-sdk` are documented here ([Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
[SemVer](https://semver.org/)). The public API surface is pinned by `*/api/*.api` (binary-compatibility-validator).

## 0.1.8 — 2026-11-13

Verified against node **0.1.14** (API 1.1.0 / protocol 3.4.0). No API change.

- `SpecVersion.NODE` is **0.1.14** (node 0.1.14: supply endpoints `/api/v1/supply`, `/supply/total`, `/supply/circulating`, and circulating supply counted the standard way; API 1.1.0 / protocol 3.4.0 unchanged, rolling upgrade).

## 0.1.7 — 2026-11-11

Verified against node **0.1.13** (API 1.1.0 / protocol 3.4.0). No API change.

- `SpecVersion.NODE` is **0.1.13** (node hardening release after a full review: a peer is counted only under the key it proved in the handshake, a next-slot vote no longer discards the current slot, one reward transaction per block, format checks at intake, rate limit on `/rpc/ws`; API 1.1.0 / protocol 3.4.0 unchanged, rolling upgrade).

## 0.1.6 — 2026-11-11

Reliability fixes from a code review. Node pin unchanged (**0.1.12**, API 1.1.0 / protocol 3.4.0). No API change.

- **`close()` leaves a shared `OkHttpClient` alone.** `JanzeerClient.close()` and `JanzeerRpc.close()` shut down the dispatcher of a client passed in by the caller, so every other user of that client failed with "executor rejected" afterwards. Only a client the SDK created itself is released now (as `JanzeerRpcWs` already did).
- **`Helpers.waitForFinality` / `sendAndWait` over REST survives a dropped poll.** One transport error while polling (the transaction was already accepted) used to surface as a network error — which a caller can mistake for a failed send and resend. The REST branch now retries until the deadline, like the JSON-RPC branch.
- **WebSocket, resume after a reconnect.** A `newBlocks` stream now resumes right after the last block it delivered. It used to re-send the original `fromHeight`, so the node replayed every block again (thousands of duplicates on a long-lived stream), and a stream without `fromHeight` silently skipped the blocks committed during the outage. Blocks are delivered exactly once by height.
- **WebSocket, a failed resubscribe is retried.** A subscription whose re-creation failed (the socket dropped again, a call timed out) was removed for good with an `WsEvent.Error` as the only trace. It now stays registered and is re-created on the next attempt.

## 0.1.5 — 2026-11-09

Verified against node **0.1.12** (API 1.1.0 / protocol 3.4.0). No API change.

- `SpecVersion.NODE` is **0.1.12** (node critical fix F-058: GraalVM native nodes could not sync across a block carrying a token transaction, fixed in 0.1.12 with a build-time guard; API 1.1.0 / protocol 3.4.0 unchanged).

## 0.1.4 — 2026-11-01

Verified against node **0.1.11** (API 1.1.0 / protocol 3.4.0). No API change.

- `SpecVersion.NODE` is **0.1.11** (node maintenance release: the `jz` command-line client — itself built on this SDK — ships in the node bundle; `GET /info/version` now returns `version`, `apiVersion` and `protocolVersion` like `/info`).

## 0.1.3 — 2026-10-28

Verified against node **0.1.10** (API 1.1.0 / protocol 3.4.0).

- `SpecVersion.PROTOCOL` now says **3.4.0**, the wire protocol the node has run since 0.1.0 (0.1.2 shipped 3.2.0; informational only).
- The Dokka reference is published to GitHub Pages by its own `pages` workflow (dispatchable), apart from the Maven Central release.

## 0.1.2 — 2026-10-28

First release on Maven Central (`org.janzeer:janzeer-sdk-core`, `org.janzeer:janzeer-sdk-client`).

### Changed
- `SpecVersion`: node **0.1.10** (API 1.1.0, protocol 3.4.0), the mainnet release line; the SDK was verified against every node release from 0.1.0 to 0.1.10. No API change for SDK users.
- Documentation: there is no public testnet or faucet; wording says "private test network".
- New node rule worth knowing: a transaction's `timestamp` must be within 60 s ahead / 6 h behind the node's clock — sign right before you send.

## Versioning policy

- **MAJOR** — a change in the signed preimage, hashing, derivation or the REST envelope that makes older releases
  produce transactions a node rejects, or a binary-incompatible API change.
- **MINOR** — new node methods, fields or SDK features; existing code keeps working.
- **PATCH** — fixes and documentation.

Each release states the node / API / protocol versions it was tested against (`SpecVersion`).

## 0.1.1 — 2026-09-14

### Added
- `NodeInfo` (REST `info()`) now carries `networkId`, `genesisHash`, `chainSpecDigest`, `version`, `apiVersion`, `protocolVersion`, `syncStatus` and `faucet` (node 0.0.3); older nodes are tolerated (defaults). Read `info().networkId` and pass it as `networkId =` to the builders to work on the public testnet (`janzeer-testnet`).

### Changed
- `SpecVersion`: node `0.0.3`, protocol `3.2.0`.

## 0.1.0 — 2026-09-14

First release.

- `janzeer-sdk-core`: BIP39 mnemonics, Janzeer seed + `m/0/0/0` derivation, `Account` (mnemonic / private key /
  seed), canonical + EIP-55 addresses; `TxBuilder` for transfer, validator registration/exit and all five JZT-1
  token operations; `UnsignedTx` → `SignedTx`; `Amounts`; `Vault` (PBKDF2-SHA256/250k + AES-256-GCM).
- `janzeer-sdk-client`: `JanzeerClient` with typed models for every `/api/v1` endpoint (lossless numbers via
  `BigDecimal`/`BigInteger`, envelope unwrapping, 404 → empty/`null`, one automatic retry on 429); `JanzeerRpc`
  (HTTP, batches) and `JanzeerRpcWs` (WebSocket, auto-reconnect with re-subscription, `newBlocks` /
  `addressActivity`); `Helpers.waitForFinality / sendAndWait / nextNonce`; coroutine extensions; the
  `TxRejectedException` / `NonceMismatchException` family shared by both transports.
- Tested against node 0.0.2 / API 1.1.0 / protocol 3.1.0 / vectors v2.
