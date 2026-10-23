package forj.http;

/**
 * What a server runs: a request in, an {@code F} of the response out. Every request is
 * answered (unlike {@link Routes}, which may not match). An app is a plain function, so a test
 * calls {@link #run} directly, with no server.
 */
@FunctionalInterface
public interface HttpApp<F<_>> {

    F<Response> run(Request request);

    /** This app wrapped in {@code middleware}. */
    default HttpApp<F> through(Middleware<F> middleware) {
        return middleware.apply(this);
    }
}
