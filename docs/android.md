# Android

`janzeer-sdk-core` and `janzeer-sdk-client` are plain JVM libraries (Java 11 bytecode) that work on Android API 26+.

## Threading

Every client call is blocking. Never call the SDK on the main thread:

```kotlin
viewModelScope.launch {
    val balance = withContext(Dispatchers.IO) { client.balance(address) }
    // or the ready-made suspend extensions:
    val nonce = client.nonceAsync(address)
    val fin = waitForFinalityAsync(rpc, tx.hash)
}
```

`org.janzeer.sdk.client.coroutines` has `balanceAsync`, `nonceAsync`, `submitAsync`, `sendAsync`,
`getAccountAsync`, `waitForFinalityAsync`, `sendAndWaitAsync`; anything else is `withContext(Dispatchers.IO) { … }`.
WebSocket notifications arrive on OkHttp's reader thread — post to the main thread before touching views.

Key derivation (PBKDF2 × 2048 + secp256k1) takes tens of milliseconds; `Vault.decrypt` (PBKDF2 × 250 000) up to
a second or two on a low-end phone — run both on `Dispatchers.Default`.

## Crypto providers

The SDK uses BouncyCastle's *lightweight* API (`org.bouncycastle.crypto.*`) and never registers a JCA provider,
so it does not conflict with the BouncyCastle build Android ships, and AES-GCM comes from `javax.crypto`. No
`Security.insertProviderAt` is needed or recommended.

## R8 / ProGuard

kotlinx-serialization and OkHttp ship their own consumer rules. The SDK reflects over nothing of its own; the
BouncyCastle classes it uses are referenced directly. If you shrink aggressively and see `NoSuchMethodError`
inside `org.bouncycastle`, add:

```
-keep class org.bouncycastle.crypto.** { *; }
-dontwarn org.bouncycastle.**
```

## Storing keys

Encrypt the mnemonic with the [vault](vault.md) and keep the blob in `EncryptedSharedPreferences` or a file in
the app's private storage; hold the decrypted `Account` in memory only while the wallet is unlocked. Consider
`BiometricPrompt` to gate the password.

## Network

Use one `OkHttpClient` for the app and pass it to `JanzeerClient(url, httpClient)` / `JanzeerRpc(url, httpClient)`
(the SDK only closes clients it created). Emulators reach the host at `http://10.0.2.2:7019`.
