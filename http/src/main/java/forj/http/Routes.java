package forj.http;

import forj.typeclass.Applicative;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Routes compiled into a path trie: matching costs one step per path segment, however many
 * routes there are, instead of trying each route in turn.
 *
 * <ul>
 *   <li>{@link #of} builds them; {@code F} comes from where the result goes
 *       ({@code Routes<IO> routes = Routes.of(...)}, or a {@code return}). Building needs no
 *       instances: the effect's {@code Applicative} is asked for only when the routes are run
 *       ({@link #orNotFound}, {@link #apply}), where {@code F} is already known. A group of only
 *       {@code respond} routes has no {@code F} in its arguments, so when it's chained
 *       straight away ({@code Routes.<IO>of(...).through(auth)}) name it.</li>
 *   <li>{@link #orElse} merges two groups into one trie; {@link #through} wraps a group's
 *       handlers in middleware and keeps the trie, so wrapped groups still merge.</li>
 *   <li>{@link #orNotFound} makes them an app that answers the rest with 404;
 *       {@link #apply} answers only matching requests.</li>
 * </ul>
 *
 * <p><b>Matching.</b> At each segment, a literal that leads to a match beats typed segments;
 * among typed segments, the route given first wins (a segment can parse as more than one, as
 * {@code 7} does for {@code LongVar} and {@code StringVar}). A dead end backtracks. When routes
 * share a method and path, the first one given wins. A path that matches only under other
 * methods is answered with 405 and an {@code Allow} header. HEAD isn't answered by GET
 * routes: declare it if you serve it.
 */
public final class Routes<F<_>> {

    private static final Object[] NO_CAPTURES = new Object[0];

    private final List<Entry<F>> entries;
    private final Node<F> root = new Node<>();
    private final int maxCaptures;

    private Routes(List<Entry<F>> entries) {
        this.entries = List.copyOf(entries);
        int max = 0;
        for (int i = 0; i < this.entries.size(); i++) {
            Entry<F> e = this.entries.get(i);
            root.insert(e, i, 0);
            max = Math.max(max, (int) e.segments.stream().filter(s -> s instanceof Var).count());
        }
        this.maxCaptures = max;
    }

    /** The routes, in priority order. {@code F} comes from where the result goes. */
    @SafeVarargs
    public static <F<_>> Routes<F> of(Route<F>... routes) {
        return of(List.of(routes));
    }

    public static <F<_>> Routes<F> of(List<Route<F>> routes) {
        List<Entry<F>> entries = new ArrayList<>(routes.size());
        for (Route<F> r : routes) entries.add(new Entry<>(r.method, r.segments, r.endpoint));
        return new Routes<>(entries);
    }

    /** No routes: matches nothing. */
    public static <F<_>> Routes<F> empty() {
        return new Routes<>(List.of());
    }

    /** These routes, then {@code other}'s, in one trie; where both match, these win. */
    public Routes<F> orElse(Routes<F> other) {
        List<Entry<F>> all = new ArrayList<>(entries);
        all.addAll(other.entries);
        return new Routes<>(all);
    }

    /** Every handler here wrapped in {@code middleware}; requests that don't match never reach it. */
    public Routes<F> through(Middleware<F> middleware) {
        List<Entry<F>> wrapped = new ArrayList<>(entries.size());
        for (Entry<F> e : entries) {
            Route.Endpoint<F> inner = e.endpoint;
            Route.Endpoint<F> outer = (request, captures, ap) ->
                    middleware.apply(r -> inner.run(r, captures, ap)).run(request);
            wrapped.add(new Entry<>(e.method, e.segments, outer));
        }
        return new Routes<>(wrapped);
    }

    /** The response for a matching request (405 if only the method is wrong), or empty. */
    public Optional<F<Response>> apply(Request request)(using Applicative<F> ap) {
        return Optional.ofNullable(find(request, ap));
    }

    /** An app answering requests that match no route with 404: what a backend serves. */
    public HttpApp<F> orNotFound()(using Applicative<F> ap) {
        F<Response> notFound = ap.pure(Response.of(Status.NOT_FOUND));
        return request -> {
            F<Response> response = find(request, ap);
            return response != null ? response : notFound;
        };
    }

    /** {@code GET /users/{long}}, one per route, in priority order. */
    public List<String> describe() {
        return entries.stream().map(e -> s"${e.method} ${Path.show(e.segments)}").collect(Collectors.toList());
    }

    private F<Response> find(Request request, Applicative<F> ap) {
        Matcher<F> m = new Matcher<>(request.method(), request.uri().segments(),
                maxCaptures == 0 ? NO_CAPTURES : new Object[maxCaptures]);
        if (m.search(root, 0, 0)) return m.best.endpoint.run(request, m.bestCaptures, ap);
        if (m.allowed != null) {
            String allow = m.allowed.stream().map(Method::name).collect(Collectors.joining(", "));
            return ap.pure(Response.of(Status.METHOD_NOT_ALLOWED).withHeader("Allow", allow));
        }
        return null;
    }

    @Override
    public String toString() {
        return s"Routes${describe()}";
    }

    private static final class Entry<F<_>> {
        final Method method;
        final List<Segment> segments;
        final Route.Endpoint<F> endpoint;

        Entry(Method method, List<Segment> segments, Route.Endpoint<F> endpoint) {
            this.method = method;
            this.segments = segments;
            this.endpoint = endpoint;
        }
    }

    /** An entry where its path ends, with its priority (its index among the routes). */
    private record Leaf<F>(Entry<F> entry, int priority) {}

    private static final class Node<F<_>> {
        Map<String, Node<F>> literals;                 // null until needed
        final List<Var<?>> vars = new ArrayList<>(0);
        final List<Node<F>> varNodes = new ArrayList<>(0);
        Map<Method, Leaf<F>> endpoints;                // null until needed

        void insert(Entry<F> e, int priority, int depth) {
            if (depth == e.segments.size()) {
                if (endpoints == null) endpoints = new LinkedHashMap<>();
                endpoints.putIfAbsent(e.method, new Leaf<>(e, priority));
                return;
            }
            Node<F> child;
            switch (e.segments.get(depth)) {
                case Segment.Literal l -> {
                    if (literals == null) literals = new HashMap<>();
                    child = literals.computeIfAbsent(l.text(), k -> new Node<>());
                }
                case Var<?> v -> {
                    int i = vars.indexOf(v);
                    if (i < 0) {
                        vars.add(v);
                        varNodes.add(child = new Node<>());
                    } else {
                        child = varNodes.get(i);
                    }
                }
            }
            child.insert(e, priority, depth + 1);
        }
    }

    /**
     * One request's walk through the trie. At each node a literal child that leads to a match
     * wins outright; otherwise every typed child that parses is explored, and the match from
     * the route given first wins.
     */
    private static final class Matcher<F<_>> {
        final Method method;
        final List<String> segments;
        final Object[] captures;
        Entry<F> best;
        int bestPriority = Integer.MAX_VALUE;
        Object[] bestCaptures;
        Set<Method> allowed; // methods of paths that matched under another method

        Matcher(Method method, List<String> segments, Object[] captures) {
            this.method = method;
            this.segments = segments;
            this.captures = captures;
        }

        /** Whether a route matched at or below {@code node}; the winner is in {@link #best}. */
        boolean search(Node<F> node, int depth, int captured) {
            if (depth == segments.size()) {
                if (node.endpoints == null) return false;
                Leaf<F> leaf = node.endpoints.get(method);
                if (leaf != null) {
                    if (leaf.priority() < bestPriority) {
                        best = leaf.entry();
                        bestPriority = leaf.priority();
                        bestCaptures = captures.length == 0 ? captures : captures.clone();
                    }
                    return true;
                }
                if (allowed == null) allowed = new LinkedHashSet<>();
                allowed.addAll(node.endpoints.keySet());
                return false;
            }
            String segment = segments.get(depth);
            if (node.literals != null) {
                Node<F> child = node.literals.get(segment);
                if (child != null && search(child, depth + 1, captured)) return true;
            }
            boolean matched = false;
            for (int i = 0; i < node.vars.size(); i++) {
                Object value = node.vars.get(i).parse(segment);
                if (value != null) {
                    captures[captured] = value;
                    matched |= search(node.varNodes.get(i), depth + 1, captured + 1);
                }
            }
            return matched;
        }
    }
}
