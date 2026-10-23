package forj.http;

import java.util.Objects;

/**
 * What every part of a request's handling may need to know about the request: its id, for
 * logs and for the calls it makes downstream. The
 * {@link forj.http.middleware.Middlewares#requestId() requestId} middleware sets it; a
 * handler makes it a local given and everything below asks for it with {@code using}, so
 * nothing threads it by hand:
 *
 * <pre>{@code
 * GET(user).to((req, id) -> {
 *     given RequestContext context = RequestContext.of(req);
 *     return repo.find(id).map(u -> Ok(u));      // repo.find(id)(using RequestContext) gets it
 * })
 * }</pre>
 */
public record RequestContext(String requestId) {
    /** The header a request id travels in, both ways. */
    public static final String HEADER = "X-Request-Id";

    /** Where the middleware puts the context. */
    public static final Attributes.Key<RequestContext> KEY = Attributes.Key.of("forj.http.RequestContext");

    public RequestContext {
        Objects.requireNonNull(requestId, "requestId");
    }

    /**
     * The request's context: the one the middleware set, or else one with the request's
     * {@code X-Request-Id} header, or {@code "-"} without the middleware or the header.
     */
    public static RequestContext of(Request request) {
        return request.attribute(KEY).orElseGet(() -> new RequestContext(request.header(HEADER).orElse("-")));
    }
}
