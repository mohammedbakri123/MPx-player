package android.net;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Lightweight test stub for [android.net.Uri] so JVM unit tests can run without throwing
 * "Method parse in android.net.Uri not mocked".
 */
public abstract class Uri implements Comparable<Uri> {

    public abstract String getScheme();
    public abstract String getAuthority();
    public abstract String getHost();
    public abstract String getPath();
    public abstract String getLastPathSegment();
    public abstract List<String> getPathSegments();
    public abstract String getQuery();

    public static Uri parse(String uriString) {
        if (uriString == null) return null;
        return new StringUri(uriString);
    }

    public static Uri fromFile(File file) {
        if (file == null) return null;
        return new StringUri("file://" + file.getAbsolutePath());
    }

    @Override
    public int compareTo(Uri o) {
        return toString().compareTo(o != null ? o.toString() : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Uri)) return false;
        return Objects.equals(toString(), o.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public abstract String toString();

    private static class StringUri extends Uri {
        private final String uriString;
        private final URI parsed;

        StringUri(String uriString) {
            this.uriString = uriString;
            URI p = null;
            try {
                p = URI.create(uriString);
            } catch (Exception ignored) {
            }
            this.parsed = p;
        }

        @Override
        public String getScheme() {
            if (parsed != null && parsed.getScheme() != null) {
                return parsed.getScheme();
            }
            int colon = uriString.indexOf(':');
            return colon != -1 ? uriString.substring(0, colon) : null;
        }

        @Override
        public String getAuthority() {
            return parsed != null ? parsed.getAuthority() : null;
        }

        @Override
        public String getHost() {
            return parsed != null ? parsed.getHost() : null;
        }

        @Override
        public String getPath() {
            if (parsed != null && parsed.getPath() != null) {
                return parsed.getPath();
            }
            int colon = uriString.indexOf(':');
            String afterScheme = colon != -1 ? uriString.substring(colon + 1) : uriString;
            while (afterScheme.startsWith("/")) {
                afterScheme = afterScheme.substring(1);
            }
            return "/" + afterScheme;
        }

        @Override
        public String getLastPathSegment() {
            List<String> segments = getPathSegments();
            return segments.isEmpty() ? null : segments.get(segments.size() - 1);
        }

        @Override
        public List<String> getPathSegments() {
            String path = getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                return Collections.emptyList();
            }
            String[] raw = path.split("/");
            List<String> result = new ArrayList<>();
            for (String s : raw) {
                if (!s.isEmpty()) {
                    result.add(s);
                }
            }
            return Collections.unmodifiableList(result);
        }

        @Override
        public String getQuery() {
            return parsed != null ? parsed.getQuery() : null;
        }

        @Override
        public String toString() {
            return uriString;
        }
    }
}
