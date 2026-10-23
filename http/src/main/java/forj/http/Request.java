package forj.http;

import java.util.Objects;
import java.util.Optional;

/**
 * An HTTP request: immutable, with {@code with*} copy methods. Backends build one per request;
 * tests build them with {@link #get}, {@link #post} and friends, no server needed.
 */
public record Request(Method method, Uri uri, Headers headers, Body body, Attributes attributes) {

    public Request {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(attributes, "attributes");
    }

    /** A request with no headers, no body and no attributes; {@code target} is {@code /path?query}. */
    public static Request of(Method method, String target) {
        return new Request(method, Uri.parse(target), Headers.EMPTY, Body.EMPTY, Attributes.EMPTY);
    }

    public static Request get(String target) {
        return of(Method.GET, target);
    }

    public static Request head(String target) {
        return of(Method.HEAD, target);
    }

    public static Request post(String target) {
        return of(Method.POST, target);
    }

    public static Request put(String target) {
        return of(Method.PUT, target);
    }

    public static Request patch(String target) {
        return of(Method.PATCH, target);
    }

    public static Request delete(String target) {
        return of(Method.DELETE, target);
    }

    public static Request options(String target) {
        return of(Method.OPTIONS, target);
    }

    // ------------------------------------------------------------------ reading

    /** The first value of header {@code name}. */
    public Optional<String> header(String name) {
        return headers.get(name);
    }

    public <T> Optional<T> attribute(Attributes.Key<T> key) {
        return attributes.get(key);
    }

    /**
     * The body decoded as an {@code A}: {@code User u = req.as();}, or {@code req.<User>as()}.
     * Reads the body, blocking.
     *
     * @throws DecodeFailure if it isn't a valid {@code A}
     */
    public <A> A as()(using EntityDecoder<A> decoder) {
        return decoder.decode(body, headers);
    }

    /** The first value of query parameter {@code name}. */
    public Optional<String> query(String name) {
        return uri.query().get(name);
    }

    // ------------------------------------------------------------------ copying

    public Request withMethod(Method method) {
        return new Request(method, uri, headers, body, attributes);
    }

    public Request withUri(Uri uri) {
        return new Request(method, uri, headers, body, attributes);
    }

    public Request withHeaders(Headers headers) {
        return new Request(method, uri, headers, body, attributes);
    }

    /** Replaces every value of header {@code name}. */
    public Request withHeader(String name, String value) {
        return withHeaders(headers.with(name, value));
    }

    /** Adds a value to header {@code name}, keeping existing ones. */
    public Request addHeader(String name, String value) {
        return withHeaders(headers.add(name, value));
    }

    public Request withBody(Body body) {
        return new Request(method, uri, headers, body, attributes);
    }

    /** The text as UTF-8; sets no content type. */
    public Request withBody(String text) {
        return withBody(Body.of(text));
    }

    public Request withBody(byte[] bytes) {
        return withBody(Body.of(bytes));
    }

    /** {@code value} as the body, with its encoder's {@code Content-Type}. */
    public <A> Request withEntity(A value)(using EntityEncoder<A> encoder) {
        Entity entity = encoder.encode(value);
        Request r = withBody(entity.body());
        return entity.contentType() == null ? r : r.withHeader("Content-Type", entity.contentType().toString());
    }

    public Request withAttributes(Attributes attributes) {
        return new Request(method, uri, headers, body, attributes);
    }

    public <T> Request withAttribute(Attributes.Key<T> key, T value) {
        return withAttributes(attributes.with(key, value));
    }

    @Override
    public String toString() {
        return s"Request($method $uri)";
    }
}
