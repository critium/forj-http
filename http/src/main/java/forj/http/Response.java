package forj.http;

import java.util.Objects;
import java.util.Optional;

/**
 * An HTTP response: a plain, immutable value, never an {@code F}. Handlers build one and
 * effectful code maps into it ({@code repo.find(id).map(u -> ...)}).
 */
public record Response(Status status, Headers headers, Body body) {

    public Response {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
    }

    /** A response with no headers and no body. */
    public static Response of(Status status) {
        return new Response(status, Headers.EMPTY, Body.EMPTY);
    }

    /** The first value of header {@code name}. */
    public Optional<String> header(String name) {
        return headers.get(name);
    }

    public Response withStatus(Status status) {
        return new Response(status, headers, body);
    }

    public Response withHeaders(Headers headers) {
        return new Response(status, headers, body);
    }

    /** Replaces every value of header {@code name}. */
    public Response withHeader(String name, String value) {
        return withHeaders(headers.with(name, value));
    }

    /** Adds a value to header {@code name}, keeping existing ones. */
    public Response addHeader(String name, String value) {
        return withHeaders(headers.add(name, value));
    }

    public Response withBody(Body body) {
        return new Response(status, headers, body);
    }

    /** The text as UTF-8; sets no content type. */
    public Response withBody(String text) {
        return withBody(Body.of(text));
    }

    public Response withBody(byte[] bytes) {
        return withBody(Body.of(bytes));
    }

    /** {@code value} as the body, with its encoder's {@code Content-Type}. */
    public <A> Response withEntity(A value)(using EntityEncoder<A> encoder) {
        Entity entity = encoder.encode(value);
        Response r = withBody(entity.body());
        return entity.contentType() == null ? r : r.withHeader("Content-Type", entity.contentType().toString());
    }

    @Override
    public String toString() {
        return s"Response($status)";
    }
}
