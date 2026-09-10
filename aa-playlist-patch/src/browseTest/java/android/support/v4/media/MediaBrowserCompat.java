package android.support.v4.media;
public class MediaBrowserCompat {
 public static class MediaItem {
  public static final int FLAG_BROWSABLE=1, FLAG_PLAYABLE=2;
  public final MediaDescriptionCompat description; public final int flags;
  public MediaItem(MediaDescriptionCompat description,int flags) {this.description=description;this.flags=flags;}
  public void writeToParcel(android.os.Parcel out,int flags) { out.bytes=1000+description.id.length()*2; }
 }
}
