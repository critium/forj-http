# AGENTS.md: forj-http

forj-http is an http4s-style server framework written **in forj**, a javac plugin that adds
Scala features to `.java` files. **Code here is not valid plain Java**, so an editor, a linter,
or your own knowledge of Java will flag it as broken. It isn't. The Mill build is the source of
truth: if `./mill __.test` passes, the code is correct.

Two parts:
- **Part 1** is how to read and write forj code. It's copied from forj's own `AGENTS.md`
  (`../../forj/AGENTS.md`, Part 1, as of forj `d4120bd`). When forj changes, resync it from
  there; forj's copy wins.
- **Part 2** is specific to this repo: design rules, layout, and how to work here.

Background: the plan is `../planning/lets-make-something-meaningfully-warm-lark.md`, forj's full
guide is `../../forj/docs/java-developers.md`, and forj's known limits are in
`../../forj/TODO.md`.

## Part 1: code that uses forj

### Never "fix" these: they are forj syntax

| You see | It is | Compiles to |
|---|---|---|
| `forj { x <- fa; y <- fb; } yield x + y` | comprehension (Scala `for`/`yield`) | `forjFlatMap(fa, x -> forjMap(fb, y -> x + y))` with the `Monad<F>` passed in |
| `x <- e;` / `_ <- e;` | generator / generator whose value is discarded | a lambda parameter |
| `guard(cond);` | filter, directly after a generator | `forjFilter` (needs `FunctorFilter<F>`) |
| `f(A a)(using Show<A> s)` | context parameters (Scala 3 `using`) | `f(A a, @forj.Using Show<A> s)` |
| `f(x)(using hex)` at a call | passing an instance explicitly | `f(x, hex)` |
| `f(x)` where `f` has a `using` clause | **instances filled in by the compiler** | `f(x, Show.integer)` |
| `given Show<Integer> integer = ...;` | an instance (a member: `public static` by default) | `@forj.Given public static final Show<Integer> integer = ...;` |
| `given <A> Show<List<A>> list(using Show<A> e) {...}` | an instance built from others | a `@forj.Given` static method |
| `extension <A> String show(A a)(using Show<A> s)` | extension method | `@forj.Extension public static` |
| `money.show()` with no `show` on `Money` | extension call | `Show.show(money, Money.show)` |
| `interface Functor<F<_>>`, `<F<_>> F<A> pure(A a)` | higher-kinded type parameter | `F` is a plain type parameter |
| `F<A>` where `F` is declared `F<_>` | a type applied to a type constructor | `forj.Kind<F, A>` |
| `record User(long id, String name) derives Codec {}` | derived instance (Scala 3 `derives`) | adds `given Codec<User> codec = Codec.product(...)` to the record |
| `s"Hi $name, ${a + b}"` / `f"$x%.2f"` / `raw"C:\$p"` | string interpolation | `+` concatenation / `String.format` / raw text |
| `import static forj.Language.lifting;` | opt-in automatic lifting for this file | nothing; it changes how calls in the file resolve |

The keywords are contextual: `forj`, `given`, `using`, `extension` and `derives` remain
usable as ordinary names elsewhere. Each keyword form has a plain-Java spelling (`@Given`,
`@Using`, `@Extension`, `@Derives(X.class)`) with identical behaviour. Write that spelling
only where a file must stay parseable by plain tools.

### Invisible behaviour to account for when reading code

- **Calls can have arguments you don't see.** Any call to a method with a `using` clause gets
  its instances inserted. To know what runs, find the given. The search goes nearest first,
  and the first level with a match wins; two matches at one level is a compile error:
  1. local `given`s;
  2. the enclosing method's `using` parameters;
  3. the enclosing classes (and their supertypes);
  4. `import static`;
  5. the companions of the required type and its type arguments;
  6. `forj.Instances` (JDK types).
- **`x.m(...)` may not be a method of `x`.** It's an extension call when `x`'s type has no
  `m`. Lookup goes nearest first:
  1. the enclosing classes;
  2. `import static`;
  3. the type classes of givens in scope;
  4. the receiver's class and its type arguments' classes;
  5. `forj.Syntax`, which provides `map`, `flatMap`, `handleErrorWith`, `attempt`,
     `combine`, `sequence` and more on any `F<A>`.

  A real method always wins: `optional.map(f)` is `Optional.map`.
- **`F` is inferred from context,** not only from arguments:
  - a typed local: `IO<Receipt> r = checkout(order);` gives `F = IO`;
  - a `return`, or an assignment;
  - the parameter the call is passed to: `app.through(errors())` on an `HttpApp<IO>`;
  - the receiver of an instance method: `routes.orNotFound()` on a `Routes<IO>` needs
    `Applicative<IO>`.

  It is *not* inferred through a generic outer call (`describe(checkout(order))`), nor for a
  call used as a receiver (`tag("a").andThen(...)`). There, use a typed local or
  `Owner.<IO>checkout(order)`.
- **A `using` clause after varargs moves in front of them** in the compiled signature:
  `of(Route<F>... rs)(using Applicative<F> ap)` is `of(Applicative<F> ap, Route<F>... rs)` to
  plain Java callers and reflection.
- **Lifting**, only in files importing `forj.Language.*`:
  - `liftArguments` wraps a JDK `List`/`Optional`/`Callable` passed where a `Kind` is
    expected;
  - `liftReceivers` lets `list.map(f)` reach `Syntax.map`;
  - `liftResults` unwraps a `Kind` assigned to a JDK type.

### Rules when writing forj code

- Every generator ends with `;`. Generators are top-level statements of the block, never
  inside `if`/loops.
- `guard(...)` must directly follow a generator or another guard.
- No `return` and no `yield` inside a `forj { }` block; the result goes after it,
  `} yield expr`. (`yield` inside a `switch` expression is fine.)
- All generators in one block use the same `F`. Nested blocks may differ.
- Binder names are lambda parameters: they can't reuse an enclosing local's name and can't be
  reassigned.
- Put `using` parameters in their own clause, after the ordinary ones:
  `f(A a)(using Monad<F> m, Show<A> s)`.
- Comprehensions need `Monad<F>`. Ask for the weakest capability that works:
  - `Applicative` with `map2` for independent steps;
  - `Functor` for `map` alone.
- Two `using` overloads must differ in their ordinary parameter types. forj doesn't rank "more
  specific" overloads.
- For `derives X`, the type class `X` must declare:
  - static `field(String, Function<T, F>)(using X<F>)` and
    `product(Function<Object[], T>, Field...)`;
  - optionally `deriveProduct`/`deriveField`, for a generated straight-line encoder.

  Every component needs an `X` given in scope.
- No Jackson by default in forj projects: forj-http's JSON is its own streaming parser, and
  other parsers plug in behind an SPI.

### Reading errors

- `forj: ...` errors come from the plugin and point at your code. Read them first. javac
  often adds a second "cannot be applied to given types" error for the same call, which is
  noise once the `forj:` error is fixed.
- `forj: no given X<T> for m` means no instance of that type is in scope. Either import or
  declare one, or the effect lacks that capability: `FunctorFilter<Callable>` doesn't exist.
- `forj: cannot infer the type of using parameter ...` means `F` has no context to come from.
  Add a typed local, or explicit type arguments.
- `forj: ambiguous givens for ...` lists the clashing instances. Remove one, or pass one
  explicitly with `(using x)`.
- `plug-in not found: Forj` in a build with annotation processors means the plugin is missing
  from the processor path. This repo's `ForjModule` (in `build.mill`) handles it.
- An `IndexOutOfBoundsException` or other crash inside `forj.plugin.*` is a plugin bug, not
  your code. Make a minimal repro, then report it (see Part 2, "When forj gets in the way").
- Editors (IntelliJ, VS Code, Metals) show red on forj syntax and can't navigate into it.
  Ignore them; build instead.

## Part 2: this repo

### What lives where

| Path | Contents |
|---|---|
| `http/src/main/java/forj/http/` | the model (`Request`, `Response`, `Method`, `Status`, `Headers`, `Uri`, `Query`, `Body`, `Attributes`), routing (`Path`, `Path0`..`Path4`, `Segment`, `Var`, `Route`, `Routes`, `Dsl`), `HttpApp`, `Middleware` |
| `http/src/main/java/forj/http/` (entities) | `MediaType`, `Entity`, `EntityEncoder`/`EntityDecoder` (with their default givens), `HttpFailure`, `DecodeFailure`, `Problem` (RFC 9457), `RequestContext`; `Dsl` also has the response helpers (`Ok(...)`, ...) |
| `http/src/main/java/forj/http/middleware/` | `Middlewares` (errors, requestId, accessLog, timeout, cors), `CorsPolicy` |
| `http/src/main/java/forj/http/server/` | the `Backend` SPI: `Backend`, `Server`, `ServerConfig` |
| `http/src/main/java/forj/http/testing/` | `HttpTest.run(...)`, `assertThat(response)`: running routes with no server |
| `http/src/test/java/forj/http/` | tests written as users would write code: `RoutesTest`, `EntityTest`, `middleware/MiddlewaresTest`, `server/ServerConfigTest`; plus `ModelTest`, `VarTest`, `ForjBuildTest` (checks the forj build itself) |
| `build.mill` | `PinnedJdk`, `ForjModule`, `HttpPublish`, `HttpTests`: copied from forj's recipe; keep in step with it |

Planned (see the plan's "Order of work"): `http-json`, the bridge to forj-codec (step 6). Backends (Helidon first), the
examples and the end-to-end benchmark live in `../plugins`, never here.

### Design rules (don't undo these without asking)

- **No reflection, anywhere: an anti-goal.** No `java.lang.reflect`, no `MethodHandles`
  lookups by name, no `ServiceLoader`, no annotation scanning, no runtime bytecode generation.
  Instances and backends are chosen at compile time, through givens.
- **`http` has no engine and no JSON dependency.** Engines come in through the `Backend` SPI;
  JSON through `http-json`, which an app opts into. So `Ok("hi")` stays `text/plain`.
- **Everything is generic in `F`**, with `IO` the default. One request is one virtual thread,
  which runs the `F` program blocking. No reactive machinery, no callbacks, no thread hops.
- **Responses are plain values, never `F`.** Effectful code maps into them:
  `repo.find(id).map(u -> Ok(u))`.
- **Handlers come in two named forms, never overloads:** `to(...)` returns `F<Response>`,
  `respond(...)` returns `Response`. Java can't choose between overloads of implicitly typed
  lambdas by what they return.
- **`Routes.of(...)` takes no instance.** Building routes needs nothing; the effect's
  `Applicative` is asked for when they run (`routes.orNotFound()`, `routes.apply(req)`).
  Endpoints receive it at dispatch, so pure handlers and route middleware need none up front.
  A group of only `respond` routes has no `F` in its arguments, so when it's chained straight
  away, name it: `Routes.<IO>of(...).through(auth)` (plain Java inference, not forj).
- **Routing is a trie,** matched in one step per segment. At each segment a literal that leads
  to a match beats typed segments; among typed segments the route given first wins; a dead end
  backtracks. A path that matches only under other methods is 405 with `Allow`. Keep this
  allocation-light: it's on every request.
- **Immutable model with `with*` copy methods.** Small collections (headers, attributes,
  query) are flat arrays scanned linearly, not maps.
- **Testing helpers have no test-framework dependency:** they throw `AssertionError`.
- **Errors never leak.** Only an `HttpFailure`'s message reaches the client; any other
  exception is a bare 500 problem, and its cause goes to the reporter. `HttpFailure`s carry no
  stack trace (they're outcomes, cheap to throw).
- **Middleware composes outside-in:** each `through` wraps what's before it, so the last one
  sees the request first and the response last. A middleware that needs another's result
  must not depend on the order where it can avoid it (the access log reads the request id
  from the response header, so it works outside `requestId()`).

### Style

- **Use forj where it reads better, which is most places:**
  - `s"..."` instead of `+` concatenation, including `toString` and error messages;
  - extension syntax from `forj.Syntax` on any `F<A>` (`app.run(req).map(...)`), not
    `IO.narrow(...)` or calls on the type class;
  - comprehensions for sequential effects;
  - `using` for instances and for context (a request context, not a parameter threaded by
    hand).
- **Middleware and helpers are generic in `F`** and ask for the weakest capability:
  `<F<_>> Middleware<F> tag(String v)(using Functor<F> functor)`, not `Middleware<IO>`.
- 4-space indentation (forj plans a move to 2 spaces; follow it when it happens).
- **Javadoc is built from the plain-Java view** of the sources (`forjExportedSources` in
  `build.mill`): `F<A>` is `forj.Kind<F, A>` and `using` instances are ordinary parameters.
  So link a method that has a `using` clause by name only, `{@link Middlewares#errors errors}`,
  never with a parameter list like `#errors()`: that signature doesn't exist there, and
  `./mill __.publishM2Local` fails with "reference not found".
- Javadoc on public types and non-trivial methods, saying *why*. One-line comments for
  surprises. Match the surrounding code.

### forj limits currently worked around here

None right now. `../../forj/TODO.md` ("Found while building forj-http") lists the ones met so
far, all fixed.

### When forj gets in the way

forj is young; this repo is where its limits show up. When one does:

1. Confirm it with a minimal repro, outside this repo (the scratchpad), compiled with the
   published plugin.
2. Work around it here in the simplest way that keeps the API right, with a comment saying
   why.
3. Log it in `../../forj/TODO.md`, under "Found while building forj-http": the symptom, the
   repro, a likely fix if you know one, and the workaround's location here. Don't change
   forj's code from this repo without asking.
4. When forj fixes it, drop the workaround.

Time-box hard javac debugging and write down what you found, rather than going deep silently.

### Build and verify

- `bin/fetch-jdk`: run once, or point `.jdk/current` at forj's JDK (same pinned build) and
  run `bin/make-preview-jdk`.
- `./mill __.test` runs all tests. `./mill http.test.testOnly forj.http.RoutesTest` runs one
  class.
- **After forj republishes,** run `./mill clean` first. The snapshot jar keeps its path, and
  Mill tracks it by path, so stale classes can survive otherwise.
- `./mill __.publishM2Local` publishes `forj:forj-http` (`0.1.0-SNAPSHOT`) to `~/.m2` for
  `../plugins`, with a javadoc jar built from the exported sources. Javadoc errors (a broken
  `{@link}`) fail it; warnings don't.
- **Docs' look:** `docStyle` in `build.mill` (copied from forj's recipe) bakes
  critium.github.io's `javadoc.css` into the javadoc jar. Offline, or while the site isn't
  live, it falls back to javadoc's default look with a warning;
  `FORJ_DOCS_STYLE=/Users/pule/Projects/critium/critium.github.io` uses the local checkout.
- **Hosted docs:** `bin/publish-docs` (copied from forj; `MODULES=("http:api")`,
  `PAGES=("README.md")`) builds the GitHub Pages tree in a temp dir: `README.md` is the front
  page, with a tab bar, and the javadoc sits under `/api/`. Pages must deploy from the
  `gh-pages` branch, `/ (root)`. `--push` force-pushes `gh-pages` to
  https://critium.github.io/forj-http/: **only with the user's OK**, and the same for enabling
  Pages. Publish order across repos: forj, codec, server, plugins.

### Working rules

- Commit only when asked.
- Don't modify other repos (`../../forj`, `../codec`, `../plugins`, `../planning`) without
  asking: other sessions work there. Logging a limit in forj's `TODO.md` (above) is the
  exception.
- Every step of the plan ends with `./mill __.test` green. The plan pauses for review after
  step 4 (a server runs) and step 8 (benchmark numbers exist).
- Finishing a change users see: update `README.md` (status and examples) and, if it changes
  the plan, say so.
