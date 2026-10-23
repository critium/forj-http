package forj.http.server;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Where and how a {@link Backend} serves. {@link #defaults()} suits development;
 * {@link #fromEnvironment()} reads each setting from a system property or an environment
 * variable, so a deployment configures it without code:
 *
 * <table>
 *   <caption>Settings</caption>
 *   <tr><th>Property</th><th>Variable</th><th>Default</th><th>Format</th></tr>
 *   <tr><td>{@code forj.http.host}</td><td>{@code FORJ_HTTP_HOST}</td><td>{@code 0.0.0.0}</td><td>host or address</td></tr>
 *   <tr><td>{@code forj.http.port}</td><td>{@code FORJ_HTTP_PORT}</td><td>{@code 8080}</td><td>0 to 65535; 0 picks a free port</td></tr>
 *   <tr><td>{@code forj.http.maxBodySize}</td><td>{@code FORJ_HTTP_MAXBODYSIZE}</td><td>{@code 10MB}</td><td>bytes, or with {@code KB}/{@code MB}/{@code GB}</td></tr>
 *   <tr><td>{@code forj.http.idleTimeout}</td><td>{@code FORJ_HTTP_IDLETIMEOUT}</td><td>{@code 60s}</td><td>{@code 500ms}, {@code 30s}, {@code 2m}, or ISO-8601 ({@code PT30S})</td></tr>
 *   <tr><td>{@code forj.http.shutdownGrace}</td><td>{@code FORJ_HTTP_SHUTDOWNGRACE}</td><td>{@code 10s}</td><td>as above</td></tr>
 * </table>
 *
 * A system property wins over the variable. A value that doesn't parse is an error naming the
 * setting, not a silent default.
 *
 * @param maxBodyBytes  the largest request body accepted; larger ones get 413
 * @param idleTimeout   how long a kept-alive connection may sit idle
 * @param shutdownGrace how long {@link Server#close()} waits for requests in flight
 */
public record ServerConfig(String host, int port, long maxBodyBytes, Duration idleTimeout, Duration shutdownGrace) {

    public ServerConfig {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(idleTimeout, "idleTimeout");
        Objects.requireNonNull(shutdownGrace, "shutdownGrace");
        if (port < 0 || port > 65535) throw new IllegalArgumentException(s"port out of range: $port");
        if (maxBodyBytes < 0) throw new IllegalArgumentException(s"negative maxBodyBytes: $maxBodyBytes");
    }

    public static ServerConfig defaults() {
        return new ServerConfig("0.0.0.0", 8080, 10L * 1024 * 1024, Duration.ofSeconds(60), Duration.ofSeconds(10));
    }

    /** The defaults, overridden by system properties and environment variables (see the class comment). */
    public static ServerConfig fromEnvironment() {
        return from(key -> {
            String property = System.getProperty(key);
            return property != null ? property : System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        });
    }

    /** The defaults, overridden by whatever {@code lookup} returns (non-null) for each property name. */
    public static ServerConfig from(Function<String, String> lookup) {
        ServerConfig d = defaults();
        return new ServerConfig(
                setting(lookup, "forj.http.host", d.host, s -> s),
                setting(lookup, "forj.http.port", d.port, Integer::parseInt),
                setting(lookup, "forj.http.maxBodySize", d.maxBodyBytes, ServerConfig::parseSize),
                setting(lookup, "forj.http.idleTimeout", d.idleTimeout, ServerConfig::parseDuration),
                setting(lookup, "forj.http.shutdownGrace", d.shutdownGrace, ServerConfig::parseDuration));
    }

    public ServerConfig withHost(String host) {
        return new ServerConfig(host, port, maxBodyBytes, idleTimeout, shutdownGrace);
    }

    public ServerConfig withPort(int port) {
        return new ServerConfig(host, port, maxBodyBytes, idleTimeout, shutdownGrace);
    }

    public ServerConfig withMaxBodyBytes(long maxBodyBytes) {
        return new ServerConfig(host, port, maxBodyBytes, idleTimeout, shutdownGrace);
    }

    public ServerConfig withIdleTimeout(Duration idleTimeout) {
        return new ServerConfig(host, port, maxBodyBytes, idleTimeout, shutdownGrace);
    }

    public ServerConfig withShutdownGrace(Duration shutdownGrace) {
        return new ServerConfig(host, port, maxBodyBytes, idleTimeout, shutdownGrace);
    }

    private static <T> T setting(Function<String, String> lookup, String key, T fallback, Function<String, T> parse) {
        String raw = lookup.apply(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return parse.apply(raw.strip());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(s"bad value for $key: \"$raw\"", e);
        }
    }

    /** {@code 1024}, {@code 512KB}, {@code 10MB}, {@code 1GB} (binary multiples; case doesn't matter). */
    static long parseSize(String s) {
        String u = s.toUpperCase(Locale.ROOT);
        long unit = 1;
        if (u.endsWith("KB")) unit = 1L << 10;
        else if (u.endsWith("MB")) unit = 1L << 20;
        else if (u.endsWith("GB")) unit = 1L << 30;
        String digits = unit == 1 ? u : u.substring(0, u.length() - 2).strip();
        return Math.multiplyExact(Long.parseLong(digits), unit);
    }

    /** {@code 500ms}, {@code 30s}, {@code 2m}, {@code 1h}, or ISO-8601 such as {@code PT30S}. */
    static Duration parseDuration(String s) {
        String u = s.toLowerCase(Locale.ROOT);
        if (u.startsWith("p")) return Duration.parse(s);
        if (u.endsWith("ms")) return Duration.ofMillis(Long.parseLong(u.substring(0, u.length() - 2).strip()));
        long n = Long.parseLong(u.substring(0, u.length() - 1).strip());
        return switch (u.charAt(u.length() - 1)) {
            case 's' -> Duration.ofSeconds(n);
            case 'm' -> Duration.ofMinutes(n);
            case 'h' -> Duration.ofHours(n);
            default -> throw new DateTimeParseException("unknown unit", s, s.length() - 1);
        };
    }
}
