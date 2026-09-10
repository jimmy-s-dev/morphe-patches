package android.util;
public class Base64 {
 public static String encodeToString(byte[] value,int flags) {
   var encoder=java.util.Base64.getUrlEncoder();
   if ((flags & 1)!=0) encoder=encoder.withoutPadding();
   return encoder.encodeToString(value);
 }
 public static byte[] decode(String value,int flags) { return java.util.Base64.getUrlDecoder().decode(value); }
}
