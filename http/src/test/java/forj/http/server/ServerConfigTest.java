package forj.http.server;

import static forj.http.Dsl.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import forj.effect.IO;
import forj.http.HttpApp;
import forj.http.Request;
import forj.http.Response;
import forj.http.Routes;
import forj.typeclass.Effect;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ServerConfigTest {

    @Test
    void settingsOverrideDefaults() {
        ServerConfig c = ServerConfig.from(Map.of(
                "forj.http.port", "0",
                "forj.http.maxBodySize", "512KB",
                "forj.http.idleTimeout", "500ms",
                "forj.http.shutdownGrace", "PT2S")::get);
        assertEquals("0.0.0.0", c.host());
        assertEquals(0, c.port());
        assertEquals(512 * 1024, c.maxBodyBytes());
        assertEquals(Duration.ofMillis(500), c.idleTimeout());
        assertEquals(Duration.ofSeconds(2), c.shutdownGrace());
        assertEquals(ServerConfig.defaults(), ServerConfig.from(key -> null));
    }

    @Test
    void sizesAndDurations() {
        assertEquals(10L << 20, ServerConfig.parseSize("10mb"));
        assertEquals(1L << 30, ServerConfig.parseSize("1 GB"));
        assertEquals(123, ServerConfig.parseSize("123"));
        assertEquals(Duration.ofMinutes(2), ServerConfig.parseDuration("2m"));
        assertEquals(Duration.ofHours(1), ServerConfig.parseDuration("1h"));
        assertEquals(Duration.ofSeconds(30), ServerConfig.parseDuration("30s"));
    }

    @Test
    void badValuesNameTheSetting() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ServerConfig.from(Map.of("forj.http.port", "eighty")::get));
        assertTrue(e.getMessage().contains("forj.http.port"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.from(Map.of("forj.http.port", "70000")::get));
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.from(Map.of("forj.http.idleTimeout", "5 fortnights")::get));
    }

    /** A backend with no network: shows what implementations do, and that the SPI resolves its Effect. */
    static final class InMemoryBackend implements Backend {
        Function<Request, Response> handle;

        @Override
        public <F<_>> Server start(ServerConfig config, HttpApp<F> app)(using Effect<F> effect) {
            handle = req -> {
                try {
                    return effect.unsafeRunSync(app.run(req));
                } catch (Exception e) {
                    return Response.of(forj.http.Status.INTERNAL_SERVER_ERROR);
                }
            };
            CountDownLatch stopped = new CountDownLatch(1);
            return new Server() {
                public int port() { return config.port(); }
                public void await() throws InterruptedException { stopped.await(); }
                public void close() { stopped.countDown(); }
            };
        }
    }

    @Test
    void aBackendRunsTheAppWithTheEffectFoundAtTheCallSite() throws Exception {
        Routes<IO> routes = Routes.of(GET("/").respond(req -> Ok("served")));
        InMemoryBackend memory = new InMemoryBackend();
        // Called through the interface: forj reports the implementation's override and the
        // interface method as an ambiguity when called on the class (see forj's TODO).
        Backend backend = memory;
        try (Server server = backend.start(ServerConfig.defaults().withPort(0), routes.orNotFound())) {
            assertEquals(0, server.port());
            assertEquals("served", memory.handle.apply(Request.get("/")).body().text());
            assertEquals(404, memory.handle.apply(Request.get("/nope")).status().code());
        }
    }
}
