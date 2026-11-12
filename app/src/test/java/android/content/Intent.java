package android.content;

import android.net.Uri;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight test stub for [android.content.Intent] so JVM unit tests can run without throwing
 * unmocked method exceptions on Intent operations.
 */
public class Intent {
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public static final String ACTION_SEND = "android.intent.action.SEND";
    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    public static final String EXTRA_TEXT = "android.intent.extra.TEXT";

    private String mAction;
    private Uri mData;
    private final Map<String, Object> mExtras = new HashMap<>();

    public Intent() {}

    public Intent(String action) {
        this.mAction = action;
    }

    public Intent(String action, Uri uri) {
        this.mAction = action;
        this.mData = uri;
    }

    public String getAction() {
        return mAction;
    }

    public Intent setAction(String action) {
        this.mAction = action;
        return this;
    }

    public Uri getData() {
        return mData;
    }

    public Intent setData(Uri data) {
        this.mData = data;
        return this;
    }

    public String getDataString() {
        return mData != null ? mData.toString() : null;
    }

    public Intent putExtra(String name, String value) {
        mExtras.put(name, value);
        return this;
    }

    public Intent putExtra(String name, boolean value) {
        mExtras.put(name, value);
        return this;
    }

    public Intent putExtra(String name, int value) {
        mExtras.put(name, value);
        return this;
    }

    public String getStringExtra(String name) {
        Object val = mExtras.get(name);
        return val != null ? val.toString() : null;
    }
}
