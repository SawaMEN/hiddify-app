package com.hiddify.hiddify.privacy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.function.UnaryOperator;

/** Rebuilds the manifest string pool, the same mechanism used by Magisk AppMigration.
 * Supports both UTF-8 and UTF-16 pools; node and resource-map indexes stay unchanged. */
public final class BinaryXml {
    private BinaryXml() {}
    public static byte[] rewrite(byte[] xml, UnaryOperator<String> transform) {
        ByteBuffer input = ByteBuffer.wrap(xml).order(ByteOrder.LITTLE_ENDIAN);
        if (xml.length < 8 || input.getShort(0) != 3 || input.getInt(4) != xml.length)
            throw new IllegalArgumentException("Invalid Android binary XML");
        int start = 8;
        while (start + 28 <= xml.length && (input.getShort(start) & 0xffff) != 1) {
            int size = input.getInt(start + 4);
            if (size < 8 || size > xml.length - start) throw new IllegalArgumentException("Invalid XML chunk");
            start += size;
        }
        if (start + 28 > xml.length) throw new IllegalArgumentException("Missing XML string pool");
        int header = input.getShort(start + 2) & 0xffff;
        int size = input.getInt(start + 4), count = input.getInt(start + 8);
        int styles = input.getInt(start + 12), flags = input.getInt(start + 16);
        int data = start + input.getInt(start + 20);
        if (header != 28 || styles != 0 || count < 0 || count > 100000 ||
                size < 28 || size > xml.length - start || data < start + 28 + count * 4 || data > start + size)
            throw new IllegalArgumentException("Unsupported or corrupt XML string pool");
        boolean utf8 = (flags & 0x100) != 0;
        ByteArrayOutputStream pool = new ByteArrayOutputStream();
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            int off = input.getInt(start + header + i * 4);
            if (off < 0 || off >= start + size - data) throw new IllegalArgumentException("Invalid string offset");
            ByteBuffer str = input.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            str.position(data + off);
            str.limit(start + size);
            int chars = readLength(str, utf8);
            int bytes = utf8 ? readLength(str, true) : Math.multiplyExact(chars, 2);
            if (bytes > str.remaining() - (utf8 ? 1 : 2)) throw new IllegalArgumentException("Invalid XML string");
            byte[] encoded = new byte[bytes]; str.get(encoded);
            String value = transform.apply(new String(encoded, utf8 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16LE));
            offsets[i] = pool.size();
            encoded = value.getBytes(utf8 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16LE);
            writeLength(pool, value.length(), utf8);
            if (utf8) writeLength(pool, encoded.length, true);
            pool.write(encoded, 0, encoded.length);
            pool.write(0); if (!utf8) pool.write(0);
        }
        while ((pool.size() & 3) != 0) pool.write(0);
        int newSize = header + count * 4 + pool.size();
        byte[] result = new byte[xml.length + newSize - size];
        System.arraycopy(xml, 0, result, 0, start + header);
        ByteBuffer output = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(4, result.length);
        output.putInt(start + 4, newSize);
        output.putInt(start + 16, flags & ~1); // mapped strings need not remain sorted
        output.putInt(start + 20, header + count * 4);
        output.putInt(start + 24, 0);
        for (int i = 0; i < count; i++) output.putInt(start + header + i * 4, offsets[i]);
        System.arraycopy(pool.toByteArray(), 0, result, start + header + count * 4, pool.size());
        System.arraycopy(xml, start + size, result, start + newSize, xml.length - start - size);
        return result;
    }
    /** Replaces only application launcher icon references, preserving all component icons. */
    public static byte[] setApplicationIcon(byte[] manifest, int icon) {
        java.util.ArrayList<String> strings = new java.util.ArrayList<>();
        rewrite(manifest, value -> { strings.add(value); return value; });
        byte[] result = manifest.clone();
        ByteBuffer b = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
        int[] resources = new int[0];
        for (int offset = 8; offset < result.length;) {
            if (result.length - offset < 8) throw new IllegalArgumentException("Truncated manifest chunk");
            int type = b.getShort(offset) & 0xffff, header = b.getShort(offset + 2) & 0xffff, size = b.getInt(offset + 4);
            if (size < header || header < 8 || size > result.length - offset) throw new IllegalArgumentException("Invalid manifest chunk");
            if (type == 0x180) {
                if ((size - header) % 4 != 0) throw new IllegalArgumentException("Invalid resource map");
                resources = new int[(size - header) / 4];
                for (int i = 0; i < resources.length; i++) resources[i] = b.getInt(offset + header + i * 4);
            } else if (type == 0x102) {
                if (header != 16 || size < 36) throw new IllegalArgumentException("Invalid element");
                int name = b.getInt(offset + 20);
                if (name < 0 || name >= strings.size()) throw new IllegalArgumentException("Invalid element name");
                if (strings.get(name).equals("application")) {
                    int start = offset + 16 + (b.getShort(offset + 24) & 0xffff);
                    int stride = b.getShort(offset + 26) & 0xffff, count = b.getShort(offset + 28) & 0xffff;
                    if (stride < 20 || start < offset + 36 || count > (offset + size - start) / stride) throw new IllegalArgumentException("Invalid attributes");
                    for (int i = 0; i < count; i++) {
                        int attribute = start + i * stride, index = b.getInt(attribute + 4);
                        if (index < 0 || index >= resources.length) continue;
                        if (resources[index] == 0x01010002 || resources[index] == 0x0101052c) {
                            b.putInt(attribute + 8, -1);
                            b.put(attribute + 15, (byte) 1); // TYPE_REFERENCE
                            b.putInt(attribute + 16, icon);
                        }
                    }
                }
            }
            offset += size;
        }
        return result;
    }

    /** Rename the fixed-width resource package so runtime getIdentifier(newPackage) still works. */
    public static byte[] renameResourcePackage(byte[] table, String oldPackage, String newPackage) {
        if (newPackage.length() >= 128) throw new IllegalArgumentException("Resource package too long");
        byte[] result = table.clone();
        ByteBuffer b = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
        if (table.length < 12 || b.getShort(0) != 2 || b.getInt(4) != table.length)
            throw new IllegalArgumentException("Invalid Android resource table");
        int offset = b.getShort(2) & 0xffff;
        while (offset < table.length) {
            if (offset < 12 || table.length - offset < 8) throw new IllegalArgumentException("Invalid resource chunk");
            int type = b.getShort(offset) & 0xffff;
            int header = b.getShort(offset + 2) & 0xffff;
            int size = b.getInt(offset + 4);
            if (size < header || header < 8 || size > table.length - offset)
                throw new IllegalArgumentException("Invalid resource chunk size");
            if (type == 0x200) {
                if (header < 268) throw new IllegalArgumentException("Invalid resource package");
                int length = 0;
                while (length < 128 && b.getShort(offset + 12 + length * 2) != 0) length++;
                String name = new String(result, offset + 12, length * 2, StandardCharsets.UTF_16LE);
                if (name.equals(oldPackage)) {
                    java.util.Arrays.fill(result, offset + 12, offset + 268, (byte) 0);
                    byte[] encoded = newPackage.getBytes(StandardCharsets.UTF_16LE);
                    System.arraycopy(encoded, 0, result, offset + 12, encoded.length);
                }
            }
            offset += size;
        }
        return result;
    }

    private static int readLength(ByteBuffer b, boolean utf8) {
        int n = utf8 ? b.get() & 0xff : b.getShort() & 0xffff;
        int mask = utf8 ? 0x80 : 0x8000;
        return (n & mask) == 0 ? n : ((n & ~mask) << (utf8 ? 8 : 16)) | (utf8 ? b.get() & 0xff : b.getShort() & 0xffff);
    }
    private static void writeLength(ByteArrayOutputStream out, int n, boolean utf8) {
        if (utf8) {
            if (n > 0x7fff) throw new IllegalArgumentException("XML UTF-8 string too long");
            if (n > 0x7f) out.write((n >> 8) | 0x80);
            out.write(n & 0xff);
        } else {
            if (n > 0x7fff) { int high = (n >> 16) | 0x8000; out.write(high & 0xff); out.write(high >> 8); }
            out.write(n & 0xff); out.write((n >> 8) & 0xff);
        }
    }
}
