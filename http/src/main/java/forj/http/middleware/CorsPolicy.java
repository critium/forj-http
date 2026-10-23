package forj.http.middleware;

import forj.http.Method;
import forj.http.Request;
import forj.http.Response;
import forj.http.Status;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Which cross-origin requests browsers may make, for {@link Middlewares#cors}. Start from
 * {@link #anyOrigin()} or {@link #origins}, then narrow with the {@code with*} methods.
 *
 * @param allOrigins   every origin is allowed ({@code origins} is then ignored)
 * @param origins      the allowed origins, compared exactly ({@code https://app.example.com})
 * @param methods      methods a preflight allows
 * @param headers      request headers a preflight allows; empty allows whatever is asked for
 * @param exposed      response headers scripts may read
 * @param credentials  whether cookies and auth headers may be sent; the response then names the
 *                     origin instead of {@code *}, as browsers require
 * @param maxAge       how long a browser may cache a preflight answer
 */
public record CorsPolicy(boolean allOrigins, Set<String> origins, Set<Method> methods, Set<String> headers,
                         Set<String> exposed, boolean credentials, Duration maxAge) {

    private static final Set<Method> DEFAULT_METHODS =
            Set.of(Method.GET, Method.HEAD, Method.POST, Method.PUT, Method.PATCH, Method.DELETE);

    public CorsPolicy {
        origins = Set.copyOf(origins);
        methods = Set.copyOf(methods);
        headers = Set.copyOf(headers);
        exposed = Set.copyOf(exposed);
        Objects.requireNonNull(maxAge, "maxAge");
    }

    public static CorsPolicy anyOrigin() {
        return new CorsPolicy(true, Set.of(), DEFAULT_METHODS, Set.of(), Set.of(), false, Duration.ofHours(1));
    }

    public static CorsPolicy origins(String... origins) {
        return new CorsPolicy(false, Set.of(origins), DEFAULT_METHODS, Set.of(), Set.of(), false, Duration.ofHours(1));
    }

    public CorsPolicy withMethods(Method... methods) {
        return new CorsPolicy(allOrigins, origins, Set.of(methods), headers, exposed, credentials, maxAge);
    }

    public CorsPolicy withHeaders(String... headers) {
        return new CorsPolicy(allOrigins, origins, methods, Set.of(headers), exposed, credentials, maxAge);
    }

    public CorsPolicy withExposedHeaders(String... exposed) {
        return new CorsPolicy(allOrigins, origins, methods, headers, Set.of(exposed), credentials, maxAge);
    }

    public CorsPolicy withCredentials(boolean credentials) {
        return new CorsPolicy(allOrigins, origins, methods, headers, exposed, credentials, maxAge);
    }

    public CorsPolicy withMaxAge(Duration maxAge) {
        return new CorsPolicy(allOrigins, origins, methods, headers, exposed, credentials, maxAge);
    }

    public boolean allows(String origin) {
        return allOrigins || origins.contains(origin);
    }

    /** {@code response} with the headers that let {@code origin} read it. */
    Response decorate(String origin, Response response) {
        Response r = allowOrigin(origin, response);
        if (credentials) r = r.withHeader("Access-Control-Allow-Credentials", "true");
        if (!exposed.isEmpty()) r = r.withHeader("Access-Control-Expose-Headers", joined(exposed.stream()));
        return r;
    }

    /** The answer to a preflight ({@code OPTIONS} with {@code Access-Control-Request-Method}) from {@code origin}. */
    Response preflight(String origin, Request request) {
        Response r = allowOrigin(origin, Response.of(Status.NO_CONTENT))
                .withHeader("Access-Control-Allow-Methods", joined(methods.stream().map(Method::name)))
                .withHeader("Access-Control-Max-Age", Long.toString(maxAge.toSeconds()))
                .addHeader("Vary", "Access-Control-Request-Method, Access-Control-Request-Headers");
        String allowHeaders = headers.isEmpty()
                ? request.header("Access-Control-Request-Headers").orElse("")
                : joined(headers.stream());
        if (!allowHeaders.isEmpty()) r = r.withHeader("Access-Control-Allow-Headers", allowHeaders);
        if (credentials) r = r.withHeader("Access-Control-Allow-Credentials", "true");
        return r;
    }

    private Response allowOrigin(String origin, Response response) {
        if (allOrigins && !credentials) return response.withHeader("Access-Control-Allow-Origin", "*");
        return response.withHeader("Access-Control-Allow-Origin", origin).addHeader("Vary", "Origin");
    }

    /** Sorted, so headers are stable whatever order the sets iterate in. */
    private static String joined(Stream<String> values) {
        return values.sorted().collect(Collectors.joining(", "));
    }
}
