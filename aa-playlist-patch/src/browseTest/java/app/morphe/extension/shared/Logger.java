package app.morphe.extension.shared;
import java.util.function.Supplier;
public class Logger {
 public static void printDebug(Supplier<String> text) {}
 public static void printInfo(Supplier<String> text) {}
 public static void printException(Supplier<String> text, Exception ex) { throw new AssertionError(text.get(),ex); }
}
