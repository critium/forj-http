package forj.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A request target in origin form: a path and an optional query, as sent on the request line
 * ({@code /users/7?verbose=1}). The path is split into percent-decoded segments once, when the
 * URI is made; the query is parsed on first use.
 *
 * <p>Segments: {@code /} and the empty path have none, {@code /a/b} has {@code [a, b]}, and a
 * trailing slash adds an empty last segment ({@code /a/} is {@code [a, ""]}), so routes match
 * {@code /a} and {@code /a/} separately. {@code %2F} decodes inside its segment and never
 * splits it.
 */
public final class Uri {
    private final String path;
    private final String rawQuery;
    private final List<String> segments;
    private Query query; // parsed on first use; a benign race, the result is the same

    private Uri(String path, String rawQuery) {
        this.path = path;
        this.rawQuery = rawQuery;
        this.segments = split(path);
    }

    /** {@code /path?query}; the query, after the first {@code ?}, is optional. A {@code #fragment} is dropped. */
    public static Uri parse(String target) {
        Objects.requireNonNull(target, "target");
        int hash = target.indexOf('#');
        if (hash >= 0) target = target.substring(0, hash);
        int q = target.indexOf('?');
        return q < 0 ? new Uri(target, "") : new Uri(target.substring(0, q), target.substring(q + 1));
    }

    /** From a raw (still encoded) path and query, as a backend has them; {@code rawQuery} may be null. */
    public static Uri of(String rawPath, String rawQuery) {
        return new Uri(Objects.requireNonNull(rawPath, "rawPath"), rawQuery == null ? "" : rawQuery);
    }

    /** The raw path, still percent-encoded. */
    public String path() {
        return path;
    }

    /** The decoded path segments (see the class comment). */
    public List<String> segments() {
        return segments;
    }

    /** The raw query, without the {@code ?}; empty when there is none. */
    public String rawQuery() {
        return rawQuery;
    }

    public Query query() {
        Query q = query;
        if (q == null) query = q = Query.parse(rawQuery);
        return q;
    }

    private static List<String> split(String path) {
        int start = path.startsWith("/") ? 1 : 0;
        if (start >= path.length()) return List.of();
        List<String> parts = new ArrayList<>(4);
        while (true) {
            int slash = path.indexOf('/', start);
            if (slash < 0) {
                parts.add(decode(path.substring(start), false));
                return List.copyOf(parts);
            }
            parts.add(decode(path.substring(start, slash), false));
            start = slash + 1;
        }
    }

    /**
     * Percent-decodes {@code s} as UTF-8; with {@code plusIsSpace} (queries) {@code +} is a space.
     * A malformed escape is kept as written rather than rejected.
     */
    static String decode(String s, boolean plusIsSpace) {
        if (s.indexOf('%') < 0 && !(plusIsSpace && s.indexOf('+') >= 0)) return s;
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length() && hex(s.charAt(i + 1)) >= 0 && hex(s.charAt(i + 2)) >= 0) {
                out.write(hex(s.charAt(i + 1)) << 4 | hex(s.charAt(i + 2)));
                i += 2;
            } else if (c == '+' && plusIsSpace) {
                out.write(' ');
            } else if (c < 0x80) {
                out.write(c);
            } else {
                byte[] utf8 = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                if (Character.isHighSurrogate(c) && i + 1 < s.length()) {
                    utf8 = s.substring(i, i + 2).getBytes(StandardCharsets.UTF_8);
                    i++;
                }
                out.writeBytes(utf8);
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Uri u && path.equals(u.path) && rawQuery.equals(u.rawQuery);
    }

    @Override
    public int hashCode() {
        return path.hashCode() * 31 + rawQuery.hashCode();
    }

    @Override
    public String toString() {
        return rawQuery.isEmpty() ? path : s"$path?$rawQuery";
    }
}
