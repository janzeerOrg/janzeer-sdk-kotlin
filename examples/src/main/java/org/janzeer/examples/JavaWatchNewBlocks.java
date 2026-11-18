package org.janzeer.examples;

import kotlin.Unit;
import org.janzeer.sdk.rpc.BlockView;
import org.janzeer.sdk.rpc.JanzeerRpcWs;
import org.janzeer.sdk.rpc.NewBlocksParams;
import org.janzeer.sdk.rpc.Subscription;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Watch newly finalized blocks from plain Java over the WebSocket feed. */
public final class JavaWatchNewBlocks {
    public static void main(String[] args) throws InterruptedException {
        Map<String, String> env = System.getenv();
        String url = env.getOrDefault("JANZEER_WS_URL", "ws://localhost:7019/rpc/ws");
        try (JanzeerRpcWs ws = JanzeerRpcWs.connect(url)) {
            BlockView tip = ws.getTip();
            long fromHeight = env.containsKey("JANZEER_FROM_HEIGHT")
                    ? Long.parseLong(env.get("JANZEER_FROM_HEIGHT"))
                    : Math.max(1, tip.getHeight() - 2);
            int maxBlocks = env.containsKey("EXAMPLE_BLOCKS")
                    ? Integer.parseInt(env.get("EXAMPLE_BLOCKS"))
                    : 3;
            AtomicInteger count = new AtomicInteger(0);

            System.out.println("watching new blocks from height " + fromHeight + " (waiting for " + maxBlocks + " blocks)…");
            Subscription sub = ws.subscribeNewBlocks(new NewBlocksParams(fromHeight, false), b -> {
                String hashPrefix = b.getHash().substring(0, Math.min(12, b.getHash().length()));
                String producerPrefix = b.getProducer().substring(0, Math.min(12, b.getProducer().length()));
                System.out.println("block " + b.getHeight() + " " + hashPrefix + " producer " + producerPrefix + " txs " + b.getTransactionsCount());
                if (count.incrementAndGet() >= maxBlocks) {
                    System.out.println("received " + maxBlocks + " blocks, done.");
                }
                return Unit.INSTANCE;
            });

            long seconds = env.containsKey("EXAMPLE_SECONDS")
                    ? Long.parseLong(env.get("EXAMPLE_SECONDS"))
                    : 40;
            long start = System.currentTimeMillis();
            while (count.get() < maxBlocks && (System.currentTimeMillis() - start) < seconds * 1000) {
                Thread.sleep(200);
            }
            sub.unsubscribe();
        }
    }
}
