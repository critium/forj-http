package forj.http.middleware;

import forj.data.Unit;
import forj.http.HttpFailure;
import forj.http.Method;
import forj.http.Middleware;
import forj.http.Problem;
import forj.http.Request;
import forj.http.RequestContext;
import forj.http.Response;
import forj.http.Status;
import forj.typeclass.Applicative;
import forj.typeclass.Concurrent;
import forj.typeclass.Functor;
import forj.typeclass.MonadError;
import forj.typeclass.Sync;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The essential middleware, each generic in {@code F} and asking for the least it needs:
 *
 * <pre>{@code
 * import static forj.http.middleware.Middlewares.*;
 *
 * HttpApp<IO> app = routes.orNotFound()
 *         .through(errors())
 *         .through(timeout(Duration.ofSeconds(10)))
 *         .through(requestId())
 *         .through(accessLog());
 * }</pre>
 *
 * Each {@code through} wraps everything before it, so the last one sees the request first and
 * the response last. With the order above, the access log records the id and the final status,
 * timeouts included, and errors are turned into responses before anything else sees them.
 */
public final class Middlewares {
    private Middlewares() {}

    private static final System.Logger ERRORS = System.getLogger("forj.http.errors");
    private static final System.Logger ACCESS = System.getLogger("forj.http.access");

    // ------------------------------------------------------------------ errors

    /** {@link #errorsWith}, reporting unexpected errors to the {@code forj.http.errors} logger. */
    public static <F<_>> Middleware<F> errors()(using MonadError<F, Throwable> errors) {
        return errorsWith(Middlewares::logError)(using errors);
    }

    /**
     * Turns failures into problem responses ({@code application/problem+json}):
     * <ul>
     *   <li>an {@link HttpFailure} (such as a {@link forj.http.DecodeFailure}) answers with its
     *       status and its message as the detail;</li>
     *   <li>anything else is a 500 whose cause stays private: it goes to {@code report}, never to
     *       the client.</li>
     * </ul>
     * Catches both errors raised in {@code F} and exceptions a handler throws directly.
     *
     * <p>(A separate name, not an {@code errors} overload: forj resolves {@code errors(lambda)}
     * to the no-argument overload, taking the lambda for its instance. See forj's TODO.)
     */
    public static <F<_>> Middleware<F> errorsWith(BiConsumer<Request, Throwable> report)(using MonadError<F, Throwable> errors) {
        return app -> req -> {
            F<Response> response;
            try {
                response = app.run(req);
            } catch (RuntimeException e) {
                response = errors.raiseError(e);
            }
            return response.handleError(e -> problem(req, e, report));
        };
    }

    private static Response problem(Request req, Throwable e, BiConsumer<Request, Throwable> report) {
        if (e instanceof HttpFailure failure) {
            if (failure.status().isServerError()) report.accept(req, e);
            return failure.toResponse();
        }
        report.accept(req, e);
        return new Problem(Status.INTERNAL_SERVER_ERROR, null).toResponse();
    }

    private static void logError(Request req, Throwable e) {
        ERRORS.log(System.Logger.Level.ERROR, s"${req.method()} ${req.uri()} [${RequestContext.of(req).requestId()}] failed", e);
    }

    // ------------------------------------------------------------------ request id

    /**
     * Gives every request a {@link RequestContext}: the caller's {@code X-Request-Id} when it's a
     * sensible one (1 to 128 letters, digits, {@code .}, {@code _} or {@code -}), a new random id
     * otherwise. The id is echoed in the response's {@code X-Request-Id}.
     */
    public static <F<_>> Middleware<F> requestId()(using Functor<F> functor) {
        return app -> req -> {
            String id = req.header(RequestContext.HEADER).filter(Middlewares::sensibleId).orElseGet(Middlewares::newId);
            return app.run(req.withAttribute(RequestContext.KEY, new RequestContext(id)))
                    .map(res -> res.withHeader(RequestContext.HEADER, id));
        };
    }

    private static boolean sensibleId(String id) {
        if (id.isEmpty() || id.length() > 128) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-';
            if (!ok) return false;
        }
        return true;
    }

    /** 16 hex digits. ThreadLocalRandom keeps its state on the Thread itself, so it's cheap on virtual threads. */
    private static String newId() {
        return HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextLong());
    }

    // ------------------------------------------------------------------ access log

    /** {@link #accessLogTo} the {@code forj.http.access} logger, at INFO. */
    public static <F<_>> Middleware<F> accessLog()(using Sync<F> sync) {
        return accessLogTo(line -> ACCESS.log(System.Logger.Level.INFO, line))(using sync);
    }

    /**
     * One line per request, once it's answered, timed from when the response starts being
     * computed: {@code GET /users/7 200 1.4ms id=3f9c...}. A failure that reaches it is logged as
     * {@code error} and passed on unchanged. (Not an {@code accessLog} overload, for the same
     * reason as {@link #errorsWith}.)
     */
    public static <F<_>> Middleware<F> accessLogTo(Consumer<String> sink)(using Sync<F> sync) {
        return app -> req -> forj {
            start <- sync.delay(System::nanoTime);
            result <- app.run(req).attempt();
            _ <- sync.delay(() -> {
                sink.accept(accessLine(req, result.fold(e -> null, res -> res), result.fold(e -> e, res -> null),
                        System.nanoTime() - start));
                return Unit.UNIT;
            });
            response <- result.fold(e -> sync.<Response>raiseError(e), res -> sync.pure(res));
        } yield response;
    }

    /**
     * The id comes from the response's {@code X-Request-Id} when there is one, so the log finds
     * it even when it wraps {@link #requestId} (which sets the header on the way out); otherwise
     * from the request.
     */
    private static String accessLine(Request req, Response res, Throwable error, long nanos) {
        double millis = nanos / 1_000_000.0;
        String outcome = res != null ? Integer.toString(res.status().code()) : s"error ${error.getClass().getSimpleName()}";
        Optional<String> id = Optional.ofNullable(res)
                .flatMap(r -> r.header(RequestContext.HEADER))
                .or(() -> req.attribute(RequestContext.KEY).map(RequestContext::requestId))
                .or(() -> req.header(RequestContext.HEADER));
        String idPart = id.map(i -> s" id=$i").orElse("");
        return f"${req.method()} ${req.uri()} $outcome $millis%.1fms$idPart";
    }

    // ------------------------------------------------------------------ timeout

    /**
     * Answers 503 when the response takes longer than {@code limit}, and cancels the handler
     * (its virtual thread is interrupted, so blocking calls stop and finalizers run).
     */
    public static <F<_>> Middleware<F> timeout(Duration limit)(using Concurrent<F> concurrent) {
        return app -> req -> {
            F<Unit> timer = concurrent.delay(() -> {
                Thread.sleep(limit);
                return Unit.UNIT;
            });
            return app.run(req).race(timer).map(winner -> winner.fold(
                    res -> res,
                    timedOut -> new Problem(Status.SERVICE_UNAVAILABLE, s"no response within $limit").toResponse()));
        };
    }

    // ------------------------------------------------------------------ CORS

    /**
     * Cross-origin resource sharing. A preflight ({@code OPTIONS} with
     * {@code Access-Control-Request-Method}) from an allowed origin is answered here with 204 and
     * never reaches the routes; from another origin, with 403. Other requests from an allowed
     * origin get the {@code Access-Control-*} headers added; requests without an
     * {@code Origin}, or from origins not allowed, pass through untouched, and the browser
     * withholds the response.
     */
    public static <F<_>> Middleware<F> cors(CorsPolicy policy)(using Applicative<F> ap) {
        return app -> req -> {
            Optional<String> origin = req.header("Origin");
            if (origin.isEmpty()) return app.run(req);
            boolean allowed = policy.allows(origin.get());
            boolean preflight = req.method().equals(Method.OPTIONS)
                    && req.header("Access-Control-Request-Method").isPresent();
            if (preflight) {
                return ap.pure(allowed ? policy.preflight(origin.get(), req) : Response.of(Status.FORBIDDEN));
            }
            if (!allowed) return app.run(req);
            return app.run(req).map(res -> policy.decorate(origin.get(), res));
        };
    }
}
