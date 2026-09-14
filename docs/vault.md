# Vault — a secret at rest

`org.janzeer.sdk.vault` seals a mnemonic (or any string) under a password so a wallet can persist it in
the Android Keystore / a file, a file or a keychain entry.

```kotlin
import org.janzeer.sdk.vault.*

val blob = Vault.encrypt(mnemonic, password)            // VaultBlob(salt, iv, ct) — base64 fields, v = 1
prefs.putString("janzeer.vault", JsonUtil.stringify(blob.toMap()))

try { val phrase = Vault.decrypt(VaultBlob.fromMap(storedMap), password) }
catch (e: VaultException) { /* wrong password or tampered blob */ }
```

## Format (v1)

| Field | Value |
|---|---|
| key | PBKDF2-HMAC-SHA256, 250 000 rounds, 16-byte random `salt`, 32-byte key |
| cipher | AES-256-GCM, 12-byte random `iv`, `ct` = ciphertext ‖ 16-byte tag |
| encoding | all three fields base64 |

Blobs are interchangeable between this SDK, the Dart SDK and the Kotlin SDK (the conformance kit's
`vault-fixture.json` pins it) and with the wallets that used the older browser-side implementation.

## Notes

- Pure JavaScript (`@noble/ciphers`, `@noble/hashes`): works on plain-http pages where `crypto.subtle` is
  unavailable, and in Node without flags.
- 250 000 PBKDF2 rounds take ~100–300 ms on a laptop and more on a phone; run `decryptVault` off the UI thread
  (a Web Worker) if that matters to you.
- A vault protects a phrase at rest; it does not make a weak password strong. Pair it with the platform's
  biometric/keychain unlock where available.
