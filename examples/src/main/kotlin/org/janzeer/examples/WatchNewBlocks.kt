package org.janzeer.examples

import org.janzeer.sdk.rpc.JanzeerRpcWs
import org.janzeer.sdk.rpc.NewBlocksParams
import java.util.concurrent.atomic.AtomicInteger

/** Watch newly finalized blocks over the WebSocket feed (exits after 3 blocks or EXAMPLE_SECONDS, default 40). */
fun main() {
    val env = System.getenv()
    val ws = JanzeerRpcWs.connect(env["JANZEER_WS_URL"] ?: "ws://localhost:7019/rpc/ws", onEvent = { println("[ws] $it") })
    val tip = ws.getTip()
    val fromHeight = env["JANZEER_FROM_HEIGHT"]?.toLongOrNull() ?: (tip.height - 2).coerceAtLeast(1)
    val maxBlocks = env["EXAMPLE_BLOCKS"]?.toIntOrNull() ?: 3
    val count = AtomicInteger(0)

    println("watching new blocks from height $fromHeight (waiting for $maxBlocks blocks)…")
    val sub = ws.subscribeNewBlocks(NewBlocksParams(fromHeight = fromHeight, includeTransactions = false)) { b ->
        println("block ${b.height} ${b.hash.take(12)} producer ${b.producer.take(12)} txs ${b.transactionsCount}")
        if (count.incrementAndGet() >= maxBlocks) {
            println("received $maxBlocks blocks, done.")
        }
    }

    val seconds = env["EXAMPLE_SECONDS"]?.toLongOrNull() ?: 40
    val start = System.currentTimeMillis()
    while (count.get() < maxBlocks && (System.currentTimeMillis() - start) < seconds * 1000) {
        Thread.sleep(200)
    }
    sub.unsubscribe()
    ws.close()
}
