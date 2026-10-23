# forj-http

An http4s-style server framework for [forj](https://github.com/critium/forj): routes are values, middleware is
function composition, codecs are type classes, and everything is generic in `F` (with `IO` as
the default). Each request runs on its own virtual thread, with no reactive machinery.

API docs (javadoc): https://critium.github.io/forj-http/api/, and for the JSON bridge
https://critium.github.io/forj-http/json/

**Status:** `forj-http` and `forj-http-json` (`0.1.0-SNAPSHOT`, published to a local Maven
repository with `./mill __.publishM2Local`): the model, routing, entity codecs, middleware, test
helpers, the `Backend` SPI and the JSON bridge. Backends live in
[forj-http-plugins](https://github.com/critium/forj-http-plugins): a Helidon WebServer 4 backend,
a runnable users example and the end-to-end benchmark.

```java
import static forj.http.Dsl.*;
import static forj.http.json.HttpJson.*;
import static forj.http.middleware.Middlewares.*;

record User(long id, String name) derives Codec {}
record NewUser(String name) derives Codec {}

static <F<_>> Routes<F> users(UserRepo<F> repo)(using Functor<F> functor) {
    Path1<Long> user = Path.of("/users").then(LongVar);
    return Routes.of(
            GET("/health").respond(req -> Ok("up")),                          // text/plain
            GET(user).to((req, id) -> repo.find(id).map(u -> Ok(json(u)))),  // application/json
            POST("/users").to(req -> {
                NewUser body = req.json();                                    // 400 if it isn't one
                return repo.add(body).map(u -> Created(json(u)));
            }));
}

Routes<IO> routes = users(repo);                                // F chosen here
Response r = HttpTest.run(routes, Request.get("/users/1"));     // no server needed

HttpApp<IO> app = routes.orNotFound()                           // 404 for the rest
        .through(errors())                                      // failures -> problem+json
        .through(timeout(Duration.ofSeconds(10)))               // 503, handler cancelled
        .through(requestId())                                   // X-Request-Id, RequestContext
        .through(accessLog());                                  // one line per request
try (Server server = HelidonBackend.create().start(ServerConfig.fromEnvironment(), app)) {
    server.await();                                             // HelidonBackend: forj-http-helidon
}
```

- **Routing:**
  - typed paths (`Path0`..`Path4`): `IntVar`, `LongVar`, `UuidVar`, `StringVar`, or
    `Var.of(name, parser)`; a segment that doesn't parse is no match;
  - two handler forms, never overloads: `to` returns `F<Response>`, `respond` returns a plain
    `Response`;
  - a trie: a literal beats a typed segment, otherwise the route given first wins; a wrong
    method gets 405 with `Allow`;
  - `a.orElse(b)` merges tries; `routes.through(m)` wraps one group's handlers. A group of
    only `respond` routes that's chained straight away needs `Routes.<IO>of(...)`.
- **Entities:** `Ok(value)`, `Created(...)`, `NotFound()`, ... pick an `EntityEncoder` by the
  value's type (`String` is `text/plain; charset=utf-8`, `byte[]` is octet-stream);
  `User u = req.as()` picks an `EntityDecoder`. A bad body throws `DecodeFailure` (400, or 415
  for an unknown charset). Your own encoders are givens next to your types. No JSON here: that's
  `http-json`, explicitly. A body can also be streamed (`Body.writer`); it's sent chunked,
  since its length isn't known.
- **JSON** (`http-json`, `import static forj.http.json.HttpJson.*`), on
  [forj-codec](https://github.com/critium/forj-codec):
  - `Ok(json(user))` encodes with the type's `Codec` (`record User(...) derives Codec`), as
    bytes with a `Content-Length`;
  - `NewUser body = req.json();` decodes; bad JSON is a 400 problem saying what's wrong and
    where (`$.name`), and a `Content-Type` that isn't JSON is a 415 (no `Content-Type` is read
    as JSON);
  - `HttpJson.encoder()` / `decoder()` make `EntityEncoder`/`EntityDecoder` givens, for an
    app that wants a type to always be JSON;
  - it's explicit at each use, so `Ok("hi")` stays `text/plain`.
- **Middleware** (`forj.http.middleware.Middlewares`), each generic in `F`:
  - `errors()` / `errors(report)` (`MonadError`): `HttpFailure`s keep their status and
    detail; anything else is a 500 whose cause is reported, never sent;
  - `requestId()` (`Functor`): keeps a sensible incoming `X-Request-Id` or makes one, echoes
    it, and puts a `RequestContext` on the request for handlers to use as a context parameter;
  - `accessLog()` / `accessLog(sink)` (`Sync`): `GET /users/7 200 1.4ms id=...`;
  - `timeout(limit)` (`Concurrent`): 503 at the limit, and the handler is cancelled;
  - `cors(policy)` (`Applicative`): preflights answered, responses decorated.
- **Backends** implement `forj.http.server.Backend`: `start(ServerConfig, HttpApp<F>)(using
  Effect<F>)` returns a `Server` that drains on `close()`. `ServerConfig.fromEnvironment()`
  reads `forj.http.port` / `FORJ_HTTP_PORT` and friends.

## Performance

On Helidon, forj-http is within 1% of raw Helidon SE: about 105k requests/s for plaintext, JSON
and a user lookup, with each server capped at 4 cores (Apple M2 Pro, load from `oha` on the same
machine). The same run puts http4s on Ember 24–35% behind with twice the p99 latency, and Spring
Boot MVC 10–28% behind. The method, every number and the caveats are in forj-http-plugins'
[benchmarks](https://github.com/critium/forj-http-plugins/blob/main/docs/benchmarks.md).

JSON sent as bytes (`json(...)`) is the fast path. Streaming JSON straight to the connection was
41% slower on small bodies (63k against 107k requests/s), so streaming is for large bodies.

## Modules

| Module | Artifact | Contents |
|---|---|---|
| `http` | `forj-http` (published) | model, routing, middleware, entity codecs, `forj.http.testing`, `Backend` SPI; no engine, no JSON |
| `http-json` | `forj-http-json` (published) | the `json(...)` / `req.json()` bridge to forj-codec |

Backends (Helidon first), the examples and the end-to-end benchmark live in
[forj-http-plugins](https://github.com/critium/forj-http-plugins).

## Building

forj-http builds against forj (and, for `http-json`, forj-codec) published to `~/.m2`, on the JDK
build forj is pinned to.

```sh
bin/fetch-jdk                                   # once: the pinned JDK 28 EA in .jdk/
(cd ../../forj && ./mill __.publishM2Local)     # after any forj change
(cd ../codec && ./mill __.publishM2Local)       # after any forj-codec change
./mill __.test
./mill __.publishM2Local                        # for downstream repos (forj-http-plugins)
```
