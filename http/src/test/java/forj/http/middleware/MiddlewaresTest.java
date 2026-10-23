package forj.http.middleware;

import static forj.http.Dsl.*;
import static forj.http.middleware.Middlewares.*;
import static forj.http.testing.HttpTest.assertThat;
import static forj.http.testing.HttpTest.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import forj.effect.IO;
import forj.http.DecodeFailure;
import forj.http.HttpApp;
import forj.http.HttpFailure;
import forj.http.Method;
import forj.http.Request;
import forj.http.RequestContext;
import forj.http.Response;
import forj.http.Routes;
import forj.http.Status;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

@SuppressWarnings("rawtypes")
class MiddlewaresTest {

    static final List<Throwable> reported = new CopyOnWriteArrayList<>();

    /** Routes that fail every way a handler can. Generic, so the same ones run in IO and Callable. */
    static <F<_>> Routes<F> failing()(using forj.typeclass.MonadError<F, Throwable> errors) {
        return Routes.of(
                GET("/thrown").respond(req -> { throw new IllegalStateException("secret detail"); }),
                GET("/raised").to(req -> errors.raiseError(new IllegalStateException("secret detail"))),
                POST("/decode").respond(req -> Ok(req.<String>as())),
                GET("/conflict").respond(req -> { throw new HttpFailure(Status.CONFLICT, "already exists"); }),
                GET("/ok").respond(req -> Ok("fine")));
    }

    // ------------------------------------------------------------------ errors

    @Test
    void errorsBecomeProblemsWithoutLeakingCauses() {
        reported.clear();
        Routes<IO> routes = failing();
        HttpApp<IO> app = routes.orNotFound().through(errorsWith((req, e) -> reported.add(e)));

        for (String path : List.of("/thrown", "/raised")) {
            assertThat(run(app, Request.get(path)))
                    .hasStatus(500)
                    .hasHeader("Content-Type", "application/problem+json")
                    .hasBody("{\"type\":\"about:blank\",\"title\":\"Internal Server Error\",\"status\":500}");
        }
        assertEquals(2, reported.size(), "unexpected errors are reported");
        assertEquals("secret detail", reported.getFirst().getMessage());
    }

    @Test
    void expectedFailuresKeepTheirStatusAndDetail() {
        reported.clear();
        Routes<IO> routes = failing();
        HttpApp<IO> app = routes.orNotFound().through(errorsWith((req, e) -> reported.add(e)));

        assertThat(run(app, Request.get("/conflict"))).hasStatus(Status.CONFLICT).hasBodyContaining("already exists");
        assertThat(run(app, Request.post("/decode").withHeader("Content-Type", "text/plain; charset=nope").withBody("x")))
                .hasStatus(Status.UNSUPPORTED_MEDIA_TYPE)
                .hasBodyContaining("unsupported charset");
        assertThat(run(app, Request.get("/ok"))).hasBody("fine");
        assertTrue(reported.isEmpty(), "client errors aren't reported as server errors");
    }

    @Test
    void errorsWorkInAnyEffectWithMonadError() {
        Routes<Callable> routes = failing();
        HttpApp<Callable> app = routes.orNotFound().through(errorsWith((req, e) -> {}));
        assertThat(run(app, Request.get("/thrown"))).hasStatus(500);
        assertThat(run(app, Request.get("/conflict"))).hasStatus(409);
    }

    @Test
    void withoutErrorsFailuresReachTheCaller() {
        Routes<IO> routes = failing();
        assertThrows(IllegalStateException.class, () -> run(routes, Request.get("/raised")));
    }

    // ------------------------------------------------------------------ request id

    /** Deep in the call stack, with no request in sight: the context arrives through `using`. */
    static String audit(String action)(using RequestContext context) {
        return s"$action by request ${context.requestId()}";
    }

    static final Routes<IO> WHO = Routes.of(GET("/who").respond(req -> {
        given RequestContext context = RequestContext.of(req);
        return Ok(audit("lookup"));
    }));

    @Test
    void requestIdsAreKeptOrMadeAndReachHandlersAsContext() {
        HttpApp<IO> app = WHO.orNotFound().through(requestId());

        Response kept = run(app, Request.get("/who").withHeader("X-Request-Id", "abc-123"));
        assertThat(kept).hasHeader("X-Request-Id", "abc-123").hasBody("lookup by request abc-123");

        Response made = run(app, Request.get("/who"));
        String id = made.header("X-Request-Id").orElseThrow();
        assertTrue(id.matches("[0-9a-f]{16}"), id);
        assertThat(made).hasBody(s"lookup by request $id");

        Response replaced = run(app, Request.get("/who").withHeader("X-Request-Id", "evil\u0000id"));
        assertTrue(replaced.header("X-Request-Id").orElseThrow().matches("[0-9a-f]{16}"));
    }

    // ------------------------------------------------------------------ access log

    @Test
    void accessLogWritesOneLinePerRequestIncludingFailures() {
        List<String> lines = new CopyOnWriteArrayList<>();
        Routes<IO> routes = failing();
        HttpApp<IO> app = routes.orNotFound().through(requestId()).through(accessLogTo(lines::add));

        run(app, Request.get("/ok").withHeader("X-Request-Id", "r1"));
        assertThrows(IllegalStateException.class, () -> run(app, Request.get("/raised")));

        assertEquals(2, lines.size(), lines.toString());
        assertTrue(lines.get(0).matches("GET /ok 200 \\d+\\.\\dms id=r1"), lines.get(0));
        assertTrue(lines.get(1).startsWith("GET /raised error IllegalStateException "), lines.get(1));
    }

    // ------------------------------------------------------------------ timeout

    @Test
    void slowResponsesTimeOutAndTheHandlerIsCancelled() {
        AtomicBoolean finished = new AtomicBoolean();
        Routes<IO> routes = Routes.of(
                GET("/slow").to(req -> IO.sleep(Duration.ofSeconds(5)).map(u -> {
                    finished.set(true);
                    return Ok("late");
                })),
                GET("/fast").respond(req -> Ok("quick")));
        HttpApp<IO> app = routes.orNotFound().through(timeout(Duration.ofMillis(100)));

        long start = System.nanoTime();
        assertThat(run(app, Request.get("/slow"))).hasStatus(Status.SERVICE_UNAVAILABLE).hasBodyContaining("PT0.1S");
        assertTrue(System.nanoTime() - start < Duration.ofSeconds(2).toNanos(), "answered at the limit, not after the handler");
        assertThat(run(app, Request.get("/fast"))).hasBody("quick");
        assertTrue(!finished.get(), "the slow handler was cancelled");
    }

    // ------------------------------------------------------------------ CORS

    static final Routes<IO> API = Routes.of(GET("/data").respond(req -> Ok("data")));

    @Test
    void corsAnswersPreflightsAndDecoratesResponses() {
        HttpApp<IO> app = API.orNotFound().through(cors(CorsPolicy.origins("https://app.example").withHeaders("X-Token")));

        Request preflight = Request.options("/data")
                .withHeader("Origin", "https://app.example")
                .withHeader("Access-Control-Request-Method", "GET");
        assertThat(run(app, preflight))
                .hasStatus(Status.NO_CONTENT)
                .hasHeader("Access-Control-Allow-Origin", "https://app.example")
                .hasHeader("Access-Control-Allow-Methods", "DELETE, GET, HEAD, PATCH, POST, PUT")
                .hasHeader("Access-Control-Allow-Headers", "X-Token")
                .hasHeader("Access-Control-Max-Age", "3600");

        assertThat(run(app, Request.get("/data").withHeader("Origin", "https://app.example")))
                .hasBody("data")
                .hasHeader("Access-Control-Allow-Origin", "https://app.example")
                .hasHeader("Vary", "Origin");

        assertThat(run(app, Request.get("/data").withHeader("Origin", "https://evil.example")))
                .hasBody("data")
                .hasNoHeader("Access-Control-Allow-Origin");
        assertThat(run(app, preflight.withHeader("Origin", "https://evil.example"))).hasStatus(Status.FORBIDDEN);
        assertThat(run(app, Request.get("/data"))).hasNoHeader("Access-Control-Allow-Origin");
    }

    @Test
    void corsWithCredentialsNamesTheOrigin() {
        HttpApp<IO> any = API.orNotFound().through(cors(CorsPolicy.anyOrigin()));
        assertThat(run(any, Request.get("/data").withHeader("Origin", "https://x.example")))
                .hasHeader("Access-Control-Allow-Origin", "*");

        HttpApp<IO> creds = API.orNotFound().through(cors(CorsPolicy.anyOrigin().withCredentials(true)));
        assertThat(run(creds, Request.get("/data").withHeader("Origin", "https://x.example")))
                .hasHeader("Access-Control-Allow-Origin", "https://x.example")
                .hasHeader("Access-Control-Allow-Credentials", "true");
    }

    // ------------------------------------------------------------------ all together

    @Test
    void theRecommendedStack() {
        List<String> lines = new CopyOnWriteArrayList<>();
        Routes<IO> routes = failing();
        HttpApp<IO> app = routes.orElse(WHO).orNotFound()
                .through(errorsWith((req, e) -> {}))
                .through(timeout(Duration.ofSeconds(5)))
                .through(requestId())
                .through(accessLogTo(lines::add));

        Response r = run(app, Request.get("/thrown").withHeader("X-Request-Id", "z9"));
        assertThat(r).hasStatus(500).hasHeader("X-Request-Id", "z9");
        assertThat(run(app, Request.get("/who").withHeader("X-Request-Id", "z10"))).hasBody("lookup by request z10");
        assertTrue(lines.getFirst().matches("GET /thrown 500 \\d+\\.\\dms id=z9"), lines.getFirst());
        assertThat(run(app, Request.of(Method.DELETE, "/ok"))).hasStatus(405);
    }

    @Test
    void decodeFailuresAreClientErrors() {
        DecodeFailure f = new DecodeFailure("missing field 'name' at $");
        assertEquals(Status.BAD_REQUEST, f.status());
        assertEquals(0, f.getStackTrace().length, "no stack trace: cheap to throw");
    }
}
