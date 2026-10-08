package com.hiddify.hiddify.nativeprofile

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter

/** Offline QR processing; no Flutter engine or network service is involved. */
object NativeQrCodec {
    fun encode(text: String): BitMatrix =
        QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            768,
            768,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 4),
        )

    fun decode(width: Int, height: Int, pixels: IntArray): String {
        require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong()) { "Invalid QR image dimensions" }
        // Bitmap pixels are ARGB; RGBLuminanceSource ignores alpha. Composite transparent
        // PNG backgrounds over white instead of interpreting them as black modules.
        val opaque = IntArray(pixels.size) { index ->
            val pixel = pixels[index]
            val alpha = pixel ushr 24
            if (alpha == 255) pixel else {
                fun channel(shift: Int) = (((pixel ushr shift) and 255) * alpha + 255 * (255 - alpha) + 127) / 255
                (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
            }
        }
        val source = RGBLuminanceSource(width, height, opaque)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8")
        val reader = QRCodeReader()
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (_: com.google.zxing.NotFoundException) {
            // Some clients use a light code on a dark background.
            reader.reset()
            reader.decode(BinaryBitmap(HybridBinarizer(source.invert())), hints).text
        } finally {
            reader.reset()
        }
    }
}
