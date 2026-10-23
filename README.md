# forj-http

An http4s-style server framework for [forj](../../forj): routes are values, middleware is
function composition, codecs are type classes, and everything is generic in `F` (with `IO` as
the default). Each request runs on its own virtual thread, with no reactive machinery. The full
plan is in [`../planning`](../planning/lets-make-something-meaningfully-warm-lark.md).

API docs (javadoc): https://critium.github.io/forj-http/api/

**Status:** `forj-http` is complete for milestone 1 and published locally (steps 2 and 3): the
model, routing, entity codecs, middleware, test helpers and the `Backend` SPI. Next: the Helidon
backend in `../plugins` (step 4).

```java
import static forj.http.Dsl.*;
import static forj.http.middleware.Middlewares.*;

static <F<_>> Routes<F> users(UserRepo<F> repo)(using Sync<F> sync) {
    Path1<Long> user = Path.of("/users").then(LongVar);
    return Routes.of(
            GET("/health").respond(req -> Ok("up")),
            GET(user).to((req, id) -> repo.find(id).map(u -> Ok(u.name()))),
            POST("/users").to(req -> repo.create(req.<String>as()).map(id -> Created(s"/users/$id"))));
}

Routes<IO> routes = users(repo);                                // F chosen here
Response r = HttpTest.run(routes, Request.get("/users/1"));     // no server needed

HttpApp<IO> app = routes.orNotFound()                           // 404 for the rest
        .through(errors())                                      // failures -> problem+json
        .through(timeout(Duration.ofSeconds(10)))               // 503, handler cancelled
        .through(requestId())                                   // X-Request-Id, RequestContext
        .through(accessLog());                                  // one line per request
try (Server server = backend.start(ServerConfig.fromEnvironment(), app)) { server.await(); }
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
  `http-json`, explicitly.
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

## Modules

| Module | Artifact | Contents |
|---|---|---|
| `http` | `forj-http` (published) | model, routing, middleware, entity codecs, `forj.http.testing`, `Backend` SPI; no engine, no JSON |
| `http-json` (planned) | `forj-http-json` | the `json(...)` / `req.json()` bridge to forj-codec |

Backends (Helidon first), the examples and the end-to-end benchmark live in
`../plugins` (forj-http-plugins).

## Building

forj-http builds against forj published to `~/.m2`, on the JDK build forj is pinned to.

```sh
bin/fetch-jdk                                   # once: the pinned JDK 28 EA in .jdk/
(cd ../../forj && ./mill __.publishM2Local)     # after any forj change
./mill __.test
./mill __.publishM2Local                        # for downstream repos (plugins/)
```
