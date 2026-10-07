package com.hiddify.hiddify.privacy

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Rebuild the binary XML string pool without changing node/resource-map indexes. */
object BinaryXml {
    fun rewrite(xml: ByteArray, transform: (String) -> String): ByteArray {
        val input = ByteBuffer.wrap(xml).order(ByteOrder.LITTLE_ENDIAN)
        require(xml.size >= 8 && input.getShort(0).toInt() == 3 && input.getInt(4) == xml.size) {
            "Invalid Android binary XML"
        }
        var start = 8
        while (start + 28 <= xml.size && input.getShort(start).toInt() and 0xffff != 1) {
            val size = input.getInt(start + 4)
            require(size >= 8 && size <= xml.size - start) { "Invalid XML chunk" }
            start += size
        }
        require(start + 28 <= xml.size) { "Missing XML string pool" }
        val header = input.getShort(start + 2).toInt() and 0xffff
        val size = input.getInt(start + 4)
        val count = input.getInt(start + 8)
        val styles = input.getInt(start + 12)
        val flags = input.getInt(start + 16)
        val dataOffset = input.getInt(start + 20)
        require(header == 28 && styles == 0 && count in 0..100000 && size >= 28 && size <= xml.size - start &&
            dataOffset >= 28 + count * 4 && dataOffset <= size) { "Unsupported or corrupt XML string pool" }
        val data = start + dataOffset
        val utf8 = flags and 0x100 != 0
        val charset = if (utf8) Charsets.UTF_8 else Charsets.UTF_16LE
        val pool = ByteArrayOutputStream()
        val offsets = IntArray(count)
        for (i in 0 until count) {
            val offset = input.getInt(start + header + i * 4)
            require(offset >= 0 && offset < start + size - data) { "Invalid string offset" }
            val str = input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            str.limit(start + size)
            str.position(data + offset)
            val chars = readLength(str, utf8)
            val bytes = if (utf8) readLength(str, true) else Math.multiplyExact(chars, 2)
            require(bytes >= 0 && bytes <= str.remaining() - (if (utf8) 1 else 2)) { "Invalid XML string" }
            val encoded = ByteArray(bytes).also { str.get(it) }
            val value = transform(String(encoded, charset))
            offsets[i] = pool.size()
            val mapped = value.toByteArray(charset)
            writeLength(pool, value.length, utf8)
            if (utf8) writeLength(pool, mapped.size, true)
            pool.write(mapped, 0, mapped.size)
            pool.write(0)
            if (!utf8) pool.write(0)
        }
        while (pool.size() and 3 != 0) pool.write(0)
        val newSize = Math.addExact(header + count * 4, pool.size())
        val result = ByteArray(Math.addExact(xml.size - size, newSize))
        xml.copyInto(result, 0, 0, start + header)
        val output = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        output.putInt(4, result.size)
        output.putInt(start + 4, newSize)
        output.putInt(start + 16, flags and 1.inv())
        output.putInt(start + 20, header + count * 4)
        output.putInt(start + 24, 0)
        offsets.forEachIndexed { i, offset -> output.putInt(start + header + i * 4, offset) }
        pool.toByteArray().copyInto(result, start + header + count * 4)
        xml.copyInto(result, start + newSize, start + size, xml.size)
        return result
    }

    /** Replace only application launcher icon references, preserving component icons. */
    fun setApplicationIcon(manifest: ByteArray, icon: Int): ByteArray {
        val strings = mutableListOf<String>()
        rewrite(manifest) { value -> strings.add(value); value }
        val result = manifest.copyOf()
        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        var resources = IntArray(0)
        var offset = 8
        while (offset < result.size) {
            require(result.size - offset >= 8) { "Truncated manifest chunk" }
            val type = buffer.getShort(offset).toInt() and 0xffff
            val header = buffer.getShort(offset + 2).toInt() and 0xffff
            val size = buffer.getInt(offset + 4)
            require(header >= 8 && size >= header && size <= result.size - offset) { "Invalid manifest chunk" }
            if (type == 0x180) {
                require((size - header) % 4 == 0) { "Invalid resource map" }
                resources = IntArray((size - header) / 4) { buffer.getInt(offset + header + it * 4) }
            } else if (type == 0x102) {
                require(header == 16 && size >= 36) { "Invalid element" }
                val name = buffer.getInt(offset + 20)
                require(name in strings.indices) { "Invalid element name" }
                if (strings[name] == "application") {
                    val start = offset + 16 + (buffer.getShort(offset + 24).toInt() and 0xffff)
                    val stride = buffer.getShort(offset + 26).toInt() and 0xffff
                    val count = buffer.getShort(offset + 28).toInt() and 0xffff
                    require(stride >= 20 && start >= offset + 36 && start <= offset + size &&
                        count <= (offset + size - start) / stride) { "Invalid attributes" }
                    for (i in 0 until count) {
                        val attribute = start + i * stride
                        val index = buffer.getInt(attribute + 4)
                        if (index !in resources.indices) continue
                        if (resources[index] == 0x01010002 || resources[index] == 0x0101052c) {
                            buffer.putInt(attribute + 8, -1)
                            buffer.put(attribute + 15, 1.toByte())
                            buffer.putInt(attribute + 16, icon)
                        }
                    }
                }
            }
            offset += size
        }
        return result
    }

    /** Rename the fixed-width resource package for runtime getIdentifier(newPackage). */
    fun renameResourcePackage(table: ByteArray, oldPackage: String, newPackage: String): ByteArray {
        require(newPackage.length < 128) { "Resource package too long" }
        val result = table.copyOf()
        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        require(table.size >= 12 && buffer.getShort(0).toInt() == 2 && buffer.getInt(4) == table.size) {
            "Invalid Android resource table"
        }
        var offset = buffer.getShort(2).toInt() and 0xffff
        while (offset < table.size) {
            require(offset >= 12 && table.size - offset >= 8) { "Invalid resource chunk" }
            val type = buffer.getShort(offset).toInt() and 0xffff
            val header = buffer.getShort(offset + 2).toInt() and 0xffff
            val size = buffer.getInt(offset + 4)
            require(header >= 8 && size >= header && size <= table.size - offset) { "Invalid resource chunk size" }
            if (type == 0x200) {
                require(header >= 268) { "Invalid resource package" }
                var length = 0
                while (length < 128 && buffer.getShort(offset + 12 + length * 2).toInt() != 0) length++
                val name = String(result, offset + 12, length * 2, Charsets.UTF_16LE)
                if (name == oldPackage) {
                    result.fill(0, offset + 12, offset + 268)
                    newPackage.toByteArray(Charsets.UTF_16LE).copyInto(result, offset + 12)
                }
            }
            offset += size
        }
        return result
    }

    private fun readLength(buffer: ByteBuffer, utf8: Boolean): Int {
        val n = if (utf8) buffer.get().toInt() and 0xff else buffer.getShort().toInt() and 0xffff
        val mask = if (utf8) 0x80 else 0x8000
        if (n and mask == 0) return n
        val low = if (utf8) buffer.get().toInt() and 0xff else buffer.getShort().toInt() and 0xffff
        return ((n and mask.inv()) shl (if (utf8) 8 else 16)) or low
    }

    private fun writeLength(output: ByteArrayOutputStream, n: Int, utf8: Boolean) {
        if (utf8) {
            require(n <= 0x7fff) { "XML UTF-8 string too long" }
            if (n > 0x7f) output.write((n shr 8) or 0x80)
            output.write(n and 0xff)
        } else {
            if (n > 0x7fff) {
                val high = (n shr 16) or 0x8000
                output.write(high and 0xff)
                output.write(high shr 8)
            }
            output.write(n and 0xff)
            output.write((n shr 8) and 0xff)
        }
    }
}
