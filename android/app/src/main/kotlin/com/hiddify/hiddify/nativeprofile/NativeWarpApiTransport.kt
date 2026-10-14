package com.hiddify.hiddify.nativeprofile

import okhttp3.*
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocketFactory

/** A retry for blocked DNS/SNI, scoped to the registration API. Normal platform trust and
 * OkHttp hostname verification remain enabled. Never reuse Opera's certificate exception. */
internal object NativeWarpApiTransport {
    const val HOST = "api.cloudflareclient.com"
    fun fallback(http: OkHttpClient): OkHttpClient = http.newBuilder()
        .dns(WarpBootstrapDns())
        .followRedirects(false).followSslRedirects(false)
        .sslSocketFactory(WarpNoSniSocketFactory(http.sslSocketFactory), http.x509TrustManager!!)
        .connectionSpecs(listOf(ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).supportsTlsExtensions(false).build()))
        .protocols(listOf(Protocol.HTTP_1_1))
        .addInterceptor { chain ->
            check(chain.request().url.isHttps && chain.request().url.host == HOST)
            chain.proceed(chain.request())
        }.build()
}

internal class WarpNoSniSocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        require(host == NativeWarpApiTransport.HOST)
        return delegate.createSocket(socket, socket.inetAddress.hostAddress, port, autoClose)
    }
    override fun createSocket(): Socket = throw UnsupportedOperationException("Connected socket required")
    override fun createSocket(host: String, port: Int): Socket = throw UnsupportedOperationException()
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket = throw UnsupportedOperationException()
    override fun createSocket(host: InetAddress, port: Int): Socket = throw UnsupportedOperationException()
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket = throw UnsupportedOperationException()
}

internal class WarpBootstrapDns : Dns {
    private val resolver = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS).callTimeout(3, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    override fun lookup(hostname: String): List<InetAddress> {
        require(hostname == NativeWarpApiTransport.HOST)
        for (url in listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/resolve")) {
            try {
                val request = Request.Builder().url("$url?name=$hostname&type=A")
                    .header("Accept", "application/dns-json").build()
                val addresses = resolver.newCall(request).execute().use { response ->
                    check(response.isSuccessful)
                    val root = NativeJsonDocument.parse(response.body!!.byteStream().use(NativeProfileTransfer::readText)).asJsonObject
                    check(root.get("Status").asInt == 0)
                    root.getAsJsonArray("Answer").filter { it.asJsonObject.get("type").asInt == 1 }
                        .map { it.asJsonObject.get("data").asString }
                        .filter { Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}").matches(it) }
                        .map(InetAddress::getByName).filterIsInstance<Inet4Address>()
                        .filter { !it.isAnyLocalAddress && !it.isLoopbackAddress && !it.isSiteLocalAddress && !it.isLinkLocalAddress }
                }
                if (addresses.isNotEmpty()) return addresses.distinct()
            } catch (_: Exception) { /* The next resolver has its own verified TLS connection. */ }
        }
        return Dns.SYSTEM.lookup(hostname).filterIsInstance<Inet4Address>()
            .also { if (it.isEmpty()) throw UnknownHostException("WARP API has no IPv4 address") }
    }
}
