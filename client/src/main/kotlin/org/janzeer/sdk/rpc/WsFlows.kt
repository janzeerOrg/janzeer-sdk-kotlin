package org.janzeer.sdk.rpc

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [Flow] adapters for [JanzeerRpcWs] live subscriptions.
 *
 * Each flow is **cold**: subscribing happens upon collection, and the underlying
 * [Subscription] is cancelled via [Subscription.unsubscribe] when the collector
 * is cancelled or completes. The owning [JanzeerRpcWs] socket is never closed by
 * these flows; the caller owns its lifecycle.
 *
 * Backpressure: events use [trySendBlocking], which back-pressures OkHttp's reader
 * thread instead of silently dropping events when the collector is slower than the
 * incoming event rate or the [callbackFlow] buffer is full.
 */

/**
 * Cold [Flow] of newly finalized blocks.
 *
 * Collecting subscribes via [JanzeerRpcWs.subscribeNewBlocks]; cancelling the
 * collector calls [Subscription.unsubscribe] in [awaitClose].
 *
 * If the initial subscribe call throws (for example if the socket is not connected
 * or the node returns an RPC error), the flow fails immediately with the SDK's
 * exception instead of hanging.
 *
 * Events are delivered with [trySendBlocking] so they are never silently dropped.
 *
 * @param params subscription parameters, defaults to [NewBlocksParams].
 */
public fun JanzeerRpcWs.newBlocksFlow(params: NewBlocksParams = NewBlocksParams()): Flow<BlockView> =
    callbackFlow {
        val sub = subscribeNewBlocks(params) { block ->
            trySendBlocking(block)
        }
        awaitClose { sub.unsubscribe() }
    }

/**
 * Cold [Flow] of final address activity involving any address in [addresses].
 *
 * Collecting subscribes via [JanzeerRpcWs.subscribeAddressActivity]; cancelling
 * the collector calls [Subscription.unsubscribe] in [awaitClose].
 *
 * If the initial subscribe call throws (for example on invalid parameters or RPC
 * error), the flow fails with that SDK exception instead of hanging.
 *
 * Events are delivered with [trySendBlocking] so they are never silently dropped.
 *
 * @param addresses 1 to 1000 addresses to watch.
 */
public fun JanzeerRpcWs.addressActivityFlow(addresses: List<String>): Flow<AddressActivityEvent> =
    callbackFlow {
        val sub = subscribeAddressActivity(addresses) { event ->
            trySendBlocking(event)
        }
        awaitClose { sub.unsubscribe() }
    }
