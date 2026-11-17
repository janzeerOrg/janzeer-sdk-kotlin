package org.janzeer.examples;

import kotlin.Unit;
import org.janzeer.sdk.rpc.JanzeerRpcWs;
import org.janzeer.sdk.rpc.NewBlocksParams;
import java.util.concurrent.atomic.AtomicInteger;

public class JavaWatchNewBlocks {
    private static final String JANZEER_WS_URL = System.getenv().getOrDefault("JANZEER_WS_URL", "ws://localhost:7019/rpc/ws");
    private static final int EXAMPLE_BLOCKS = 3;

    public static void main(String[] args) throws Exception {
        try (JanzeerRpcWs ws = JanzeerRpcWs.connect(JANZEER_WS_URL)) {
            long tip = ws.getTip();
            long fromHeight = tip - 2;
            AtomicInteger count = new AtomicInteger(0);

            System.out.println("Subscribing from height: " + fromHeight);

            ws.subscribeNewBlocks(new NewBlocksParams(fromHeight, false), block -> {
                System.out.printf("Height: %d | Hash: %s... | Producer: %s | Txs: %d%n",
                        block.getHeight(),
                        block.getHash().substring(0, 12),
                        block.getProducer(),
                        block.getTxCount());

                if (count.incrementAndGet() >= EXAMPLE_BLOCKS) {
                    ws.unsubscribe();
                }
                return Unit.INSTANCE;
            });

            Thread.sleep(40000);
        }
    }
}
