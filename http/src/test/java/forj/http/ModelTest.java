package forj.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModelTest {

    @Test
    void uriSplitsPathIntoDecodedSegments() {
        assertEquals(List.of(), Uri.parse("/").segments());
        assertEquals(List.of(), Uri.parse("").segments());
        assertEquals(List.of("users", "7"), Uri.parse("/users/7").segments());
        assertEquals(List.of("users", ""), Uri.parse("/users/").segments());
        assertEquals(List.of("a b", "c/d", "é"), Uri.parse("/a%20b/c%2Fd/%C3%A9").segments());
        assertEquals(List.of("a+b"), Uri.parse("/a+b").segments(), "+ is literal in a path");
        assertEquals(List.of("100%", "%zz"), Uri.parse("/100%/%zz").segments(), "malformed escapes are kept");
    }

    @Test
    void uriKeepsRawPathAndQuery() {
        Uri uri = Uri.parse("/a%20b?x=1&y=two#frag");
        assertEquals("/a%20b", uri.path());
        assertEquals("x=1&y=two", uri.rawQuery());
        assertEquals("/a%20b?x=1&y=two", uri.toString());
        assertEquals(Uri.of("/a%20b", "x=1&y=two"), uri);
        assertEquals("", Uri.of("/a", null).rawQuery());
    }

    @Test
    void queryDecodesAndKeepsRepeats() {
        Query q = Uri.parse("/?a=1&b=x+y&a=2&flag&&c=%26").query();
        assertEquals(Optional.of("1"), q.get("a"));
        assertEquals(List.of("1", "2"), q.all("a"));
        assertEquals(Optional.of("x y"), q.get("b"));
        assertEquals(Optional.of(""), q.get("flag"));
        assertEquals(Optional.of("&"), q.get("c"));
        assertEquals(Optional.empty(), q.get("missing"));
        assertEquals(List.of("a", "b", "flag", "c"), List.copyOf(q.names()));
        assertTrue(Uri.parse("/").query().isEmpty());
    }

    @Test
    void headersAreCaseInsensitiveOrderedAndImmutable() {
        Headers h = Headers.of("Content-Type", "text/plain", "Set-Cookie", "a=1");
        Headers more = h.add("set-cookie", "b=2");
        assertEquals(Optional.of("text/plain"), more.get("content-type"));
        assertEquals(List.of("a=1", "b=2"), more.all("SET-COOKIE"));
        assertEquals(2, h.size(), "add returns a copy");
        Headers replaced = more.with("Set-Cookie", "c=3");
        assertEquals(List.of("c=3"), replaced.all("set-cookie"));
        assertFalse(replaced.without("SET-COOKIE").contains("set-cookie"));
        assertEquals(List.of("Content-Type", "Set-Cookie"), List.copyOf(more.names()));
    }

    @Test
    void headersRejectLineBreaks() {
        assertThrows(IllegalArgumentException.class, () -> Headers.EMPTY.add("X-A", "1\r\nX-Injected: 2"));
        assertThrows(IllegalArgumentException.class, () -> Headers.of("X\nA", "1"));
        assertThrows(IllegalArgumentException.class, () -> Headers.of("only-a-name"));
    }

    @Test
    void attributesAreTypedByKeyIdentity() {
        Attributes.Key<String> user = Attributes.Key.of("user");
        Attributes.Key<String> otherUser = Attributes.Key.of("user");
        Attributes a = Attributes.EMPTY.with(user, "ana");
        assertEquals(Optional.of("ana"), a.get(user));
        assertEquals(Optional.empty(), a.get(otherUser), "same name, different key");
        assertEquals(Optional.of("bo"), a.with(user, "bo").get(user));
        assertEquals(Optional.of("ana"), a.get(user), "with returns a copy");
        assertTrue(a.without(user).isEmpty());
    }

    @Test
    void statusAndMethodReuseConstants() {
        assertSame(Status.NOT_FOUND, Status.of(404));
        assertEquals(new Status(299, ""), Status.of(299));
        assertEquals("404 Not Found", Status.NOT_FOUND.toString());
        assertThrows(IllegalArgumentException.class, () -> Status.of(99));
        assertSame(Method.GET, Method.of("GET"));
        assertEquals("PROPFIND", Method.of("PROPFIND").name());
    }

    @Test
    void bodiesReadTheSameWhateverTheirForm() throws Exception {
        byte[] bytes = "héllo".getBytes(StandardCharsets.UTF_8);
        List<Body> bodies = List.of(
                Body.of("héllo"),
                Body.of(new ByteArrayInputStream(bytes), bytes.length),
                Body.writer(out -> out.write(bytes), -1));
        for (Body body : bodies) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            body.writeTo(out);
            assertEquals("héllo", out.toString(StandardCharsets.UTF_8), body.toString());
        }
        assertEquals("héllo", Body.of("héllo").text());
        assertEquals(6, Body.of("héllo").length());
        assertSame(Body.EMPTY, Body.of(new byte[0]));
    }

    @Test
    void requestsAndResponsesCopyOnWrite() {
        Attributes.Key<Integer> n = Attributes.Key.of("n");
        Request r = Request.post("/users?dry=1").withHeader("X-Id", "7").withBody("{}").withAttribute(n, 3);
        assertEquals(Method.POST, r.method());
        assertEquals(Optional.of("1"), r.query("dry"));
        assertEquals(Optional.of("7"), r.header("x-id"));
        assertEquals("{}", r.body().text());
        assertEquals(Optional.of(3), r.attribute(n));
        assertEquals(Method.GET, r.withMethod(Method.GET).method());
        assertEquals(Method.POST, r.method());

        Response res = Response.of(Status.CREATED).withHeader("Location", "/users/1").withBody("ok");
        assertEquals(Status.CREATED, res.status());
        assertEquals(Optional.of("/users/1"), res.header("location"));
        assertEquals("ok", res.body().text());
    }
}
