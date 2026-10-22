package forj.http;

import static forj.http.Dsl.*;
import static forj.http.testing.HttpTest.assertThat;
import static forj.http.testing.HttpTest.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import forj.effect.IO;
import forj.typeclass.Applicative;
import forj.typeclass.Functor;
import forj.typeclass.Sync;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

@SuppressWarnings("rawtypes")
class RoutesTest {

    static Response text(String body) {
        return Response.of(Status.OK).withBody(body);
    }

    static final Path1<Long> USER = Path.of("/users").then(LongVar);

    @Test
    void typedSegmentsReachTheHandler() {
        Routes<IO> routes = Routes.of(
                GET(USER).to((req, id) -> IO.pure(text(s"user $id"))),
                GET(Path.of("/files").then(UuidVar).then("v").then(IntVar))
                        .respond((req, file, version) -> text(s"$file@$version")),
                GET(Path.root()).respond(req -> text("root")));

        assertThat(run(routes, Request.get("/users/42"))).hasStatus(Status.OK).hasBody("user 42");
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertThat(run(routes, Request.get(s"/files/$id/v/3"))).hasBody(s"$id@3");
        assertThat(run(routes, Request.get("/"))).hasBody("root");
    }

    @Test
    void segmentsThatDontParseAreNotFound() {
        Routes<IO> routes = Routes.of(GET(USER).respond((req, id) -> text("found")));
        assertThat(run(routes, Request.get("/users/ana"))).hasStatus(Status.NOT_FOUND);
        assertThat(run(routes, Request.get("/users/1/extra"))).hasStatus(Status.NOT_FOUND);
        assertThat(run(routes, Request.get("/users"))).hasStatus(Status.NOT_FOUND);
        assertThat(run(routes, Request.get("/users/1/"))).hasStatus(Status.NOT_FOUND);
        assertTrue(routes.apply(Request.get("/nope"), IO.concurrent).isEmpty(), "routes alone don't answer what they don't match");
    }

    @Test
    void wrongMethodIs405WithAllow() {
        Routes<IO> routes = Routes.of(
                GET(USER).respond((req, id) -> text("get")),
                DELETE(USER).respond((req, id) -> text("delete")));
        assertThat(run(routes, Request.post("/users/1")))
                .hasStatus(Status.METHOD_NOT_ALLOWED)
                .hasHeader("Allow", "GET, DELETE");
        assertThat(run(routes, Request.delete("/users/1"))).hasBody("delete");
    }

    @Test
    void literalsBeatVarsAndDeadEndsBacktrack() {
        Routes<IO> routes = Routes.of(
                GET(Path.of("/users").then(StringVar).then("posts")).respond((req, name) -> text(s"posts of $name")),
                GET(USER).respond((req, id) -> text(s"id $id")),
                GET("/users/me").respond(req -> text("me")),
                GET(Path.of("/users").then(StringVar)).respond((req, name) -> text(s"name $name")));

        assertThat(run(routes, Request.get("/users/me"))).hasBody("me");
        assertThat(run(routes, Request.get("/users/7"))).hasBody("id 7");
        assertThat(run(routes, Request.get("/users/ana"))).hasBody("name ana");
        // /users/me/posts: the literal "me" branch dead-ends, so the router backtracks to {string}
        assertThat(run(routes, Request.get("/users/me/posts"))).hasBody("posts of me");
    }

    @Test
    void amongTypedSegmentsTheRouteGivenFirstWins() {
        Routes<IO> stringFirst = Routes.of(
                GET(Path.of("/users").then(StringVar)).respond((req, name) -> text("string")),
                GET(USER).respond((req, id) -> text("long")));
        assertThat(run(stringFirst, Request.get("/users/7"))).hasBody("string");
    }

    @Test
    void firstRouteWinsForTheSameMethodAndPath() {
        Routes<IO> routes = Routes.of(
                GET(USER).respond((req, id) -> text("first")),
                GET(USER).respond((req, id) -> text("second")));
        assertThat(run(routes, Request.get("/users/1"))).hasBody("first");
    }

    @Test
    void orElseMergesTriesSo405SeesBothGroups() {
        Routes<IO> reads = Routes.of(GET(USER).respond((req, id) -> text("read")));
        Routes<IO> writes = Routes.of(PUT(USER).respond((req, id) -> text("write")));
        Routes<IO> all = reads.orElse(writes);

        assertThat(run(all, Request.get("/users/1"))).hasBody("read");
        assertThat(run(all, Request.put("/users/1"))).hasBody("write");
        assertThat(run(all, Request.post("/users/1"))).hasStatus(405).hasHeader("Allow", "GET, PUT");
        assertEquals(List.of("GET /users/{long}", "PUT /users/{long}"), all.describe());
    }

    @Test
    void effectsRunOnlyWhenTheResponseIsRun() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Routes<IO> routes = Routes.of(GET("/count").to(req -> IO.delay(() -> text(s"n=${calls.incrementAndGet()}"))));

        HttpApp<IO> app = HttpApp.of(routes);
        IO<Response> pending = IO.narrow(app.run(Request.get("/count")));
        assertEquals(0, calls.get());
        assertEquals("n=1", pending.unsafeRunSync().body().text());
        assertEquals("n=2", pending.unsafeRunSync().body().text(), "an IO runs again from the start");
    }

    // ------------------------------------------------------------------ middleware

    /** Generic in F: `map` on the app's F<Response> is forj.Syntax's extension, backed by the Functor. */
    static <F<_>> Middleware<F> tag(String value)(using Functor<F> functor) {
        return app -> req -> app.run(req).map(res -> res.addHeader("X-Tag", value));
    }

    /** Lets a request through with its bearer token as the {@code user} attribute; 401 without one. */
    static <F<_>> Middleware<F> bearer(Attributes.Key<String> user)(using Applicative<F> ap) {
        return app -> req -> req.header("Authorization")
                .filter(h -> h.startsWith("Bearer "))
                .map(h -> app.run(req.withAttribute(user, h.substring("Bearer ".length()))))
                .orElseGet(() -> ap.pure(Response.of(Status.UNAUTHORIZED)));
    }

    @Test
    void routeMiddlewareWrapsOnlyItsGroupAndKeepsTheTrie() {
        Middleware<IO> adminTag = tag("admin");
        Routes<IO> admin = Routes.<IO>of(GET("/admin/stats").respond(req -> text("stats"))).through(adminTag);
        Routes<IO> open = Routes.of(GET(USER).respond((req, id) -> text("user")));
        HttpApp<IO> app = HttpApp.of(admin.orElse(open));

        assertThat(run(app, Request.get("/admin/stats"))).hasHeader("X-Tag", "admin");
        assertThat(run(app, Request.get("/users/1"))).hasBody("user").hasNoHeader("X-Tag");
        assertThat(run(app, Request.get("/admin/other"))).hasStatus(404).hasNoHeader("X-Tag");
    }

    @Test
    void middlewareCanPassValuesToHandlersAsAttributes() {
        Attributes.Key<String> user = Attributes.Key.of("user");
        Middleware<IO> auth = bearer(user);
        Routes<IO> routes = Routes.<IO>of(
                GET("/me").respond(req -> text(s"hi ${req.attribute(user).orElseThrow()}"))).through(auth);

        assertThat(run(routes, Request.get("/me"))).hasStatus(Status.UNAUTHORIZED);
        assertThat(run(routes, Request.get("/me").withHeader("Authorization", "Bearer ana"))).hasBody("hi ana");
    }

    @Test
    void appMiddlewareComposesOutsideIn() {
        Routes<IO> routes = Routes.of(GET("/").respond(req -> text("ok")));
        Middleware<IO> inner = tag("inner");
        Middleware<IO> outer = tag("outer");
        HttpApp<IO> app = HttpApp.of(routes).through(inner.andThen(outer));
        Response res = run(app, Request.get("/"));
        assertEquals(List.of("inner", "outer"), res.headers().all("X-Tag"), "outer sees the response last");
    }

    // ------------------------------------------------------------------ generic in F

    /** Written once against Sync; the caller picks the effect. */
    static <F<_>> Routes<F> counter(AtomicInteger n)(using Sync<F> sync) {
        return Routes.of(
                GET("/count").to(req -> sync.delay(() -> text(Integer.toString(n.incrementAndGet())))),
                GET("/ping").respond(req -> text("pong")));
    }

    @Test
    void theSameRoutesRunInAnyEffect() {
        AtomicInteger n = new AtomicInteger();
        Routes<IO> io = counter(n);
        Routes<Callable> callable = counter(n);

        assertThat(run(io, Request.get("/count"))).hasBody("1");
        assertThat(run(callable, Request.get("/count"))).hasBody("2");
        assertThat(run(callable, Request.get("/ping"))).hasBody("pong");
        assertThat(run(callable, Request.get("/nope"))).hasStatus(404);
    }

    @Test
    void handlerErrorsSurfaceWhenRun() {
        Routes<IO> routes = Routes.of(GET("/boom").to(req -> IO.raiseError(new IllegalStateException("boom"))));
        IllegalStateException e = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> run(routes, Request.get("/boom")));
        assertEquals("boom", e.getMessage());
    }

    @Test
    void manyRoutesFromAList() {
        List<Route<IO>> many = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String body = s"r$i";
            many.add(GET(s"/r/$i").respond(req -> text(body)));
        }
        Routes<IO> routes = Routes.of(many);
        assertThat(run(routes, Request.get("/r/37"))).hasBody("r37");
    }
}
