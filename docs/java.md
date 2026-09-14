# Using the SDK from Java

The SDK is written in Kotlin with Java callers in mind: every public function is a static method on an `object`
(`Amounts`, `Address`, `Mnemonic`, `TxBuilder`, `Helpers`, `Vault`) or an instance method, defaults are exposed
as overloads (`@JvmOverloads`), and no `suspend` function or Kotlin-only type appears in a signature.

```java
import org.janzeer.sdk.*; import org.janzeer.sdk.client.*; import org.janzeer.sdk.rpc.*; import org.janzeer.sdk.tx.*;

try (JanzeerClient client = new JanzeerClient("http://localhost:7019"); JanzeerRpc rpc = new JanzeerRpc("http://localhost:7019")) {
    Account me = Account.fromMnemonic(phrase);
    long nonce = client.nonce(me.getAddress());
    SignedTx tx = me.signTx(TxBuilder.transfer(me.getAddress(), to, "1.25", nonce, "hello"));   // fee/timestamp default
    client.submit(tx);
    TxView fin = Helpers.waitForFinality(rpc, tx.getHash());
    System.out.println(fin.getBlockHeight() + " " + Amounts.formatJnz(client.balance(me.getAddress())));
} catch (NonceMismatchException e) {
    System.out.println("resend with nonce " + e.getExpected());
} catch (TxRejectedException e) {
    System.out.println("rejected: " + e.getType() + " " + e.getMessage());
}
```

Notes:

- Kotlin `object` members are `static` (`Amounts.toScaledLong(...)`, `TxBuilder.Token.create(...)`).
- Properties are getters (`tx.getHash()`, `account.getChecksumAddress()`, `fin.getBlockHeight()`).
- `TxBuilder.transfer` overloads: `(from, to, amount, nonce)`, `(from, to, amount, nonce, data)`,
  `(…, data, fee)`, `(…, fee, timestamp)`, `(…, timestamp, networkId)`.
- Amount parameters are `Object`: pass a `String` (`"1.25"`), `BigDecimal`, `BigInteger` or `Long`.
- Lambdas: `ws.subscribeNewBlocks(new NewBlocksParams(100L, true), block -> { …; return Unit.INSTANCE; })`.
- Exceptions are unchecked (`RuntimeException` subclasses); see [errors](errors.md).

The complete example: `examples/src/main/java/org/janzeer/examples/JavaQuickstart.java`.
