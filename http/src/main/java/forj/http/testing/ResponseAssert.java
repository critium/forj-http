package forj.http.testing;

import forj.http.Response;
import forj.http.Status;
import java.util.Optional;

/**
 * Fluent checks on a {@link Response}, failing with {@link AssertionError}, so they work under
 * any test framework. The body is read once and kept, so a streamed body can be checked more
 * than once.
 */
public final class ResponseAssert {
    private final Response response;
    private byte[] body;

    ResponseAssert(Response response) {
        if (response == null) throw new AssertionError("expected a response, got null");
        this.response = response;
    }

    public ResponseAssert hasStatus(Status expected) {
        return hasStatus(expected.code());
    }

    public ResponseAssert hasStatus(int expected) {
        if (response.status().code() != expected) {
            fail(s"expected status $expected but was ${response.status()}");
        }
        return this;
    }

    public ResponseAssert hasHeader(String name, String expected) {
        Optional<String> actual = response.header(name);
        if (actual.isEmpty()) fail(s"expected header $name: $expected but it is missing");
        if (!actual.get().equals(expected)) fail(s"expected header $name: $expected but was ${actual.get()}");
        return this;
    }

    public ResponseAssert hasNoHeader(String name) {
        response.header(name).ifPresent(v -> fail(s"expected no header $name but was $v"));
        return this;
    }

    public ResponseAssert hasBody(String expected) {
        String actual = text();
        if (!actual.equals(expected)) fail(s"expected body \"$expected\" but was \"$actual\"");
        return this;
    }

    public ResponseAssert hasBodyContaining(String part) {
        String actual = text();
        if (!actual.contains(part)) fail(s"expected body containing \"$part\" but was \"$actual\"");
        return this;
    }

    public ResponseAssert hasEmptyBody() {
        if (bytes().length != 0) fail(s"expected an empty body but was \"${text()}\"");
        return this;
    }

    /** The response under test, for checks these methods don't cover. */
    public Response response() {
        return response;
    }

    /** The body as UTF-8 text. */
    public String text() {
        return new String(bytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] bytes() {
        if (body == null) body = response.body().bytes();
        return body;
    }

    private void fail(String message) {
        throw new AssertionError(s"$message ($response)");
    }
}
