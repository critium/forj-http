package forj.http;

/**
 * Wraps an app: cross-cutting behaviour (errors, request ids, logging, timeouts, CORS, auth)
 * as plain function composition. Applies to a whole {@link HttpApp} or, with
 * {@link Routes#through}, to every route of a group while keeping the group's trie.
 */
@FunctionalInterface
public interface Middleware<F<_>> {

    HttpApp<F> apply(HttpApp<F> app);

    /** This middleware inside {@code outer}: {@code outer} sees the request first and the response last. */
    default Middleware<F> andThen(Middleware<F> outer) {
        return app -> outer.apply(apply(app));
    }

    static <F<_>> Middleware<F> identity() {
        return app -> app;
    }
}
