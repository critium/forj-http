package forj.http;

import forj.typeclass.Applicative;
import java.util.List;
import java.util.Objects;

/**
 * One method and path with its handler, made with {@link Dsl}: {@code GET(path).to(...)} or
 * {@code GET(path).respond(...)}. {@link Routes#of} compiles routes into a trie.
 */
public final class Route<F<_>> {

    /**
     * A handler after the path is matched: the request, the captured values in order, and the
     * effect's {@code Applicative}, which arrives only when routes are run, so building routes
     * needs no instance (see {@link Routes}).
     */
    @FunctionalInterface
    interface Endpoint<G<_>> {
        G<Response> run(Request request, Object[] captures, Applicative<G> ap);
    }

    final Method method;
    final List<Segment> segments;
    final Endpoint<F> endpoint;

    Route(Method method, List<Segment> segments, Endpoint<F> endpoint) {
        this.method = Objects.requireNonNull(method, "method");
        this.segments = segments;
        this.endpoint = endpoint;
    }

    public Method method() {
        return method;
    }

    public List<Segment> segments() {
        return segments;
    }

    @Override
    public String toString() {
        return s"$method ${Path.show(segments)}";
    }
}
