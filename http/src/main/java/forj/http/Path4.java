package forj.http;

import java.util.List;

/** A route path capturing 4 typed values: A, B, C, D, in order. */
public final class Path4<A, B, C, D> {
    private final List<Segment> segments;

    Path4(List<Segment> segments) {
        this.segments = segments;
    }

    /** Adds a fixed segment. */
    public Path4<A, B, C, D> then(String literal) {
        return new Path4<>(Path.append(segments, new Segment.Literal(literal)));
    }

    public List<Segment> segments() {
        return segments;
    }

    @Override
    public String toString() {
        return Path.show(segments);
    }
}
