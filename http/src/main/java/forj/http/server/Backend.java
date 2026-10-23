package forj.http.server;

import forj.http.HttpApp;
import forj.typeclass.Effect;

/**
 * An HTTP engine that serves an {@link HttpApp}. forj-http has none built in; they're separate
 * modules (forj-http-helidon first), and an app picks one by constructing it. No
 * {@code ServiceLoader}: nothing is discovered at runtime.
 *
 * <p>The contract for an implementation:
 * <ul>
 *   <li>each request runs on its own virtual thread, which builds a {@link forj.http.Request}
 *       (with {@link forj.http.Method#of} and {@link forj.http.Uri#of}, so common values aren't
 *       allocated), calls {@code app.run(request)}, and runs the result with
 *       {@code effect.unsafeRunSync}, blocking, on that same thread: no hand-off to another
 *       pool;</li>
 *   <li>the request body is a {@link forj.http.Body#of(java.io.InputStream, long) stream},
 *       limited to {@link ServerConfig#maxBodyBytes()}; a larger body is answered with 413
 *       before or while it's read;</li>
 *   <li>the response body is written with {@link forj.http.Body#writeTo}, using
 *       {@link forj.http.Body#length()} for {@code Content-Length} when it's known;</li>
 *   <li>if running the app throws (no {@code errors()} middleware, or a failure inside it), the
 *       client gets a bare 500 and the error is logged; a connection never just closes.</li>
 * </ul>
 */
public interface Backend {

    /**
     * Starts serving {@code app}, and returns once the server accepts connections.
     *
     * @throws java.io.UncheckedIOException if the address can't be bound
     */
    <F<_>> Server start(ServerConfig config, HttpApp<F> app)(using Effect<F> effect);
}
