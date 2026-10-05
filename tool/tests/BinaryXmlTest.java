import com.hiddify.hiddify.privacy.BinaryXml;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;

/** Independent binary XML fixtures check package/authority renaming, Unicode labels,
 * variable-length pools, unchanged node indexes and rejection of corrupt offsets. */
public class BinaryXmlTest {
    static byte[] fixture(boolean utf8) {
        String[] strings = {"app.hiddify.com", "app.hiddify.com.privacy.files", "com.hiddify.hiddify.MainActivity", "Hiddify"};
        ByteArrayOutputStream data = new ByteArrayOutputStream(); int[] offsets=new int[strings.length];
        for(int i=0;i<strings.length;i++) {
            offsets[i]=data.size();byte[] encoded=strings[i].getBytes(utf8?StandardCharsets.UTF_8:StandardCharsets.UTF_16LE);
            data.write(strings[i].length());if(utf8)data.write(encoded.length);else data.write(0);
            data.write(encoded,0,encoded.length);data.write(0);if(!utf8)data.write(0);
        }
        while(data.size()%4!=0)data.write(0);
        int poolSize=28+offsets.length*4+data.size();
        ByteBuffer b=ByteBuffer.allocate(8+poolSize+8).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)3).putShort((short)8).putInt(b.capacity());
        b.putShort((short)1).putShort((short)28).putInt(poolSize).putInt(offsets.length).putInt(0).putInt(utf8?256:0).putInt(28+offsets.length*4).putInt(0);
        for(int offset:offsets)b.putInt(offset);b.put(data.toByteArray());b.putInt(0x12345678).putInt(8);
        return b.array();
    }
    public static void main(String[] args) {
        for(boolean utf8:new boolean[]{false,true}) {
            byte[] source=fixture(utf8);
            final int[] changes={0};String label="Настройки сети "+"я".repeat(150);
            byte[] renamed=BinaryXml.rewrite(source,s->{
                if(s.equals("Hiddify")){changes[0]++;return label;}
                if(s.startsWith("app.hiddify.com")){changes[0]++;return s.replace("app.hiddify.com","app.random.packageidentity");}
                return s;
            });
            if(changes[0]!=3)throw new AssertionError("Package, authority and label should change");
            final int[] seen={0};
            BinaryXml.rewrite(renamed,s->{
                if(s.equals("app.random.packageidentity")||s.equals("app.random.packageidentity.privacy.files")||s.equals(label)||s.equals("com.hiddify.hiddify.MainActivity"))seen[0]++;
                return s;
            });
            if(seen[0]!=4)throw new AssertionError("String pool offsets/encoding corrupted");
            ByteBuffer result=ByteBuffer.wrap(renamed).order(ByteOrder.LITTLE_ENDIAN);
            if(result.getInt(4)!=renamed.length||result.getInt(renamed.length-8)!=0x12345678)throw new AssertionError("Node chunk changed");
            if(!java.util.Arrays.equals(source,BinaryXml.rewrite(source,s->s)))throw new AssertionError("Identity rewrite must be exact");
            ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN).putInt(36,Integer.MAX_VALUE);
            try { BinaryXml.rewrite(source,s->s); throw new AssertionError("Bad offset accepted"); }
            catch(IllegalArgumentException expected){}
        }
        ByteBuffer table = ByteBuffer.allocate(12+288).order(ByteOrder.LITTLE_ENDIAN);
        table.putShort((short)2).putShort((short)12).putInt(table.capacity()).putInt(1);
        table.putShort((short)0x200).putShort((short)288).putInt(288).putInt(0x7f);
        table.put("app.hiddify.com".getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
        byte[] renamedTable = BinaryXml.renameResourcePackage(table.array(), "app.hiddify.com", "app.random.identity");
        String resourceName = new String(renamedTable,24,"app.random.identity".length()*2,java.nio.charset.StandardCharsets.UTF_16LE);
        if(!resourceName.equals("app.random.identity") || renamedTable.length!=table.capacity()) throw new AssertionError("Resource package was not renamed");
        byte[] pool = BinaryXml.rewrite(fixture(true), v -> switch(v) {
            case "app.hiddify.com" -> "icon";
            case "app.hiddify.com.privacy.files" -> "roundIcon";
            case "com.hiddify.hiddify.MainActivity" -> "activity";
            default -> "application";
        });
        ByteBuffer icons = ByteBuffer.allocate(pool.length-8+24+76*2).order(ByteOrder.LITTLE_ENDIAN);
        icons.put(pool,0,pool.length-8);
        icons.putShort((short)0x180).putShort((short)8).putInt(24).putInt(0x01010002).putInt(0x0101052c).putInt(0).putInt(0);
        int applicationOffset = icons.position();
        for(int name:new int[]{3,2}) {
            icons.putShort((short)0x102).putShort((short)16).putInt(76).putInt(1).putInt(-1);
            icons.putInt(-1).putInt(name).putShort((short)20).putShort((short)20).putShort((short)2).putShort((short)0).putShort((short)0).putShort((short)0);
            for(int attribute:new int[]{0,1}) icons.putInt(-1).putInt(attribute).putInt(-1).putShort((short)8).put((byte)0).put((byte)1).putInt(0x7f010001);
        }
        icons.putInt(4,icons.capacity());
        ByteBuffer changed = ByteBuffer.wrap(BinaryXml.setApplicationIcon(icons.array(),0x7f010002)).order(ByteOrder.LITTLE_ENDIAN);
        if(changed.getInt(applicationOffset+52)!=0x7f010002 || changed.getInt(applicationOffset+72)!=0x7f010002 || changed.getInt(applicationOffset+76+52)!=0x7f010001) throw new AssertionError("Only application icons should change");
        System.out.println("BinaryXml fixtures passed (UTF-8, UTF-16, Unicode, authorities, corruption)");
    }
}
