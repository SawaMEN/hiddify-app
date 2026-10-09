package com.hiddify.hiddify.privacy

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BinaryXmlTest {
    private fun fixture(utf8: Boolean): ByteArray {
        val strings = listOf("app.hiddify.com", "app.hiddify.com.privacy.files", "com.hiddify.hiddify.MainActivity", "Hiddify")
        val data = ByteArrayOutputStream()
        val offsets = strings.map { value ->
            val offset = data.size()
            val encoded = value.toByteArray(if (utf8) Charsets.UTF_8 else Charsets.UTF_16LE)
            data.write(value.length)
            data.write(if (utf8) encoded.size else 0)
            data.write(encoded)
            data.write(0)
            if (!utf8) data.write(0)
            offset
        }
        while (data.size() % 4 != 0) data.write(0)
        val poolSize = 28 + offsets.size * 4 + data.size()
        return ByteBuffer.allocate(8 + poolSize + 8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(3).putShort(8).putInt(capacity())
            putShort(1).putShort(28).putInt(poolSize).putInt(offsets.size).putInt(0)
            putInt(if (utf8) 256 else 0).putInt(28 + offsets.size * 4).putInt(0)
            offsets.forEach { putInt(it) }; put(data.toByteArray()); putInt(0x12345678).putInt(8)
        }.array()
    }

    @Test fun binaryXmlFixturesPreserveResourceAndManifestStructure() {
        for (utf8 in listOf(false, true)) {
            val source = fixture(utf8)
            var changes = 0
            val label = "Настройки сети " + "я".repeat(150)
            val renamed = BinaryXml.rewrite(source) { value ->
                when {
                    value == "Hiddify" -> { changes++; label }
                    value.startsWith("app.hiddify.com") -> { changes++; value.replace("app.hiddify.com", "app.random.packageidentity") }
                    else -> value
                }
            }
            check(changes == 3) { "Package, authority and label should change" }
            var seen = 0
            BinaryXml.rewrite(renamed) { value ->
                if (value in listOf("app.random.packageidentity", "app.random.packageidentity.privacy.files", label,
                    "com.hiddify.hiddify.MainActivity")) seen++
                value
            }
            check(seen == 4) { "String pool offsets/encoding corrupted" }
            val result = ByteBuffer.wrap(renamed).order(ByteOrder.LITTLE_ENDIAN)
            check(result.getInt(4) == renamed.size && result.getInt(renamed.size - 8) == 0x12345678) { "Node chunk changed" }
            check(source.contentEquals(BinaryXml.rewrite(source) { it })) { "Identity rewrite must be exact" }
            ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN).putInt(36, Int.MAX_VALUE)
            try { BinaryXml.rewrite(source) { it }; error("Bad offset accepted") } catch (_: IllegalArgumentException) { }
        }
        val table = ByteBuffer.allocate(12 + 288).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(2).putShort(12).putInt(capacity()).putInt(1)
            putShort(0x200).putShort(288).putInt(288).putInt(0x7f)
            put("app.hiddify.com".toByteArray(Charsets.UTF_16LE))
        }
        val renamedTable = BinaryXml.renameResourcePackage(table.array(), "app.hiddify.com", "app.random.identity")
        val resourceName = String(renamedTable, 24, "app.random.identity".length * 2, Charsets.UTF_16LE)
        check(resourceName == "app.random.identity" && renamedTable.size == table.capacity()) { "Resource package was not renamed" }
        val pool = BinaryXml.rewrite(fixture(true)) { value -> when (value) {
            "app.hiddify.com" -> "icon"; "app.hiddify.com.privacy.files" -> "roundIcon"
            "com.hiddify.hiddify.MainActivity" -> "activity"; else -> "application"
        } }
        val icons = ByteBuffer.allocate(pool.size - 8 + 24 + 76 * 2).order(ByteOrder.LITTLE_ENDIAN)
        icons.put(pool, 0, pool.size - 8)
        icons.putShort(0x180).putShort(8).putInt(24).putInt(0x01010002).putInt(0x0101052c).putInt(0).putInt(0)
        val applicationOffset = icons.position()
        for (name in listOf(3, 2)) {
            icons.putShort(0x102).putShort(16).putInt(76).putInt(1).putInt(-1)
            icons.putInt(-1).putInt(name).putShort(20).putShort(20).putShort(2).putShort(0).putShort(0).putShort(0)
            for (attribute in listOf(0, 1)) icons.putInt(-1).putInt(attribute).putInt(-1).putShort(8).put(0).put(1).putInt(0x7f010001)
        }
        icons.putInt(4, icons.capacity())
        val changed = ByteBuffer.wrap(BinaryXml.setApplicationIcon(icons.array(), 0x7f010002)).order(ByteOrder.LITTLE_ENDIAN)
        check(changed.getInt(applicationOffset + 52) == 0x7f010002 && changed.getInt(applicationOffset + 72) == 0x7f010002 &&
            changed.getInt(applicationOffset + 76 + 52) == 0x7f010001) { "Only application icons should change" }
    }
}
