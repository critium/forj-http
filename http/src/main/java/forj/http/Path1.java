package forj.http;

import java.util.List;

/** A route path capturing 1 typed value: A, in order. */
public final class Path1<A> {
    private final List<Segment> segments;

    Path1(List<Segment> segments) {
        this.segments = segments;
    }

    /** Adds a fixed segment. */
    public Path1<A> then(String literal) {
        return new Path1<>(Path.append(segments, new Segment.Literal(literal)));
    }

    /** Adds a typed segment, whose value the handler receives last. */
    public <B> Path2<A, B> then(Var<B> var) {
        return new Path2<>(Path.append(segments, var));
    }

    public List<Segment> segments() {
        return segments;
    }

    @Override
    public String toString() {
        return Path.show(segments);
    }
}
