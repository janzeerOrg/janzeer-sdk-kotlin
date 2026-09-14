# Changelog

All notable changes to `janzeer-sdk` are documented here ([Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
[SemVer](https://semver.org/)). The public API surface is pinned by `*/api/*.api` (binary-compatibility-validator).

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
