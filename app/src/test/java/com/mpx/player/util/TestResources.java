package com.mpx.player.util;

import android.content.res.Resources;

/**
 * A {@link Resources} whose {@code getString()} answers without the Android
 * framework, for host JVM unit tests.
 *
 * <p>{@code Context.getString(int, Object...)} is final and delegates to
 * {@code getResources().getString(...)}, so a fake {@code Context} whose
 * base is null NPEs on any string lookup. Returning this from a fake
 * context's {@code getResources()} intercepts the lookup. The super
 * constructor is a no-op in the unit-test android.jar stub, so the null
 * arguments are never dereferenced; this class never touches them because
 * {@code getString()} is overridden.
 */
public class TestResources extends Resources {

    public TestResources() {
        super(null, null, null);
    }

    @Override
    public String getString(int id, Object... formatArgs) {
        if (formatArgs != null && formatArgs.length > 0) {
            return "Unsupported format: " + formatArgs[0];
        }
        return "unsupported";
    }
}
