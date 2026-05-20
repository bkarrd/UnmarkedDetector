package com.example.unmarkeddetector.util

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private object DirectExecutor : Executor {
    override fun execute(command: Runnable) {
        command.run()
    }
}

suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            runCatching { get() }
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it) }
        },
        DirectExecutor
    )
    continuation.invokeOnCancellation { cancel(true) }
}
