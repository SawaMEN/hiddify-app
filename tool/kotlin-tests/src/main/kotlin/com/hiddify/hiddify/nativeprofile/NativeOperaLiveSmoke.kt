package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonParser
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/** Opt-in live check, separate from deterministic unit tests. Never prints or uploads credentials. */
fun main() {
    // Proxy certificates are verified using their region hostname, while SNI stays disabled.
    // This property affects only this standalone test JVM, not the Android application's TLS clients.
    System.setProperty("jsse.enableSNIExtension", "false")
    val config = try { NativeOperaProfilesRepository().generate() }
    catch (error: NativeOperaUnavailableException) { error(error.message.orEmpty()) }
    val outbounds = JsonParser.parseString(config).asJsonObject.getAsJsonArray("outbounds")
    println("Opera registration and profile generation: OK (${outbounds.size()} servers)")
    var successes = 0
    val tls = SSLContext.getInstance("TLS").apply { init(null, null, null) }
    for (region in NativeOperaProfiles.regions.keys) {
        val hostname = "${region.lowercase()}0.sec-tunnel.com"
        val candidates = outbounds.filter { it.asJsonObject.getAsJsonObject("tls").get("server_name").asString == hostname }
        var connected = false
        for (candidate in candidates.take(4)) {
            val server = candidate.asJsonObject
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(server.get("server").asString, server.get("server_port").asInt), 5000)
                    (tls.socketFactory.createSocket(socket, hostname, server.get("server_port").asInt, true) as SSLSocket).use { secure ->
                        secure.soTimeout = 5000
                        secure.sslParameters = secure.sslParameters.apply {
                            endpointIdentificationAlgorithm = "HTTPS"
                            serverNames = emptyList()
                        }
                        secure.startHandshake()
                        val credentials = server.get("username").asString + ":" + server.get("password").asString
                        val auth = Base64.getEncoder().encodeToString(credentials.toByteArray())
                        val request = "CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\nProxy-Authorization: Basic $auth\r\n\r\n"
                        secure.outputStream.write(request.toByteArray()); secure.outputStream.flush()
                        val status = secure.inputStream.bufferedReader().readLine().orEmpty()
                        connected = Regex("HTTP/1\\.[01] 200(?: .*|$)").matches(status)
                    }
                }
            } catch (_: Exception) { /* Report only the region result, never raw requests or secrets. */ }
            if (connected) break
        }
        println("$region: verified TLS and authenticated TCP CONNECT ${if (connected) "OK" else "unavailable"}")
        if (connected) successes++
    }
    check(successes > 0) { "Opera API succeeded, but no region accepted a verified TCP tunnel" }
    println("Opera live smoke check: $successes / 3 regions available from this runner")
}
