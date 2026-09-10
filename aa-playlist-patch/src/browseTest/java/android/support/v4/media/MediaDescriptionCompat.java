package android.support.v4.media;
import android.net.Uri;
public class MediaDescriptionCompat {
 public final String id; public final CharSequence title;
 public MediaDescriptionCompat(String id, CharSequence title, CharSequence subtitle, CharSequence description,
 Object bitmap, Uri artwork, Object extras, Uri uri) { this.id=id; this.title=title; }
}
