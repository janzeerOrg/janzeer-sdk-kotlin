package org.janzeer.examples

import org.janzeer.sdk.rpc.JanzeerRpcWs
import org.janzeer.sdk.rpc.NewBlocksParams
import java.util.concurrent.atomic.AtomicInteger

/** Watch new blocks over the WebSocket feed (exits after EXAMPLE_SECONDS, default 40). */
fun main() {
    val env = System.getenv()
    val ws = JanzeerRpcWs.connect(
        env["JANZEER_WS_URL"] ?: "ws://localhost:7019/rpc/ws",
        onEvent = { println("[ws] $it") },
    )
    val tip = ws.getTip()
    val fromHeight = (tip.height - 2).coerceAtLeast(1)
    val seen = AtomicInteger(0)
    val maxBlocks = env["EXAMPLE_MAX_BLOCKS"]?.toIntOrNull() ?: 5

    ws.subscribeNewBlocks(NewBlocksParams(fromHeight = fromHeight, includeTransactions = false)) { b ->
        val n = seen.incrementAndGet()
        println("block ${b.height} ${b.hash.take(12)} producer=${b.producer} txs=${b.transactionsCount}")
        if (n >= maxBlocks) {
            // main loop will exit on deadline/block count check
        }
    }

    val seconds = env["EXAMPLE_SECONDS"]?.toLongOrNull() ?: 40
    println("listening for new blocks… (exits after $seconds s or $maxBlocks blocks)")
    val deadline = System.currentTimeMillis() + seconds * 1000
    while (System.currentTimeMillis() < deadline && seen.get() < maxBlocks) {
        Thread.sleep(250)
    }
    ws.close()
}
