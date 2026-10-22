package forj.http;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decoded query parameters, in order: {@code a=1&b=x+y&a=2}. A name can repeat; a parameter
 * without {@code =} has the value {@code ""}; {@code +} is a space.
 */
public final class Query {
    public static final Query EMPTY = new Query(new String[0]);

    private final String[] pairs;

    private Query(String[] pairs) {
        this.pairs = pairs;
    }

    /** Parses a raw query string (without the {@code ?}). */
    public static Query parse(String raw) {
        if (raw == null || raw.isEmpty()) return EMPTY;
        List<String> pairs = new ArrayList<>();
        int start = 0;
        while (start <= raw.length()) {
            int amp = raw.indexOf('&', start);
            int end = amp < 0 ? raw.length() : amp;
            if (end > start) {
                int eq = raw.indexOf('=', start);
                if (eq < 0 || eq > end) {
                    pairs.add(Uri.decode(raw.substring(start, end), true));
                    pairs.add("");
                } else {
                    pairs.add(Uri.decode(raw.substring(start, eq), true));
                    pairs.add(Uri.decode(raw.substring(eq + 1, end), true));
                }
            }
            if (amp < 0) break;
            start = amp + 1;
        }
        return new Query(pairs.toArray(String[]::new));
    }

    /** The first value of {@code name}. */
    public Optional<String> get(String name) {
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i].equals(name)) return Optional.of(pairs[i + 1]);
        }
        return Optional.empty();
    }

    /** Every value of {@code name}, in order. */
    public List<String> all(String name) {
        List<String> values = new ArrayList<>(1);
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i].equals(name)) values.add(pairs[i + 1]);
        }
        return List.copyOf(values);
    }

    public boolean contains(String name) {
        return get(name).isPresent();
    }

    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; i < pairs.length; i += 2) names.add(pairs[i]);
        return names;
    }

    public boolean isEmpty() {
        return pairs.length == 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Query q && Arrays.equals(pairs, q.pairs);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pairs);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Query(");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) sb.append(", ");
            sb.append(pairs[i]).append('=').append(pairs[i + 1]);
        }
        return sb.append(')').toString();
    }
}
