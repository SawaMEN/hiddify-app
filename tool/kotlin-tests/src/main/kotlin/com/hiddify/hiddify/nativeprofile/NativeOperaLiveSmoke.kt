package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SNIHostName

/** Check every endpoint's actual HTTP/HTTPS traffic, never log or upload credentials. */
fun main() {
    val config = NativeOperaProfilesRepository(onDiagnostic = { region, reason -> println("Opera discovery $region: $reason") }).generate()
    val outbounds = JsonParser.parseString(config).asJsonObject.getAsJsonArray("outbounds")
    println("Opera registration: OK (${outbounds.size()} servers)")
    var httpsSuccesses = 0
    for (candidate in outbounds) {
        val server = candidate.asJsonObject
        val https = probeOpera(server, "www.gstatic.com", 443, "/generate_204", 204)
        val http = probeOpera(server, "captive.apple.com", 80, "/hotspot-detect.html", 200)
        println("${server.get("tag").asString}: HTTPS $https; HTTP $http")
        if (https == "OK") httpsSuccesses++
    }
    println("Opera end-to-end HTTPS: $httpsSuccesses / ${outbounds.size()} servers")
    check(httpsSuccesses == outbounds.size()) { "Some advertised Opera endpoints could not transfer HTTPS traffic" }
}

private fun probeOpera(server: JsonObject, host: String, port: Int, path: String, expected: Int): String {
    var stage = "connect"
    return try {
        val tls = SSLContext.getInstance("TLS").apply { init(null, null, null) }
        Socket().use { socket ->
            socket.connect(InetSocketAddress(server.get("server").asString, server.get("server_port").asInt), 5000)
            val certificateName = server.getAsJsonObject("tls").get("server_name").asString
            (tls.socketFactory.createSocket(socket, certificateName, server.get("server_port").asInt, true) as SSLSocket).use { secure ->
                secure.soTimeout = 5000
                secure.sslParameters = secure.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS"; serverNames = emptyList() }
                stage = "proxy TLS"; secure.startHandshake()
                val credentials = server.get("username").asString + ":" + server.get("password").asString
                val auth = Base64.getEncoder().encodeToString(credentials.toByteArray())
                val request = "CONNECT $host:$port HTTP/1.1\r\nHost: $host:$port\r\nProxy-Authorization: Basic $auth\r\n\r\n"
                secure.outputStream.write(request.toByteArray()); secure.outputStream.flush()
                stage = "proxy authorization"
                val connectReader = secure.inputStream.bufferedReader()
                val connectCode = connectReader.readLine().split(' ').getOrNull(1)?.toIntOrNull()
                while (!connectReader.readLine().isNullOrEmpty()) { }
                if (connectCode != 200) return "CONNECT $connectCode"
                stage = "destination HTTP"
                if (port == 443) {
                    (tls.socketFactory.createSocket(secure, host, port, false) as SSLSocket).use { target ->
                        target.soTimeout = 5000
                        target.sslParameters = target.sslParameters.apply {
                            endpointIdentificationAlgorithm = "HTTPS"; serverNames = listOf(SNIHostName(host))
                        }
                        stage = "destination TLS"; target.startHandshake(); stage = "destination HTTP"
                        target.outputStream.write("GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
                        target.outputStream.flush()
                        val code = target.inputStream.bufferedReader().readLine().split(' ').getOrNull(1)?.toIntOrNull()
                        if (code != expected) return "HTTP $code"
                    }
                } else {
                    secure.outputStream.write("GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
                    secure.outputStream.flush()
                    val code = secure.inputStream.bufferedReader().readLine().split(' ').getOrNull(1)?.toIntOrNull()
                    if (code != expected) return "HTTP $code"
                }
            }
        }
        "OK"
    } catch (error: Exception) { "$stage / ${error.javaClass.simpleName}" }
}
