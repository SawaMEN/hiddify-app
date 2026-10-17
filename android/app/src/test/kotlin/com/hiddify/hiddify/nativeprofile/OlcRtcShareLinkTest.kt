package com.hiddify.hiddify.nativeprofile

import org.junit.Assert.*
import org.junit.Test

class OlcRtcShareLinkTest {
    private val key = "d823fa01cb3e0609b67322f7cf984c4ee2e4ce2e294936fc24ef38c9e59f4799"

    @Test fun parsesVideoParamsAndCommentWithoutConfusingKeyForFragment() {
        val link = OlcRtcShareLink.parse("olcrtc://wbstream?vp8channel<vp8-fps=60&vp8-batch=64>@room-1#" + key + "$" + "RU / node")
        assertEquals("wbstream", link.provider)
        assertEquals("vp8channel", link.transport)
        assertEquals("room-1", link.roomId)
        assertEquals(key, link.encryptionKey)
        assertEquals(mapOf("vp8-fps" to "60", "vp8-batch" to "64"), link.transportParameters)
        assertEquals("RU / node", link.comment)
        assertFalse(link.toString().contains(key))
    }

    @Test fun acceptsRoomUrlsAndNonUriCharactersInMetadata() {
        val link = OlcRtcShareLink.parse("olcrtc://jitsi?datachannel@https://meet.example.org/room#" + key + "$" + "Test / node")
        assertEquals("https://meet.example.org/room", link.roomId)
        assertEquals("Test / node", link.comment)
    }

    @Test fun rejectsSecretWithInvalidSizeAndBadProvider() {
        assertThrows(IllegalArgumentException::class.java) { OlcRtcShareLink.parse("olcrtc://jitsi?datachannel@room#1234") }
        assertThrows(IllegalArgumentException::class.java) { OlcRtcShareLink.parse("olcrtc://fake?datachannel@room#" + key) }
    }

    @Test fun rejectsUnsupportedTransportParametersAndDuplicates() {
        assertThrows(IllegalArgumentException::class.java) { OlcRtcShareLink.parse("olcrtc://jitsi?datachannel<fps=30>@room#" + key) }
        assertThrows(IllegalArgumentException::class.java) { OlcRtcShareLink.parse("olcrtc://jitsi?vp8channel<vp8-fps=30&vp8-fps=20>@room#" + key) }
        assertThrows(IllegalArgumentException::class.java) { OlcRtcShareLink.parse("olcrtc://jitsi?vp8channel<vp8-fps=-1>@room#" + key) }
    }

    @Test fun metadataDoesNotChangeFingerprintButCredentialDoes() {
        val one = OlcRtcShareLink.parse("olcrtc://jitsi?datachannel@room#" + key + "\$One")
        val two = OlcRtcShareLink.parse("olcrtc://jitsi?datachannel@room#" + key + "\$Two")
        val three = OlcRtcShareLink.parse("olcrtc://jitsi?datachannel@room#" + "a".repeat(64))
        assertEquals(one.fingerprint(), two.fingerprint())
        assertNotEquals(one.fingerprint(), three.fingerprint())
    }

    @Test fun rejectsMultilineAndOversizedRoom() {
        assertThrows(IllegalArgumentException::class.java) { OlcRtcShareLink.parse("olcrtc://jitsi?datachannel@room#" + key + "\nother") }
        assertThrows(IllegalArgumentException::class.java) { OlcRtcShareLink.parse("olcrtc://jitsi?datachannel@" + "a".repeat(2049) + "#" + key) }
    }
}
