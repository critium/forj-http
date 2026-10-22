package forj.http;

import java.util.ArrayList;
import java.util.List;

/**
 * Where typed route paths start: {@code Path.root().then("users").then(LongVar)}, or
 * {@code Path.of("/users").then(LongVar)}, is a {@link Path1}{@code <Long>}. Each
 * {@code then(Var)} adds a value the handler receives, up to four ({@link Path0} to
 * {@link Path4}). Plain Java: no plugin needed.
 */
public final class Path {
    private Path() {}

    /** The empty path, {@code /}. */
    public static Path0 root() {
        return Path0.ROOT;
    }

    /** Fixed segments, written as a path: {@code Path.of("/api/users")}. Empty segments are skipped. */
    public static Path0 of(String literalPath) {
        Path0 path = Path0.ROOT;
        for (String part : literalPath.split("/")) {
            if (!part.isEmpty()) path = path.then(part);
        }
        return path;
    }

    static List<Segment> append(List<Segment> segments, Segment next) {
        List<Segment> all = new ArrayList<>(segments.size() + 1);
        all.addAll(segments);
        all.add(next);
        return List.copyOf(all);
    }

    static String show(List<Segment> segments) {
        if (segments.isEmpty()) return "/";
        StringBuilder sb = new StringBuilder();
        for (Segment s : segments) sb.append('/').append(s);
        return sb.toString();
    }
}
