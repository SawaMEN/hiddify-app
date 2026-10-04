package com.hiddify.hiddify.bg

import android.net.DnsResolver
import android.os.Build
import android.os.CancellationSignal
import android.system.ErrnoException
import android.util.Log
import androidx.annotation.RequiresApi
import com.hiddify.core.libbox.ExchangeContext
import com.hiddify.core.libbox.LocalDNSTransport
import com.hiddify.hiddify.ktx.tryResume
import com.hiddify.hiddify.ktx.tryResumeWithException
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.suspendCoroutine

object LocalResolver : LocalDNSTransport {
    private const val TAG = "LocalResolver"
    private const val RCODE_SERVFAIL = 2
    private const val RCODE_NXDOMAIN = 3

    override fun raw(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun exchange(ctx: ExchangeContext, message: ByteArray) {
        try {
            runBlocking {
                val defaultNetwork = DefaultNetworkMonitor.require()
                suspendCoroutine { continuation ->
                    val signal = CancellationSignal()
                    runCatching { ctx.onCancel(signal::cancel) }
                    val callback = object : DnsResolver.Callback<ByteArray> {
                        override fun onAnswer(answer: ByteArray, rcode: Int) {
                            runCatching {
                                if (rcode == 0) ctx.rawSuccess(answer) else ctx.errorCode(rcode)
                            }.onFailure { Log.w(TAG, "failed to return raw DNS answer to core", it) }
                            continuation.tryResume(Unit)
                        }

                        override fun onError(error: DnsResolver.DnsException) {
                            when (val cause = error.cause) {
                                is ErrnoException -> {
                                    runCatching { ctx.errnoCode(cause.errno) }
                                        .onFailure { Log.w(TAG, "failed to return DNS errno to core", it) }
                                    continuation.tryResume(Unit)
                                }
                                else -> continuation.tryResumeWithException(error)
                            }
                        }
                    }
                    try {
                        DnsResolver.getInstance().rawQuery(
                            defaultNetwork,
                            message,
                            DnsResolver.FLAG_NO_RETRY,
                            Dispatchers.IO.asExecutor(),
                            signal,
                            callback,
                        )
                    } catch (e: Exception) {
                        signal.cancel()
                        continuation.tryResumeWithException(e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "raw DNS exchange failed", e)
            runCatching { ctx.errorCode(RCODE_SERVFAIL) }
                .onFailure { Log.w(TAG, "failed to return DNS failure to core", it) }
        }
    }

    override fun lookup(ctx: ExchangeContext, network: String, domain: String) {
        try {
            runBlocking {
                val defaultNetwork = DefaultNetworkMonitor.require()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    lookupModern(ctx, defaultNetwork, network, domain)
                } else {
                    val answer = try {
                        defaultNetwork.getAllByName(domain)
                    } catch (_: UnknownHostException) {
                        runCatching { ctx.errorCode(RCODE_NXDOMAIN) }
                        return@runBlocking
                    }
                    runCatching { ctx.success(answer.mapNotNull { it.hostAddress }.joinToString("\n")) }
                        .onFailure { Log.w(TAG, "failed to return DNS lookup result to core", it) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DNS lookup failed for $domain", e)
            runCatching { ctx.errorCode(RCODE_SERVFAIL) }
                .onFailure { Log.w(TAG, "failed to return DNS failure to core", it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun lookupModern(
        ctx: ExchangeContext,
        defaultNetwork: android.net.Network,
        network: String,
        domain: String,
    ) {
        suspendCoroutine { continuation ->
            val signal = CancellationSignal()
            runCatching { ctx.onCancel(signal::cancel) }
            val callback = object : DnsResolver.Callback<Collection<InetAddress>> {
                override fun onAnswer(answer: Collection<InetAddress>, rcode: Int) {
                    runCatching {
                        if (rcode == 0) {
                            ctx.success(answer.mapNotNull { it.hostAddress }.joinToString("\n"))
                        } else {
                            ctx.errorCode(rcode)
                        }
                    }.onFailure { Log.w(TAG, "failed to return DNS answer to core", it) }
                    continuation.tryResume(Unit)
                }

                override fun onError(error: DnsResolver.DnsException) {
                    when (val cause = error.cause) {
                        is ErrnoException -> {
                            runCatching { ctx.errnoCode(cause.errno) }
                                .onFailure { Log.w(TAG, "failed to return DNS errno to core", it) }
                            continuation.tryResume(Unit)
                        }
                        else -> continuation.tryResumeWithException(error)
                    }
                }
            }

            try {
                val type = when {
                    network.endsWith("4") -> DnsResolver.TYPE_A
                    network.endsWith("6") -> DnsResolver.TYPE_AAAA
                    else -> null
                }
                if (type != null) {
                    DnsResolver.getInstance().query(
                        defaultNetwork,
                        domain,
                        type,
                        DnsResolver.FLAG_NO_RETRY,
                        Dispatchers.IO.asExecutor(),
                        signal,
                        callback,
                    )
                } else {
                    DnsResolver.getInstance().query(
                        defaultNetwork,
                        domain,
                        DnsResolver.FLAG_NO_RETRY,
                        Dispatchers.IO.asExecutor(),
                        signal,
                        callback,
                    )
                }
            } catch (e: Exception) {
                signal.cancel()
                continuation.tryResumeWithException(e)
            }
        }
    }
}
