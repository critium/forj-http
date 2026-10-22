package forj.http;

import java.util.List;

/** A route path capturing 2 typed values: A, B, in order. */
public final class Path2<A, B> {
    private final List<Segment> segments;

    Path2(List<Segment> segments) {
        this.segments = segments;
    }

    /** Adds a fixed segment. */
    public Path2<A, B> then(String literal) {
        return new Path2<>(Path.append(segments, new Segment.Literal(literal)));
    }

    /** Adds a typed segment, whose value the handler receives last. */
    public <C> Path3<A, B, C> then(Var<C> var) {
        return new Path3<>(Path.append(segments, var));
    }

    public List<Segment> segments() {
        return segments;
    }

    @Override
    public String toString() {
        return Path.show(segments);
    }
}
