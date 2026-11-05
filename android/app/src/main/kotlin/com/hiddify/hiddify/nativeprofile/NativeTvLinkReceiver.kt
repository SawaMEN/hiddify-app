package com.hiddify.hiddify.nativeprofile

import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Collections

/**
 * Temporary, local-network-only TV receiver. It listens only while the import sheet is shown.
 * The six-digit capability, same-subnet check, numeric Host and ten-attempt limit prevent
 * remote requests and DNS rebinding. Submissions still require an explicit TV confirmation.
 */
internal class NativeTvLinkReceiver private constructor(
    private val listener: ServerSocket,
    private val localInterface: InterfaceAddress,
    private val code: String,
    private val onReceive: (String) -> Unit,
) : AutoCloseable {
    val url: String = "http://" + listener.inetAddress.hostAddress + ":" + listener.localPort + "/transfer/" + code
    @Volatile private var running = true
    @Volatile private var delivered = false
    private var wrongCodes = 0
    private val thread = Thread({ serve() }, "NativeTvLinkReceiver").apply {
        isDaemon = true
        start()
    }

    companion object {
        fun open(onReceive: (String) -> Unit): NativeTvLinkReceiver? {
            val candidates = Collections.list(NetworkInterface.getNetworkInterfaces() ?: return null)
                .filter { iface ->
                    runCatching { iface.isUp && !iface.isLoopback && !iface.isVirtual }.getOrDefault(false) &&
                        !iface.name.startsWith("tun") && !iface.name.startsWith("wg")
                }
                .flatMap { iface -> iface.interfaceAddresses.map { iface.name to it } }
                .filter { (_, addr) ->
                    addr.address is Inet4Address && NativeTvTransferPolicy.isPrivateIpv4(addr.address) &&
                        addr.networkPrefixLength in 8..32
                }
                .sortedBy { (name, _) ->
                    when {
                        name.startsWith("wlan") -> 0
                        name.startsWith("eth") || name.startsWith("en") -> 1
                        else -> 2
                    }
                }
            val secureRandom = SecureRandom()
            for ((_, address) in candidates) {
                val server = runCatching { ServerSocket(0, 8, address.address) }.getOrNull() ?: continue
                val code = secureRandom.nextInt(1_000_000).toString().padStart(6, '0')
                return NativeTvLinkReceiver(server, address, code, onReceive)
            }
            return null
        }
    }

    private fun serve() {
        while (running) {
            val socket = try { listener.accept() } catch (_: SocketException) { break }
                catch (_: Exception) { break }
            socket.use { client ->
                runCatching {
                    client.soTimeout = 5000
                    handle(client)
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        if (!running || !NativeTvTransferPolicy.sameSubnet(
                listener.inetAddress, socket.inetAddress, localInterface.networkPrefixLength.toInt())) {
            respond(socket, 403, "Not on the TV's local network")
            return
        }
        val input = BufferedInputStream(socket.getInputStream())
        val header = java.io.ByteArrayOutputStream()
        var terminator = 0
        while (header.size() <= NativeTvTransferPolicy.MAX_HEADER_BYTES && terminator < 4) {
            val next = input.read()
            if (next < 0) return
            header.write(next)
            terminator = if (next == "\r\n\r\n"[terminator].code) terminator + 1
                else if (next == '\r'.code) 1 else 0
        }
        if (terminator != 4 || header.size() > NativeTvTransferPolicy.MAX_HEADER_BYTES) {
            respond(socket, 413, "Request too large")
            return
        }
        val lines = header.toString(Charsets.ISO_8859_1.name()).split("\r\n")
        val first = lines.firstOrNull()?.split(" ") ?: return
        if (first.size != 3 || first[2] !in listOf("HTTP/1.0", "HTTP/1.1")) {
            respond(socket, 400, "Invalid request")
            return
        }
        val headers = lines.drop(1).mapNotNull {
            val colon = it.indexOf(':')
            if (colon < 1) null else it.substring(0, colon).trim().lowercase() to it.substring(colon + 1).trim()
        }.toMap()
        if (!NativeTvTransferPolicy.matchesHost(headers["host"], listener.inetAddress.hostAddress, listener.localPort)) {
            respond(socket, 403, "Invalid Host")
            return
        }
        if (wrongCodes >= NativeTvTransferPolicy.MAX_WRONG_CODES) {
            respond(socket, 403, "Pairing locked. Reopen the TV import page")
            return
        }
        if (first[1] != "/transfer/" + code) {
            wrongCodes++
            respond(socket, 404, "Invalid pairing code")
            return
        }
        if (first[0] == "GET") {
            respond(socket, 200, page(), true)
            return
        }
        if (first[0] != "POST") {
            respond(socket, 405, "Only GET and POST are accepted")
            return
        }
        if (delivered) {
            respond(socket, 409, "Link already sent. Confirm on TV")
            return
        }
        if (headers["content-type"]?.substringBefore(';')?.trim()?.lowercase() !=
                "application/x-www-form-urlencoded" || headers.containsKey("transfer-encoding")) {
            respond(socket, 415, "Expected a simple HTML form")
            return
        }
        val size = headers["content-length"]?.toIntOrNull()
        if (size == null || size < 1 || size > NativeTvTransferPolicy.MAX_FORM_BYTES) {
            respond(socket, 413, "Form too large")
            return
        }
        val bytes = ByteArray(size)
        var copied = 0
        while (copied < size) {
            val read = input.read(bytes, copied, size - copied)
            if (read <= 0) return
            copied += read
        }
        val data = String(bytes, Charsets.UTF_8)
        val form = data.split('&').firstOrNull { it.startsWith("link=") }?.substringAfter('=')
        val decoded = form?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrNull() }
        val link = decoded?.let(NativeTvTransferPolicy::validSubscriptionUrl)
        if (link == null) {
            respond(socket, 400, "Enter a valid subscription URL")
            return
        }
        if (!running) return
        delivered = true
        onReceive(link)
        respond(socket, 200, "Sent. Confirm import on your TV.")
    }

    private fun page(): String =
        "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>" +
        "<body><h1>Connect to your TV</h1><p>Paste your subscription URL and confirm import on the TV.</p>" +
        "<form method=\"POST\" action=\"/transfer/" + code + "\">" +
        "<label>Subscription URL <input name=\"link\" type=\"url\" required maxlength=\"4096\"></label>" +
        "<button type=\"submit\">Send to TV</button></form></body></html>"

    private fun respond(socket: Socket, status: Int, text: String, html: Boolean = false) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"
            405 -> "Method Not Allowed"; 409 -> "Conflict"; 413 -> "Payload Too Large"
            else -> "Unsupported Media Type"
        }
        val headers = "HTTP/1.1 " + status + " " + reason + "\r\n" +
            "Content-Type: " + (if (html) "text/html" else "text/plain") + "; charset=utf-8\r\n" +
            "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n" +
            "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'\r\n" +
            "Referrer-Policy: no-referrer\r\nConnection: close\r\n" +
            "Content-Length: " + bytes.size + "\r\n\r\n"
        socket.getOutputStream().apply {
            write(headers.toByteArray(Charsets.US_ASCII))
            write(bytes)
            flush()
        }
    }

    override fun close() {
        running = false
        runCatching { listener.close() }
    }
}
