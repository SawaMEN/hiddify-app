package com.hiddify.hiddify.nativecore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope

/** One scoped streaming request; close both directions and the transport on every exit path. */
internal suspend fun <S : Any, R : Any, T> withNativeResponseChannel(
    request: S,
    open: (CoroutineScope) -> Pair<SendChannel<S>, ReceiveChannel<R>>,
    cancel: () -> Unit,
    consume: suspend (ReceiveChannel<R>) -> T,
): T = coroutineScope {
    try {
        val (requests, responses) = open(this)
        try {
            requests.send(request)
            requests.close()
            consume(responses)
        } finally {
            responses.cancel()
            requests.close()
        }
    } finally { cancel() }
}

/** Normal EOF is an empty snapshot; transport failures must propagate to the retry/UI boundary. */
internal suspend fun <R : Any> ReceiveChannel<R>.nativeFirstOrNull(): R? {
    val first = receiveCatching()
    first.exceptionOrNull()?.let { throw it }
    return first.getOrNull()
}
