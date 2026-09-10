package app.morphe.extension.shared;
public class Utils {
 public static final java.util.List<Runnable> timers=new java.util.ArrayList<>();
 public static void runOnBackgroundThread(Runnable action) { action.run(); }
 public static void runOnMainThreadDelayed(Runnable action,long delay) { timers.add(action); }
}
