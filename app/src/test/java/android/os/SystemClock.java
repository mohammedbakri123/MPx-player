package android.os;

/**
 * Test stub for [android.os.SystemClock] so JVM unit tests can run without throwing
 * "Method elapsedRealtime in android.os.SystemClock not mocked".
 */
public final class SystemClock {
    private SystemClock() {}

    public static long elapsedRealtime() {
        return System.currentTimeMillis();
    }

    public static long uptimeMillis() {
        return System.currentTimeMillis();
    }
}
