package forj.http;

import static forj.http.Dsl.*;
import static forj.http.testing.HttpTest.assertThat;
import static forj.http.testing.HttpTest.run;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import forj.data.Unit;
import forj.effect.IO;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EntityTest {

    record Money(long cents) {
        /** A user-defined encoder, next to the type it encodes: found with no import. */
        given EntityEncoder<Money> text = EntityEncoder.string.contramap(m -> f"$$${m.cents() / 100.0}%.2f");
    }

    @Test
    void responseHelpersPickTheEncoderByType() {
        Response text = Ok("hi");
        assertEquals(Status.OK, text.status());
        assertEquals(Optional.of("text/plain; charset=utf-8"), text.header("Content-Type"));
        assertEquals("hi", text.body().text());

        Response bytes = Created(new byte[] {1, 2});
        assertEquals(Status.CREATED, bytes.status());
        assertEquals(Optional.of("application/octet-stream"), bytes.header("Content-Type"));

        Response unit = Ok(Unit.UNIT);
        assertEquals(Optional.empty(), unit.header("Content-Type"));
        assertEquals(0, unit.body().length());

        assertEquals("$12.50", Ok(new Money(1250)).body().text());
        assertEquals(Status.NO_CONTENT, NoContent().status());
        assertEquals(Optional.of("/users/1"), SeeOther("/users/1").header("Location"));
    }

    @Test
    void aLocalGivenOverridesTheDefault() {
        given EntityEncoder<String> shouting = EntityEncoder.string.contramap(String::toUpperCase);
        assertEquals("HI", Ok("hi").body().text());
    }

    @Test
    void anEncodedEntityPassesThroughAsIs() {
        Entity csv = new Entity(Body.of("a,b"), MediaType.parse("text/csv"));
        Response r = Ok(csv);
        assertEquals(Optional.of("text/csv"), r.header("Content-Type"));
        assertEquals("a,b", r.body().text());
    }

    @Test
    void requestsDecodeByTargetType() {
        Request req = Request.post("/").withEntity("héllo");
        String text = req.as();
        assertEquals("héllo", text);
        assertArrayEquals("héllo".getBytes(StandardCharsets.UTF_8), req.<byte[]>as());
    }

    @Test
    void stringsDecodeInTheDeclaredCharset() {
        Request latin1 = Request.post("/")
                .withHeader("Content-Type", "text/plain; charset=ISO-8859-1")
                .withBody("café".getBytes(StandardCharsets.ISO_8859_1));
        assertEquals("café", latin1.<String>as());

        Request unknown = Request.post("/").withHeader("Content-Type", "text/plain; charset=klingon").withBody("x");
        DecodeFailure f = assertThrows(DecodeFailure.class, () -> unknown.<String>as());
        assertEquals(Status.UNSUPPORTED_MEDIA_TYPE, f.status());

        Request garbage = Request.post("/").withHeader("Content-Type", "nonsense").withBody("x");
        assertEquals(Status.BAD_REQUEST, assertThrows(DecodeFailure.class, () -> garbage.<String>as()).status());
    }

    @Test
    void handlersDecodeAndEncode() {
        Routes<IO> routes = Routes.of(POST("/echo").respond(req -> {
            String body = req.as();
            return Ok(s"you said: $body");
        }));
        assertThat(run(routes, Request.post("/echo").withEntity("hi")))
                .hasStatus(Status.OK)
                .hasHeader("Content-Type", "text/plain; charset=utf-8")
                .hasBody("you said: hi");
    }

    @Test
    void mediaTypesParse() {
        MediaType t = MediaType.parse("Text/HTML; Charset=\"UTF-8\"; level=1");
        assertEquals("text", t.type());
        assertEquals("html", t.subtype());
        assertEquals(Map.of("charset", "UTF-8", "level", "1"), t.parameters());
        assertEquals(Optional.of(StandardCharsets.UTF_8), t.charset());
        assertTrue(MediaType.parse("application/*").matches(MediaType.JSON));
        assertThrows(IllegalArgumentException.class, () -> MediaType.parse("text"));
        assertThrows(IllegalArgumentException.class, () -> MediaType.parse("a/b/c"));
    }

    @Test
    void problemsAreEscapedJson() {
        Problem p = new Problem(Status.BAD_REQUEST, "bad \"name\"\nat $.users[3]");
        assertEquals("{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,"
                + "\"detail\":\"bad \\\"name\\\"\\nat $.users[3]\"}", p.toJson());
        assertEquals("{\"type\":\"about:blank\",\"title\":\"Internal Server Error\",\"status\":500}",
                new Problem(Status.INTERNAL_SERVER_ERROR, null).toJson());
        assertEquals(Optional.of("application/problem+json"), p.toResponse().header("Content-Type"));
    }
}
