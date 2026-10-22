# forj-http

An http4s-style server framework for [forj](../../forj): routes are values, middleware is
function composition, codecs are type classes, and everything is generic in `F` (with `IO` as
the default). Each request runs on its own virtual thread, with no reactive machinery. The full
plan is in [`../planning`](../planning/lets-make-something-meaningfully-warm-lark.md).

**Status:** build skeleton only. Next: `Effect` in forj core, then the `http` model and routing.

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
