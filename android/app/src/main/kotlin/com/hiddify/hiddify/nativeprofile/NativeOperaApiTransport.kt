package com.hiddify.hiddify.nativeprofile

import okhttp3.*
import java.net.InetAddress
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

/** SurfEasy's bootstrap endpoint uses a no-SNI/self-signed TLS endpoint (as in opera-proxy).
 * This exception is confined to this fixed API, never proxy traffic, DoH or subscription downloads.
 * No personal account, subscription headers or user data are sent to it. Proxy certificates stay verified.
 */
internal object NativeOperaApiTransport {
    const val HOST = "api2.sec-tunnel.com"
    fun builder(): OkHttpClient.Builder {
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException("Client certificates not supported")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty()) throw CertificateException("Missing Opera bootstrap certificate")
            }
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        return OkHttpClient.Builder()
            .sslSocketFactory(NoSniSocketFactory(context.socketFactory), trust)
            .hostnameVerifier { host, _ -> host == HOST }
            .connectionSpecs(listOf(ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).supportsTlsExtensions(false).build()))
            .protocols(listOf(Protocol.HTTP_1_1))
            .dns(OperaBootstrapDns())
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false)
            .addInterceptor { chain ->
                val url = chain.request().url
                check(url.isHttps && url.host == HOST) { "Unsupported Opera API target" }
                chain.proceed(chain.request())
            }
    }
}

/** Layer with the connected IP, not the logical HTTP host: JSSE/Conscrypt must not infer SNI.
 * ConnectionSpec also prevents OkHttp from adding SNI back on Android.
 */
internal class NoSniSocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        require(host == NativeOperaApiTransport.HOST) { "Unsupported Opera TLS target" }
        return delegate.createSocket(socket, socket.inetAddress.hostAddress, port, autoClose)
    }
    override fun createSocket(): Socket = throw UnsupportedOperationException("Connected socket required")
    override fun createSocket(host: String, port: Int): Socket = throw UnsupportedOperationException("Connected socket required")
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket = throw UnsupportedOperationException("Connected socket required")
    override fun createSocket(host: InetAddress, port: Int): Socket = throw UnsupportedOperationException("Connected socket required")
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket = throw UnsupportedOperationException("Connected socket required")
}

/** Bootstrap DoH is a separate, normally verified HTTPS client; no trust exception is shared. */
internal class OperaBootstrapDns(private val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).callTimeout(2, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).build()) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        require(hostname == NativeOperaApiTransport.HOST)
        for (resolver in listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/resolve")) {
            try {
                val request = Request.Builder().url("$resolver?name=$hostname&type=A").header("Accept", "application/dns-json").build()
                val addresses = http.newCall(request).execute().use { response ->
                    check(response.isSuccessful)
                    val body = response.body!!.byteStream().use { input ->
                        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                        while (true) { val n = input.read(buffer); if (n < 0) break
                            require(output.size() + n <= 65536); output.write(buffer, 0, n) }
                        output.toString("UTF-8")
                    }
                    val root = NativeJsonDocument.parse(body).asJsonObject
                    check(root.get("Status").asInt == 0)
                    root.getAsJsonArray("Answer").filter { it.asJsonObject.get("type").asInt == 1 }
                        .map { it.asJsonObject.get("data").asString }.filter(NativeOperaProfiles::isPublicLiteral)
                        .map(InetAddress::getByName)
                }
                if (addresses.isNotEmpty()) return addresses.distinct()
            } catch (_: Exception) { /* Try another verified resolver, then the system resolver. */ }
        }
        return Dns.SYSTEM.lookup(hostname).filter { NativeOperaProfiles.isPublicLiteral(it.hostAddress.orEmpty()) }
            .also { if (it.isEmpty()) throw java.net.UnknownHostException("Opera API has no public address") }
    }
}
