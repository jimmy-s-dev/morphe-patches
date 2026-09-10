import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import app.morphe.extension.music.patches.MusicHomeRenderer;

public class MusicHomeRendererTest {
 static byte[] field(int number,byte[] value) {
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  varint(out,((long)number<<3)|2);varint(out,value.length);out.writeBytes(value);return out.toByteArray();
 }
 static void varint(ByteArrayOutputStream out,long value) {
  while(value>127){out.write((int)(value&127)|128);value>>>=7;}out.write((int)value);
 }
 static byte[] concat(byte[]... arrays) {ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] b:arrays)out.writeBytes(b);return out.toByteArray();}
 static byte[] text(int n,String value){return field(n,value.getBytes(StandardCharsets.UTF_8));}
 static byte[] wrap(byte[] b,int... path){for(int i=path.length-1;i>=0;i--)b=field(path[i],b);return b;}
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args) throws Exception {
  if(args.length>0){for(String path:args){var rows=MusicHomeRenderer.read(Files.readAllBytes(Path.of(path)));System.out.println("Parsed Home rows="+rows.size()+", browse folders="+rows.stream().filter(r->!r.browseId.isEmpty()).count());}return;}
  byte[] command=field(48687626,text(2,"VLtest_playlist"));
  byte[] row=concat(text(1,"추천 재생목록"),text(2,"아티스트"),wrap(command,4,169495254));
  byte[] renderer=wrap(row,172660663,1,168777401,5,404005902,1,3);
  var rows=MusicHomeRenderer.read(renderer);
  check(rows.size()==1 && rows.get(0).browseId.equals("VLtest_playlist"));
  check(rows.get(0).title.equals("추천 재생목록") && rows.get(0).subtitle.equals("아티스트"));
  check(Arrays.equals(rows.get(0).command,command));
  check(MusicHomeRenderer.read(wrap(row,172660663,1,168777401,5,404005903,1,3)).isEmpty());
  check(MusicHomeRenderer.read(Arrays.copyOf(renderer,renderer.length-1)).isEmpty());
  check(MusicHomeRenderer.read(new byte[2_000_001]).isEmpty());
  check(MusicHomeRenderer.read(new byte[]{10,(byte)255,(byte)255,(byte)255,(byte)255,127}).isEmpty());
  System.out.println("PASS: Home model metadata, native command preservation, unknown schema, truncation and size limits");
 }
}
