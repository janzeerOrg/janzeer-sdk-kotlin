package org.janzeer.examples

import org.janzeer.sdk.rpc.JanzeerRpcWs
import org.janzeer.sdk.rpc.NewBlocksParams

/** Watch an address over the WebSocket feed and stream new blocks (exits after EXAMPLE_SECONDS, default 40). */
fun main() {
    val env = System.getenv()
    val ws = JanzeerRpcWs.connect(env["JANZEER_WS_URL"] ?: "ws://localhost:7019/rpc/ws", onEvent = { println("[ws] $it") })
    val watch = env["JANZEER_E2E_RECIPIENT"] ?: "0x598b1301acef3baba6ce25e38dd17b723f7b98b1"
    val tip = ws.getTip()
    ws.subscribeNewBlocks(NewBlocksParams(fromHeight = (tip.height - 2).coerceAtLeast(1))) { println("block ${it.height} ${it.hash.take(12)} txs ${it.transactionsCount}") }
    ws.subscribeAddressActivity(listOf(watch)) { println("activity for $watch → ${it.transaction.type} ${it.transaction.hash.take(12)} ${it.transaction.amount} in block ${it.blockHeight}") }
    val seconds = env["EXAMPLE_SECONDS"]?.toLongOrNull() ?: 40
    println("listening… (this example exits after $seconds s)")
    Thread.sleep(seconds * 1000)
    ws.close()
}
