package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.security.KeyStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class NativeOperaApiTransportTest {
    /** Real TLS, not an interceptor response: reproduces the upstream no-SNI/self-signed endpoint.
     * The disposable keypair is generated for this local test and never used or shipped by the app.
     */
    @Test fun bootstrapPerformsNoSniDigestAndCookieFlowOverRealTls() {
        val directory = Files.createTempDirectory("opera-api-test").toFile()
        val store = File(directory, "fixture.p12")
        val password = "local-test-fixture"
        val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/keytool").path,
            "-genkeypair", "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "1", "-dname", "CN=Local Opera Fixture", "-storetype", "PKCS12",
            "-keystore", store.path, "-storepass", password, "-keypass", password)
            .redirectErrorStream(true).redirectOutput(File(directory, "keytool.log")).start()
        assertTrue(process.waitFor(20, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue())
        val keystore = KeyStore.getInstance("PKCS12").apply { store.inputStream().use { load(it, password.toCharArray()) } }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keystore, password.toCharArray()) }
        val tls = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
        val server = tls.serverSocketFactory.createServerSocket(0, 50, InetAddress.getLoopbackAddress()) as SSLServerSocket
        server.soTimeout = 10000
        val executor = Executors.newSingleThreadExecutor()
        val captures = executor.submit<List<String>> {
            val paths = mutableListOf<String>()
            // Five RPCs, each challenged once with a fresh connection.
            repeat(10) {
                (server.accept() as SSLSocket).use { socket ->
                    socket.soTimeout = 5000
                    socket.startHandshake()
                    assertTrue((socket.session as ExtendedSSLSession).requestedServerNames.isEmpty())
                    val reader = socket.inputStream.bufferedReader(Charsets.UTF_8)
                    val path = reader.readLine().split(' ')[1]
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = reader.readLine() ?: error("Missing headers")
                        if (line.isEmpty()) break
                        val separator = line.indexOf(':')
                        headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
                    }
                    repeat(headers.getValue("content-length").toInt()) { reader.read() }
                    val authorized = headers["authorization"]?.startsWith("Digest ") == true
                    val body = if (!authorized) "" else when (path) {
                        "/v4/register_subscriber" -> """{"return_code":{"0":"OK"},"data":null}"""
                        "/v4/register_device" -> {
                            assertTrue(headers["cookie"].orEmpty().contains("opera_session=fixture"))
                            """{"return_code":{"0":"OK"},"data":{"device_id":"fixture","device_password":"temporary-access"}}"""
                        }
                        else -> {
                            assertEquals("/v4/discover", path)
                            assertTrue(headers["cookie"].orEmpty().contains("opera_session=fixture"))
                            """{"return_code":{"0":"OK"},"data":{"ips":[{"ip":"77.111.244.26","ports":[443]}]}}"""
                        }
                    }
                    if (authorized) paths += path
                    val challenge = if (!authorized) "WWW-Authenticate: Digest realm=\"api\", nonce=\"fixture\", qop=\"auth\"\r\n" else ""
                    val cookie = if (authorized && path == "/v4/register_subscriber") "Set-Cookie: opera_session=fixture; Path=/v4; Secure; HttpOnly\r\n" else ""
                    val response = "HTTP/1.1 ${if (authorized) "200 OK" else "401 Unauthorized"}\r\n$challenge$cookie" +
                        "Content-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
                    socket.outputStream.write(response.toByteArray()); socket.outputStream.flush()
                }
            }
            paths
        }
        try {
            val redirectToFixture = Interceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(chain.request().url.newBuilder().port(server.localPort).build()).build())
            }
            val repository = NativeOperaProfilesRepository(redirectToFixture, bootstrapDns = object : Dns { override fun lookup(hostname: String) = listOf(InetAddress.getLoopbackAddress()) })
            val outbounds = JsonParser.parseString(repository.generate()).asJsonObject.getAsJsonArray("outbounds")
            assertEquals(3, outbounds.size())
            assertEquals(listOf("/v4/register_subscriber", "/v4/register_device", "/v4/discover", "/v4/discover", "/v4/discover"), captures.get(10, TimeUnit.SECONDS))
            assertTrue(outbounds.all { it.asJsonObject.getAsJsonObject("tls").get("disable_sni").asBoolean })
            assertTrue(outbounds.none { it.asJsonObject.getAsJsonObject("tls").has("insecure") })
        } finally {
            server.close(); executor.shutdownNow(); directory.deleteRecursively()
        }
    }

    @Test fun apiTlsExceptionCannotBeUsedForAnotherHost() {
        val client = NativeOperaApiTransport.builder().build()
        try {
            assertThrows(IllegalStateException::class.java) {
                client.newCall(Request.Builder().url("https://example.invalid/v4/register_device").build()).execute().close()
            }
        } finally { client.connectionPool.evictAll() }
    }

    @Test fun bootstrapDnsUsesVerifiedResolverAndRejectsPrivateAnswers() {
        val requestHosts = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requestHosts += chain.request().url.host
            val json = if (requestHosts.size == 1) """{"Status":0,"Answer":[{"type":1,"data":"127.0.0.1"}]}"""
                else """{"Status":0,"Answer":[{"type":1,"data":"77.111.244.26"}]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()
        assertEquals("77.111.244.26", OperaBootstrapDns(http).lookup(NativeOperaApiTransport.HOST).single().hostAddress)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), requestHosts)
    }
}
