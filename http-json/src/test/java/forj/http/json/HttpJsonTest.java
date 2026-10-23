package forj.http.json;

import static forj.http.Dsl.*;
import static forj.http.json.HttpJson.*;
import static forj.http.middleware.Middlewares.*;
import static forj.http.testing.HttpTest.assertThat;
import static forj.http.testing.HttpTest.run;
import static org.junit.jupiter.api.Assertions.assertEquals;

import forj.effect.IO;
import forj.http.Body;
import forj.http.EntityDecoder;
import forj.http.HttpApp;
import forj.http.Request;
import forj.http.Routes;
import forj.json.Codec;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class HttpJsonTest {

    record User(long id, String name, List<String> tags) derives Codec {}

    record NewUser(String name) derives Codec {}

    static final Routes<IO> ROUTES = Routes.of(
            GET("/users/1").respond(req -> Ok(json(new User(1, "ana", List.of("admin"))))),
            POST("/users").respond(req -> {
                NewUser body = req.json();
                return Created(json(new User(7, body.name(), List.of())));
            }),
            GET("/hi").respond(req -> Ok("hi")));

    static final HttpApp<IO> APP = ROUTES.orNotFound().through(errors());

    @Test
    void encodesWithTheDerivedCodec() {
        assertThat(run(APP, Request.get("/users/1")))
                .hasStatus(200)
                .hasHeader("Content-Type", "application/json")
                .hasBody("{\"id\":1,\"name\":\"ana\",\"tags\":[\"admin\"]}");
    }

    @Test
    void decodesTheBodyIntoTheTypedLocal() {
        Request post = Request.post("/users").withHeader("Content-Type", "application/json").withBody("{\"name\":\"bo\"}");
        assertThat(run(APP, post))
                .hasStatus(201)
                .hasBody("{\"id\":7,\"name\":\"bo\",\"tags\":[]}");
        // no Content-Type: read as JSON
        assertThat(run(APP, Request.post("/users").withBody("{\"name\":\"cy\"}"))).hasStatus(201);
        // a +json type is JSON too
        assertThat(run(APP, post.withHeader("Content-Type", "application/merge-patch+json"))).hasStatus(201);
    }

    @Test
    void badJsonIs400WithWhereItWentWrong() {
        Request post = Request.post("/users").withHeader("Content-Type", "application/json").withBody("{\"name\":7}");
        String body = assertThat(run(APP, post))
                .hasStatus(400)
                .hasHeader("Content-Type", "application/problem+json")
                .text();
        assertEquals(true, body.contains("invalid JSON") && body.contains("$.name"), body);
        assertThat(run(APP, post.withBody(""))).hasStatus(400);
    }

    @Test
    void aBodyThatIsntJsonIs415() {
        Request form = Request.post("/users").withHeader("Content-Type", "application/x-www-form-urlencoded").withBody("name=ana");
        assertThat(run(APP, form)).hasStatus(415);
    }

    @Test
    void textStaysText() {
        assertThat(run(APP, Request.get("/hi"))).hasHeader("Content-Type", "text/plain; charset=utf-8").hasBody("hi");
    }

    @Test
    void theMostSpecificJsonWinsForARequest() {
        Request post = Request.post("/").withBody(Body.of(new ByteArrayInputStream("{\"name\":\"ed\"}".getBytes()), -1));
        NewUser fromStream = post.json();
        assertEquals(new NewUser("ed"), fromStream);
    }

    @Test
    void aLocalGivenMakesAsReadJson() {
        given EntityDecoder<NewUser> users = HttpJson.decoder();
        NewUser user = Request.post("/").withBody("{\"name\":\"di\"}").as();
        assertEquals(new NewUser("di"), user);
    }
}
