package android.os;
public class Parcel {
 public int bytes;
 public static Parcel obtain(){return new Parcel();}
 public int dataSize(){return bytes;}
 public void recycle(){}
}
