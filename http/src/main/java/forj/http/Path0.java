package forj.http;

import java.util.List;

/** A route path with no typed segments. */
public final class Path0 {
    static final Path0 ROOT = new Path0(List.of());

    private final List<Segment> segments;

    Path0(List<Segment> segments) {
        this.segments = segments;
    }

    /** Adds a fixed segment. */
    public Path0 then(String literal) {
        return new Path0(Path.append(segments, new Segment.Literal(literal)));
    }

    /** Adds a typed segment, whose value the handler receives last. */
    public <A> Path1<A> then(Var<A> var) {
        return new Path1<>(Path.append(segments, var));
    }

    public List<Segment> segments() {
        return segments;
    }

    @Override
    public String toString() {
        return Path.show(segments);
    }
}
