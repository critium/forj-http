package forj.http;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A media type, as in a {@code Content-Type} header: {@code text/plain; charset=utf-8}. The
 * type, subtype and parameter names are lower-cased; parameter values are kept as written.
 */
public record MediaType(String type, String subtype, Map<String, String> parameters) {
    public static final MediaType TEXT_PLAIN = new MediaType("text", "plain", Map.of("charset", "utf-8"));
    public static final MediaType OCTET_STREAM = new MediaType("application", "octet-stream", Map.of());
    public static final MediaType JSON = new MediaType("application", "json", Map.of());
    public static final MediaType PROBLEM_JSON = new MediaType("application", "problem+json", Map.of());

    public MediaType {
        type = Objects.requireNonNull(type, "type").toLowerCase(Locale.ROOT);
        subtype = Objects.requireNonNull(subtype, "subtype").toLowerCase(Locale.ROOT);
        parameters = Map.copyOf(parameters);
    }

    /**
     * Parses a {@code Content-Type} value. Quoted parameter values lose their quotes.
     *
     * @throws IllegalArgumentException if it isn't {@code type/subtype[; name=value]*}
     */
    public static MediaType parse(String value) {
        String[] parts = value.split(";");
        String essence = parts[0].strip();
        int slash = essence.indexOf('/');
        if (slash <= 0 || slash == essence.length() - 1 || essence.indexOf('/', slash + 1) >= 0) {
            throw new IllegalArgumentException(s"not a media type: \"$value\"");
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].strip();
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            if (eq <= 0) throw new IllegalArgumentException(s"bad media type parameter \"$p\" in \"$value\"");
            String v = p.substring(eq + 1).strip();
            if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
            params.put(p.substring(0, eq).strip().toLowerCase(Locale.ROOT), v);
        }
        return new MediaType(essence.substring(0, slash), essence.substring(slash + 1), params);
    }

    /**
     * The {@code charset} parameter.
     *
     * @throws java.nio.charset.IllegalCharsetNameException or
     *     {@link java.nio.charset.UnsupportedCharsetException} for a charset the JVM lacks
     */
    public Optional<Charset> charset() {
        String name = parameters.get("charset");
        return name == null ? Optional.empty() : Optional.of(Charset.forName(name));
    }

    /** Same type and subtype; parameters are ignored, and {@code *} matches anything. */
    public boolean matches(MediaType other) {
        return (type.equals("*") || other.type.equals("*") || type.equals(other.type))
                && (subtype.equals("*") || other.subtype.equals("*") || subtype.equals(other.subtype));
    }

    /** This type with {@code name} set to {@code value}. */
    public MediaType withParameter(String name, String value) {
        Map<String, String> params = new LinkedHashMap<>(parameters);
        params.put(name.toLowerCase(Locale.ROOT), value);
        return new MediaType(type, subtype, params);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(type).append('/').append(subtype);
        parameters.forEach((k, v) -> sb.append("; ").append(k).append('=').append(v));
        return sb.toString();
    }
}
