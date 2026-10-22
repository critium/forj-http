package forj.http;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * A typed path segment: {@code Path.of("/users").then(LongVar)} matches {@code /users/7} and
 * hands the handler a {@code long}. A segment that doesn't parse is no match, so the router
 * tries the next route (as in http4s), and with none left the answer is 404, not 400.
 *
 * <p>Built in: {@link #IntVar}, {@link #LongVar}, {@link #UuidVar} (canonical form only) and
 * {@link #StringVar} (any non-empty segment). Make others with {@link #of}. Vars compare by
 * identity: routes share a trie branch only when they use the same {@code Var} instance, so
 * declare custom ones as constants.
 */
public final class Var<A> implements Segment {

    /** Parses a decoded segment; {@code null} means no match. */
    @FunctionalInterface
    interface Parser<A> {
        A parse(String segment);
    }

    public static final Var<Integer> IntVar = new Var<>("int", s -> {
        Long n = parseLong(s);
        return n != null && n == n.intValue() ? n.intValue() : null;
    });
    public static final Var<Long> LongVar = new Var<>("long", Var::parseLong);
    public static final Var<UUID> UuidVar = new Var<>("uuid", Var::parseUuid);
    public static final Var<String> StringVar = new Var<>("string", s -> s.isEmpty() ? null : s);

    private final String name;
    private final Parser<A> parser;

    private Var(String name, Parser<A> parser) {
        this.name = name;
        this.parser = parser;
    }

    /** A custom segment: {@code parse} returns empty when the segment doesn't match. */
    public static <A> Var<A> of(String name, Function<String, Optional<A>> parse) {
        Objects.requireNonNull(parse, "parse");
        return new Var<>(Objects.requireNonNull(name, "name"), s -> parse.apply(s).orElse(null));
    }

    public String name() {
        return name;
    }

    /** The parsed value, or {@code null} when {@code segment} doesn't match. */
    A parse(String segment) {
        return parser.parse(segment);
    }

    /** An optional {@code -} and 1 to 19 digits, in range; no {@code +}, spaces or exceptions. */
    private static Long parseLong(String s) {
        int n = s.length();
        int i = n > 0 && s.charAt(0) == '-' ? 1 : 0;
        if (i == n || n - i > 19) return null;
        long value = 0;
        for (; i < n; i++) {
            int d = s.charAt(i) - '0';
            if (d < 0 || d > 9) return null;
            // accumulate negatively so Long.MIN_VALUE fits
            if (value < Long.MIN_VALUE / 10) return null;
            value *= 10;
            if (value < Long.MIN_VALUE + d) return null;
            value -= d;
        }
        if (s.charAt(0) == '-') return value;
        return value == Long.MIN_VALUE ? null : -value;
    }

    private static UUID parseUuid(String s) {
        if (s.length() != 36) return null;
        for (int i = 0; i < 36; i++) {
            char c = s.charAt(i);
            boolean dash = i == 8 || i == 13 || i == 18 || i == 23;
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (dash ? c != '-' : !hex) return null;
        }
        return UUID.fromString(s);
    }

    @Override
    public String toString() {
        return s"{$name}";
    }
}
