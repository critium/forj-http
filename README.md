# forj-http

An http4s-style server framework for [forj](../../forj): routes are values, middleware is
function composition, codecs are type classes, and everything is generic in `F` (with `IO` as
the default). Each request runs on its own virtual thread, with no reactive machinery. The full
plan is in [`../planning`](../planning/lets-make-something-meaningfully-warm-lark.md).

**Status:** the model, routing and test helpers are in (step 2). Next: middleware, entity
codecs and the `Backend` SPI (step 3), then the Helidon backend in `../plugins`.

```java
import static forj.http.Dsl.*;

static <F<_>> Routes<F> users(UserRepo<F> repo)(using Sync<F> sync) {
    Path1<Long> user = Path.of("/users").then(LongVar);
    return Routes.of(
            GET("/health").respond(req -> Response.of(Status.OK)),
            GET(user).to((req, id) -> repo.find(id).map(u -> ...)));
}

Routes<IO> routes = users(repo);                       // F chosen here
Response r = HttpTest.run(routes, Request.get("/users/1"));   // no server needed
HttpApp<IO> app = HttpApp.of(routes).through(errors);  // what a backend serves; 404 for the rest
```

- **Typed paths** (`Path0`..`Path4`): `IntVar`, `LongVar`, `UuidVar`, `StringVar`, or
  `Var.of(name, parser)`. A segment that doesn't parse is no match.
- **Two handler forms, never overloads:** `to` returns `F<Response>`, `respond` returns a plain
  `Response`.
- **Matching** is a trie: a literal beats a typed segment, otherwise the route given first
  wins. A wrong method gets 405 with `Allow`.
- **Groups:** `a.orElse(b)` merges tries; `routes.through(m)` wraps one group's handlers.
  A group of only `respond` routes that's chained straight away needs `Routes.<IO>of(...)`.

## Modules

| Module | Artifact | Contents |
|---|---|---|
| `http` | `forj-http` | model, routing, middleware, entity codecs, `forj.http.testing`, `Backend` SPI; no engine, no JSON |
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
