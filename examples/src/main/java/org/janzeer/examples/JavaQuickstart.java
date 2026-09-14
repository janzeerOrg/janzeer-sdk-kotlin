package org.janzeer.examples;

import org.janzeer.sdk.Account;
import org.janzeer.sdk.Amounts;
import org.janzeer.sdk.client.Helpers;
import org.janzeer.sdk.client.JanzeerClient;
import org.janzeer.sdk.rpc.JanzeerRpc;
import org.janzeer.sdk.rpc.TxView;
import org.janzeer.sdk.tx.SignedTx;
import org.janzeer.sdk.tx.TxBuilder;

/** The quickstart from plain Java: every SDK call is a static or instance method with overloads for the defaults. */
public final class JavaQuickstart {
    public static void main(String[] args) {
        String node = System.getenv().getOrDefault("JANZEER_NODE_URL", "http://localhost:7019");
        String phrase = System.getenv().getOrDefault("JANZEER_E2E_MNEMONIC", "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about");
        String to = System.getenv().getOrDefault("JANZEER_E2E_RECIPIENT", "0x598b1301acef3baba6ce25e38dd17b723f7b98b1");
        try (JanzeerClient client = new JanzeerClient(node); JanzeerRpc rpc = new JanzeerRpc(System.getenv().getOrDefault("JANZEER_RPC_URL", node))) {
            Account account = Account.fromMnemonic(phrase);
            System.out.println("wallet " + account.getChecksumAddress() + " balance " + Amounts.formatJnz(client.balance(account.getAddress())));
            SignedTx tx = account.signTx(TxBuilder.transfer(account.getAddress(), to, "0.75", client.nonce(account.getAddress()), "hello from Java"));
            client.submit(tx);
            System.out.println("submitted " + tx.getHash());
            TxView fin = Helpers.waitForFinality(rpc, tx.getHash());
            System.out.println("FINAL in block " + fin.getBlockHeight() + " balance now " + Amounts.formatJnz(client.balance(account.getAddress())));
        }
    }
}
