package com.hiddify.hiddify.privacy

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Persistent software signing identity for the on-device repacked APK.
 *
 * AndroidKeyStore private keys are intentionally non-exportable and some JCA providers used by
 * apksig reject AndroidKeyStorePrivateKey when apksig asks for a generic SHA256withRSA Signature.
 * Keeping this app-local RSA key in noBackupFilesDir gives apksig a normal PKCS#8 RSA private key,
 * while the Android application sandbox still protects it from other applications.
 */
internal object SoftwareSigningIdentity {
    data class Material(val privateKey: PrivateKey, val certificate: X509Certificate)

    private const val DIRECTORY = "private-package-signer"
    private const val PRIVATE_KEY = "signing-key.pk8"
    private const val CERTIFICATE = "signing-cert.der"
    private const val MAX_KEY_BYTES = 16 * 1024
    private const val MAX_CERT_BYTES = 16 * 1024
    private val lock = Any()
    @Volatile private var cached: Material? = null

    fun loadOrCreate(context: Context): Material = cached ?: synchronized(lock) {
        cached ?: load(context)?.also { validate(it) }?.also { cached = it }
            ?: create(context).also { validate(it); cached = it }
    }

    private fun load(context: Context): Material? {
        val directory = File(context.noBackupFilesDir, DIRECTORY)
        val keyFile = File(directory, PRIVATE_KEY)
        val certFile = File(directory, CERTIFICATE)
        if (!keyFile.exists() && !certFile.exists()) return null
        check(keyFile.isFile && certFile.isFile) {
            "Private APK signing identity is incomplete. Remove the private copy and create it again."
        }
        val keyBytes = keyFile.readLimited(MAX_KEY_BYTES, "private signing key")
        val certBytes = certFile.readLimited(MAX_CERT_BYTES, "signing certificate")
        return try {
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
            val certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(certBytes.inputStream()) as X509Certificate
            Material(privateKey, certificate)
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun create(context: Context): Material {
        val pair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom())
        }.generateKeyPair()
        val certificate = createCertificate(pair.private, pair.public.encoded)
        val material = Material(pair.private, certificate)
        val directory = File(context.noBackupFilesDir, DIRECTORY)
        check(directory.isDirectory || directory.mkdirs()) { "Unable to create private APK signing directory" }
        writeAtomically(File(directory, PRIVATE_KEY), pair.private.encoded)
        writeAtomically(File(directory, CERTIFICATE), certificate.encoded)
        return material
    }

    private fun validate(material: Material) {
        check(material.privateKey.algorithm.equals("RSA", ignoreCase = true)) { "Private APK signing key is not RSA" }
        check(material.certificate.publicKey.algorithm.equals("RSA", ignoreCase = true)) { "Private APK signing certificate is not RSA" }
        val challenge = ByteArray(32).also(SecureRandom()::nextBytes)
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(material.privateKey)
            update(challenge)
        }.sign()
        val valid = Signature.getInstance("SHA256withRSA").apply {
            initVerify(material.certificate)
            update(challenge)
        }.verify(signature)
        check(valid) { "Private APK signing key does not match its certificate" }
    }

    private fun createCertificate(privateKey: PrivateKey, publicKeyInfo: ByteArray): X509Certificate {
        val now = System.currentTimeMillis()
        val serial = BigInteger(128, SecureRandom()).abs().let { if (it.signum() == 0) BigInteger.ONE else it }
        val name = sequence(set(sequence(oid("2.5.4.3"), utf8("Application"))))
        val algorithm = algorithmIdentifier("1.2.840.113549.1.1.11") // sha256WithRSAEncryption
        val tbs = sequence(
            explicit(0, integer(BigInteger.valueOf(2))), // X.509 v3
            integer(serial),
            algorithm,
            name,
            sequence(time(Date(now - 86_400_000L)), time(Date(now + 20L * 365 * 86_400_000L))),
            name,
            publicKeyInfo,
        )
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(tbs)
        }.sign()
        val encoded = sequence(tbs, algorithm, bitString(signature))
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(encoded.inputStream()) as X509Certificate
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            temporary.outputStream().use { output ->
                output.write(bytes)
                output.flush()
                (output as? java.io.FileOutputStream)?.fd?.sync()
            }
            if (target.exists()) check(target.delete()) { "Unable to replace ${target.name}" }
            check(temporary.renameTo(target)) { "Unable to save ${target.name}" }
            check(target.setReadable(false, false) && target.setReadable(true, true)) { "Unable to protect ${target.name}" }
            check(target.setWritable(false, false) && target.setWritable(true, true)) { "Unable to protect ${target.name}" }
        } finally {
            temporary.delete()
        }
    }

    private fun File.readLimited(limit: Int, description: String): ByteArray {
        check(length() in 1..limit.toLong()) { "Invalid $description" }
        return inputStream().use { it.readBytes() }.also { check(it.size <= limit) { "$description is too large" } }
    }

    private fun algorithmIdentifier(value: String) = sequence(oid(value), der(0x05, byteArrayOf()))
    private fun sequence(vararg values: ByteArray) = der(0x30, concat(*values))
    private fun set(vararg values: ByteArray) = der(0x31, concat(*values))
    private fun explicit(tag: Int, value: ByteArray) = der(0xa0 + tag, value)
    private fun utf8(value: String) = der(0x0c, value.toByteArray(Charsets.UTF_8))
    private fun bitString(value: ByteArray) = der(0x03, byteArrayOf(0) + value)

    private fun time(value: Date): ByteArray {
        val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = value }
        val year = calendar.get(java.util.Calendar.YEAR)
        val utcTime = year in 1950..2049
        val pattern = if (utcTime) "yyMMddHHmmss'Z'" else "yyyyMMddHHmmss'Z'"
        val format = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return der(if (utcTime) 0x17 else 0x18, format.format(value).toByteArray(Charsets.US_ASCII))
    }

    private fun integer(value: BigInteger): ByteArray = der(0x02, value.toByteArray())

    private fun oid(value: String): ByteArray {
        val parts = value.split('.').map(String::toLong)
        require(parts.size >= 2 && parts[0] in 0..2 && parts[1] >= 0) { "Invalid OID" }
        val out = ByteArrayOutputStream()
        writeBase128(out, parts[0] * 40 + parts[1])
        for (index in 2 until parts.size) writeBase128(out, parts[index])
        return der(0x06, out.toByteArray())
    }

    private fun writeBase128(out: ByteArrayOutputStream, value: Long) {
        require(value >= 0) { "Invalid OID component" }
        var remaining = value
        val encoded = ByteArray(10)
        var index = encoded.size
        encoded[--index] = (remaining and 0x7f).toByte()
        remaining = remaining ushr 7
        while (remaining != 0L) {
            encoded[--index] = ((remaining and 0x7f) or 0x80).toByte()
            remaining = remaining ushr 7
        }
        out.write(encoded, index, encoded.size - index)
    }

    private fun der(tag: Int, body: ByteArray): ByteArray = ByteArrayOutputStream().use { out ->
        out.write(tag)
        writeLength(out, body.size)
        out.write(body)
        out.toByteArray()
    }

    private fun writeLength(out: ByteArrayOutputStream, size: Int) {
        if (size < 128) {
            out.write(size)
            return
        }
        var remaining = size
        val encoded = ByteArray(4)
        var index = encoded.size
        while (remaining > 0) {
            encoded[--index] = (remaining and 0xff).toByte()
            remaining = remaining ushr 8
        }
        out.write(0x80 or (encoded.size - index))
        out.write(encoded, index, encoded.size - index)
    }

    private fun concat(vararg values: ByteArray): ByteArray = ByteArrayOutputStream().use { out ->
        values.forEach(out::write)
        out.toByteArray()
    }
}
