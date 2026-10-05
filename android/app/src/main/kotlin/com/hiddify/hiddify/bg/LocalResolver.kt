package com.hiddify.hiddify.bg

import android.net.DnsResolver
import android.net.Network
import android.os.Build
import android.os.CancellationSignal
import android.system.ErrnoException
import android.util.Log
import androidx.annotation.RequiresApi
import com.hiddify.core.libbox.ExchangeContext
import com.hiddify.core.libbox.LocalDNSTransport
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object LocalResolver : LocalDNSTransport {
    private const val TAG = "LocalResolver"
    private const val SERVFAIL = 2
    private const val NXDOMAIN = 3
    private const val TIMEOUT_MS = 15_000L

    override fun raw(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    // Cancellation must release both the network wait and the DNS continuation.
    // Android does not invoke a DNS result callback for a cancelled query.
    private fun resolve(ctx: ExchangeContext, operation: suspend () -> (() -> Unit)) {
        val job = Job()
        val cancelled = AtomicBoolean(false)
        try {
            ctx.onCancel {
                cancelled.set(true)
                job.cancel()
            }
            val deliver = runBlocking(job) { withTimeout(TIMEOUT_MS) { operation() } }
            if (!cancelled.get()) deliver()
        } catch (_: TimeoutCancellationException) {
            if (!cancelled.get()) runCatching { ctx.errorCode(SERVFAIL) }
        } catch (_: CancellationException) {
            // The core no longer owns this request. Never send a second answer.
        } catch (_: UnknownHostException) {
            if (!cancelled.get()) runCatching { ctx.errorCode(NXDOMAIN) }
        } catch (e: Exception) {
            Log.w(TAG, "DNS request failed", e)
            if (!cancelled.get()) runCatching { ctx.errorCode(SERVFAIL) }
        } finally {
            job.cancel()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun <T : Any> query(start: (CancellationSignal, DnsResolver.Callback<T>) -> Unit): Pair<T, Int> =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            val finished = AtomicBoolean(false)
            continuation.invokeOnCancellation {
                finished.set(true)
                signal.cancel()
            }
            val callback = object : DnsResolver.Callback<T> {
                override fun onAnswer(answer: T, rcode: Int) {
                    if (finished.compareAndSet(false, true)) continuation.resume(answer to rcode)
                }
                override fun onError(error: DnsResolver.DnsException) {
                    if (finished.compareAndSet(false, true)) continuation.resumeWithException(error)
                }
            }
            try {
                if (continuation.isActive) start(signal, callback)
            } catch (e: Exception) {
                signal.cancel()
                if (finished.compareAndSet(false, true)) continuation.resumeWithException(e)
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun exchange(ctx: ExchangeContext, message: ByteArray) = resolve(ctx) {
        val network = DefaultNetworkMonitor.require()
        try {
            val (answer, rcode) = query<ByteArray> { signal, callback ->
                DnsResolver.getInstance().rawQuery(
                    network, message, DnsResolver.FLAG_NO_RETRY,
                    Dispatchers.IO.asExecutor(), signal, callback,
                )
            }
            ({ if (rcode == 0) ctx.rawSuccess(answer) else ctx.errorCode(rcode) })
        } catch (e: DnsResolver.DnsException) {
            val cause = e.cause
            if (cause is ErrnoException) ({ ctx.errnoCode(cause.errno) }) else throw e
        }
    }

    override fun lookup(ctx: ExchangeContext, network: String, domain: String) = resolve(ctx) {
        val defaultNetwork = DefaultNetworkMonitor.require()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            lookupModern(ctx, defaultNetwork, network, domain)
        } else {
            // Do not keep the gomobile caller waiting for an uncancellable legacy resolver.
            val answer = suspendCancellableCoroutine<List<InetAddress>> { continuation ->
                Dispatchers.IO.asExecutor().execute {
                    try {
                        continuation.resume(defaultNetwork.getAllByName(domain).toList())
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            }
            val filtered = answer.filter { address ->
                when {
                    network.endsWith("4") -> address is Inet4Address
                    network.endsWith("6") -> address is Inet6Address
                    else -> true
                }
            }
            ({ ctx.success(filtered.mapNotNull { it.hostAddress }.joinToString("\n")) })
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun lookupModern(ctx: ExchangeContext, network: Network, family: String, domain: String): () -> Unit {
        return try {
            val (answer, rcode) = query<Collection<InetAddress>> { signal, callback ->
                val type = when {
                    family.endsWith("4") -> DnsResolver.TYPE_A
                    family.endsWith("6") -> DnsResolver.TYPE_AAAA
                    else -> null
                }
                if (type == null) {
                    DnsResolver.getInstance().query(
                        network, domain, DnsResolver.FLAG_NO_RETRY,
                        Dispatchers.IO.asExecutor(), signal, callback,
                    )
                } else {
                    DnsResolver.getInstance().query(
                        network, domain, type, DnsResolver.FLAG_NO_RETRY,
                        Dispatchers.IO.asExecutor(), signal, callback,
                    )
                }
            }
            ({ if (rcode == 0) ctx.success(answer.mapNotNull { it.hostAddress }.joinToString("\n")) else ctx.errorCode(rcode) })
        } catch (e: DnsResolver.DnsException) {
            val cause = e.cause
            if (cause is ErrnoException) ({ ctx.errnoCode(cause.errno) }) else throw e
        }
    }
}
