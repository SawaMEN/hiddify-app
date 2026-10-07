package com.hiddify.hiddify.nativeprofile

import org.junit.Assert.*
import org.junit.Test

class NativeQrCodecTest {
    private fun roundTrip(text: String, inverted: Boolean = false) {
        val matrix = NativeQrCodec.encode(text)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width] xor inverted) {
                0xff000000.toInt()
            } else {
                0xffffffff.toInt()
            }
        }
        assertEquals(text, NativeQrCodec.decode(matrix.width, matrix.height, pixels))
    }

    @Test fun decodesUnicodeSubscription() =
        roundTrip("https://vpn.example:2096/sub?token=a%2Fb#Москва")

    @Test fun decodesProtocolLink() =
        roundTrip("vless://token@vpn.example:443?security=reality&type=tcp#VPN")

    @Test fun decodesInvertedImage() =
        roundTrip("hiddify://import/https://vpn.example/sub#VPN", inverted = true)

    @Test fun refusesContentThatDoesNotFitQr() {
        assertThrows(com.google.zxing.WriterException::class.java) {
            NativeQrCodec.encode("https://vpn.example/" + "a".repeat(10_000))
        }
    }
}
