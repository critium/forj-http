package forj.http;

import java.util.List;

/** A route path capturing 3 typed values: A, B, C, in order. */
public final class Path3<A, B, C> {
    private final List<Segment> segments;

    Path3(List<Segment> segments) {
        this.segments = segments;
    }

    /** Adds a fixed segment. */
    public Path3<A, B, C> then(String literal) {
        return new Path3<>(Path.append(segments, new Segment.Literal(literal)));
    }

    /** Adds a typed segment, whose value the handler receives last. */
    public <D> Path4<A, B, C, D> then(Var<D> var) {
        return new Path4<>(Path.append(segments, var));
    }

    public List<Segment> segments() {
        return segments;
    }

    @Override
    public String toString() {
        return Path.show(segments);
    }
}
