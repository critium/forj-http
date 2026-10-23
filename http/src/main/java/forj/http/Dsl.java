package forj.http;

import java.util.List;

/**
 * The routing DSL: {@code import static forj.http.Dsl.*;} then
 *
 * <pre>{@code
 * Path1<Long> user = Path.of("/users").then(LongVar);
 * Routes<IO> routes = Routes.of(
 *     GET("/health").respond(req -> Ok("up")),
 *     GET(user).to((req, id) -> repo.find(id).map(u -> Ok(u))));
 * }</pre>
 *
 * Handlers come in two named forms, never overloads (Java can't pick between overloads of
 * implicitly typed lambdas by what they return):
 * <ul>
 *   <li>{@code to(...)}: the handler returns an {@code F<Response>};</li>
 *   <li>{@code respond(...)}: it returns a plain {@link Response}, lifted into {@code F}.</li>
 * </ul>
 * The handler receives the request, then the path's typed values in order.
 */
public final class Dsl {
    private Dsl() {}

    public static final Var<Integer> IntVar = Var.IntVar;
    public static final Var<Long> LongVar = Var.LongVar;
    public static final Var<java.util.UUID> UuidVar = Var.UuidVar;
    public static final Var<String> StringVar = Var.StringVar;

    // ------------------------------------------------------------------ starting a route

    public static On0 route(Method method, String path) {
        return new On0(method, Path.of(path).segments());
    }

    public static On0 route(Method method, Path0 path) {
        return new On0(method, path.segments());
    }

    public static <A> On1<A> route(Method method, Path1<A> path) {
        return new On1<>(method, path.segments());
    }

    public static <A, B> On2<A, B> route(Method method, Path2<A, B> path) {
        return new On2<>(method, path.segments());
    }

    public static <A, B, C> On3<A, B, C> route(Method method, Path3<A, B, C> path) {
        return new On3<>(method, path.segments());
    }

    public static <A, B, C, D> On4<A, B, C, D> route(Method method, Path4<A, B, C, D> path) {
        return new On4<>(method, path.segments());
    }

    public static On0 GET(String path) {
        return route(Method.GET, path);
    }

    public static On0 GET(Path0 path) {
        return route(Method.GET, path);
    }

    public static <A> On1<A> GET(Path1<A> path) {
        return route(Method.GET, path);
    }

    public static <A, B> On2<A, B> GET(Path2<A, B> path) {
        return route(Method.GET, path);
    }

    public static <A, B, C> On3<A, B, C> GET(Path3<A, B, C> path) {
        return route(Method.GET, path);
    }

    public static <A, B, C, D> On4<A, B, C, D> GET(Path4<A, B, C, D> path) {
        return route(Method.GET, path);
    }

    public static On0 POST(String path) {
        return route(Method.POST, path);
    }

    public static On0 POST(Path0 path) {
        return route(Method.POST, path);
    }

    public static <A> On1<A> POST(Path1<A> path) {
        return route(Method.POST, path);
    }

    public static <A, B> On2<A, B> POST(Path2<A, B> path) {
        return route(Method.POST, path);
    }

    public static <A, B, C> On3<A, B, C> POST(Path3<A, B, C> path) {
        return route(Method.POST, path);
    }

    public static <A, B, C, D> On4<A, B, C, D> POST(Path4<A, B, C, D> path) {
        return route(Method.POST, path);
    }

    public static On0 PUT(String path) {
        return route(Method.PUT, path);
    }

    public static On0 PUT(Path0 path) {
        return route(Method.PUT, path);
    }

    public static <A> On1<A> PUT(Path1<A> path) {
        return route(Method.PUT, path);
    }

    public static <A, B> On2<A, B> PUT(Path2<A, B> path) {
        return route(Method.PUT, path);
    }

    public static <A, B, C> On3<A, B, C> PUT(Path3<A, B, C> path) {
        return route(Method.PUT, path);
    }

    public static <A, B, C, D> On4<A, B, C, D> PUT(Path4<A, B, C, D> path) {
        return route(Method.PUT, path);
    }

    public static On0 PATCH(String path) {
        return route(Method.PATCH, path);
    }

    public static On0 PATCH(Path0 path) {
        return route(Method.PATCH, path);
    }

    public static <A> On1<A> PATCH(Path1<A> path) {
        return route(Method.PATCH, path);
    }

    public static <A, B> On2<A, B> PATCH(Path2<A, B> path) {
        return route(Method.PATCH, path);
    }

    public static <A, B, C> On3<A, B, C> PATCH(Path3<A, B, C> path) {
        return route(Method.PATCH, path);
    }

    public static <A, B, C, D> On4<A, B, C, D> PATCH(Path4<A, B, C, D> path) {
        return route(Method.PATCH, path);
    }

    public static On0 DELETE(String path) {
        return route(Method.DELETE, path);
    }

    public static On0 DELETE(Path0 path) {
        return route(Method.DELETE, path);
    }

    public static <A> On1<A> DELETE(Path1<A> path) {
        return route(Method.DELETE, path);
    }

    public static <A, B> On2<A, B> DELETE(Path2<A, B> path) {
        return route(Method.DELETE, path);
    }

    public static <A, B, C> On3<A, B, C> DELETE(Path3<A, B, C> path) {
        return route(Method.DELETE, path);
    }

    public static <A, B, C, D> On4<A, B, C, D> DELETE(Path4<A, B, C, D> path) {
        return route(Method.DELETE, path);
    }

    // ------------------------------------------------------------------ responses

    /*
     * Plain values, not F: effectful code maps into them (`repo.find(id).map(u -> Ok(u))`).
     * With a body, the encoder for its type is found at compile time: Ok("hi") is text/plain,
     * Ok(json(user)) is application/json.
     */

    /** A response with {@code status} and {@code body}, encoded by its {@link EntityEncoder}. */
    public static <A> Response response(Status status, A body)(using EntityEncoder<A> encoder) {
        return Response.of(status).withEntity(body)(using encoder);
    }

    public static Response Ok() {
        return Response.of(Status.OK);
    }

    public static <A> Response Ok(A body)(using EntityEncoder<A> encoder) {
        return response(Status.OK, body)(using encoder);
    }

    public static Response Created() {
        return Response.of(Status.CREATED);
    }

    public static <A> Response Created(A body)(using EntityEncoder<A> encoder) {
        return response(Status.CREATED, body)(using encoder);
    }

    public static Response Accepted() {
        return Response.of(Status.ACCEPTED);
    }

    public static <A> Response Accepted(A body)(using EntityEncoder<A> encoder) {
        return response(Status.ACCEPTED, body)(using encoder);
    }

    public static Response BadRequest() {
        return Response.of(Status.BAD_REQUEST);
    }

    public static <A> Response BadRequest(A body)(using EntityEncoder<A> encoder) {
        return response(Status.BAD_REQUEST, body)(using encoder);
    }

    public static Response Unauthorized() {
        return Response.of(Status.UNAUTHORIZED);
    }

    public static <A> Response Unauthorized(A body)(using EntityEncoder<A> encoder) {
        return response(Status.UNAUTHORIZED, body)(using encoder);
    }

    public static Response Forbidden() {
        return Response.of(Status.FORBIDDEN);
    }

    public static <A> Response Forbidden(A body)(using EntityEncoder<A> encoder) {
        return response(Status.FORBIDDEN, body)(using encoder);
    }

    public static Response NotFound() {
        return Response.of(Status.NOT_FOUND);
    }

    public static <A> Response NotFound(A body)(using EntityEncoder<A> encoder) {
        return response(Status.NOT_FOUND, body)(using encoder);
    }

    public static Response Conflict() {
        return Response.of(Status.CONFLICT);
    }

    public static <A> Response Conflict(A body)(using EntityEncoder<A> encoder) {
        return response(Status.CONFLICT, body)(using encoder);
    }

    public static Response UnprocessableContent() {
        return Response.of(Status.UNPROCESSABLE_CONTENT);
    }

    public static <A> Response UnprocessableContent(A body)(using EntityEncoder<A> encoder) {
        return response(Status.UNPROCESSABLE_CONTENT, body)(using encoder);
    }

    public static Response InternalServerError() {
        return Response.of(Status.INTERNAL_SERVER_ERROR);
    }

    public static <A> Response InternalServerError(A body)(using EntityEncoder<A> encoder) {
        return response(Status.INTERNAL_SERVER_ERROR, body)(using encoder);
    }

    public static Response ServiceUnavailable() {
        return Response.of(Status.SERVICE_UNAVAILABLE);
    }

    public static <A> Response ServiceUnavailable(A body)(using EntityEncoder<A> encoder) {
        return response(Status.SERVICE_UNAVAILABLE, body)(using encoder);
    }

    public static Response NoContent() {
        return Response.of(Status.NO_CONTENT);
    }

    /** 303 to {@code location}: where a client goes after a POST. */
    public static Response SeeOther(String location) {
        return Response.of(Status.SEE_OTHER).withHeader("Location", location);
    }

    public static Response Found(String location) {
        return Response.of(Status.FOUND).withHeader("Location", location);
    }

    public static Response MovedPermanently(String location) {
        return Response.of(Status.MOVED_PERMANENTLY).withHeader("Location", location);
    }

    // ------------------------------------------------------------------ handlers

    /** An effectful handler for a path with 0 typed values. */
    @FunctionalInterface
    public interface Handler0<F<_>> {
        F<Response> apply(Request request);
    }

    /** A pure handler for a path with 0 typed values. */
    @FunctionalInterface
    public interface Pure0 {
        Response apply(Request request);
    }

    /** An effectful handler for a path with 1 typed value. */
    @FunctionalInterface
    public interface Handler1<F<_>, A> {
        F<Response> apply(Request request, A a);
    }

    /** A pure handler for a path with 1 typed value. */
    @FunctionalInterface
    public interface Pure1<A> {
        Response apply(Request request, A a);
    }

    /** An effectful handler for a path with 2 typed values. */
    @FunctionalInterface
    public interface Handler2<F<_>, A, B> {
        F<Response> apply(Request request, A a, B b);
    }

    /** A pure handler for a path with 2 typed values. */
    @FunctionalInterface
    public interface Pure2<A, B> {
        Response apply(Request request, A a, B b);
    }

    /** An effectful handler for a path with 3 typed values. */
    @FunctionalInterface
    public interface Handler3<F<_>, A, B, C> {
        F<Response> apply(Request request, A a, B b, C c);
    }

    /** A pure handler for a path with 3 typed values. */
    @FunctionalInterface
    public interface Pure3<A, B, C> {
        Response apply(Request request, A a, B b, C c);
    }

    /** An effectful handler for a path with 4 typed values. */
    @FunctionalInterface
    public interface Handler4<F<_>, A, B, C, D> {
        F<Response> apply(Request request, A a, B b, C c, D d);
    }

    /** A pure handler for a path with 4 typed values. */
    @FunctionalInterface
    public interface Pure4<A, B, C, D> {
        Response apply(Request request, A a, B b, C c, D d);
    }

    /** A method and a path with 0 typed values, waiting for its handler. */
    public static final class On0 {
        private final Method method;
        private final List<Segment> segments;

        On0(Method method, List<Segment> segments) {
            this.method = method;
            this.segments = segments;
        }

        public <F<_>> Route<F> to(Handler0<F> handler) {
            return new Route<>(method, segments, (request, captures, ap) -> handler.apply(request));
        }

        public <F<_>> Route<F> respond(Pure0 handler) {
            return new Route<>(method, segments, (request, captures, ap) -> ap.pure(handler.apply(request)));
        }
    }

    /** A method and a path with 1 typed value, waiting for its handler. */
    public static final class On1<A> {
        private final Method method;
        private final List<Segment> segments;

        On1(Method method, List<Segment> segments) {
            this.method = method;
            this.segments = segments;
        }

        @SuppressWarnings("unchecked")
        public <F<_>> Route<F> to(Handler1<F, A> handler) {
            return new Route<>(method, segments, (request, captures, ap) -> handler.apply(request, (A) captures[0]));
        }

        @SuppressWarnings("unchecked")
        public <F<_>> Route<F> respond(Pure1<A> handler) {
            return new Route<>(method, segments, (request, captures, ap) -> ap.pure(handler.apply(request, (A) captures[0])));
        }
    }

    /** A method and a path with 2 typed values, waiting for its handler. */
    public static final class On2<A, B> {
        private final Method method;
        private final List<Segment> segments;

        On2(Method method, List<Segment> segments) {
            this.method = method;
            this.segments = segments;
        }

        @SuppressWarnings("unchecked")
        public <F<_>> Route<F> to(Handler2<F, A, B> handler) {
            return new Route<>(method, segments, (request, captures, ap) -> handler.apply(request, (A) captures[0], (B) captures[1]));
        }

        @SuppressWarnings("unchecked")
        public <F<_>> Route<F> respond(Pure2<A, B> handler) {
            return new Route<>(method, segments, (request, captures, ap) -> ap.pure(handler.apply(request, (A) captures[0], (B) captures[1])));
        }
    }

    /** A method and a path with 3 typed values, waiting for its handler. */
    public static final class On3<A, B, C> {
        private final Method method;
        private final List<Segment> segments;

        On3(Method method, List<Segment> segments) {
            this.method = method;
            this.segments = segments;
        }

        @SuppressWarnings("unchecked")
        public <F<_>> Route<F> to(Handler3<F, A, B, C> handler) {
            return new Route<>(method, segments, (request, captures, ap) -> handler.apply(request, (A) captures[0], (B) captures[1], (C) captures[2]));
        }

        @SuppressWarnings("unchecked")
        public <F<_>> Route<F> respond(Pure3<A, B, C> handler) {
            return new Route<>(method, segments, (request, captures, ap) -> ap.pure(handler.apply(request, (A) captures[0], (B) captures[1], (C) captures[2])));
        }
    }

    /** A method and a path with 4 typed values, waiting for its handler. */
    public static final class On4<A, B, C, D> {
        private final Method method;
        private final List<Segment> segments;

        On4(Method method, List<Segment> segments) {
            this.method = method;
            this.segments = segments;
        }

        @SuppressWarnings("unchecked")
        public <F<_>> Route<F> to(Handler4<F, A, B, C, D> handler) {
            return new Route<>(method, segments, (request, captures, ap) -> handler.apply(request, (A) captures[0], (B) captures[1], (C) captures[2], (D) captures[3]));
        }

        @SuppressWarnings("unchecked")
        public <F<_>> Route<F> respond(Pure4<A, B, C, D> handler) {
            return new Route<>(method, segments, (request, captures, ap) -> ap.pure(handler.apply(request, (A) captures[0], (B) captures[1], (C) captures[2], (D) captures[3])));
        }
    }

}
