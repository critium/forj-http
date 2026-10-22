package forj.http;

import forj.typeclass.Applicative;

/**
 * What a server runs: a request in, an {@code F} of the response out. Every request is
 * answered (unlike {@link Routes}, which may not match). An app is a plain function, so a test
 * calls {@link #run} directly, with no server.
 */
@FunctionalInterface
public interface HttpApp<F<_>> {

    F<Response> run(Request request);

    /**
     * The app serving {@code routes}: a request no route matches gets 404. {@code F} comes
     * from {@code routes}, and the effect's {@code Applicative} lifts the pure handlers.
     */
    static <F<_>> HttpApp<F> of(Routes<F> routes)(using Applicative<F> ap) {
        return routes.orNotFound(ap);
    }

    /** This app wrapped in {@code middleware}. */
    default HttpApp<F> through(Middleware<F> middleware) {
        return middleware.apply(this);
    }
}
