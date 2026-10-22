package forj.http;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Immutable HTTP headers: names are case-insensitive, a name can repeat, and order is kept.
 * Stored as one flat array of name/value pairs: a request has a handful of headers, so a
 * linear scan beats hashing and allocates nothing.
 */
public final class Headers {
    public static final Headers EMPTY = new Headers(new String[0]);

    private final String[] pairs;

    private Headers(String[] pairs) {
        this.pairs = pairs;
    }

    /** {@code Headers.of("Content-Type", "text/plain", "X-Id", "7")}. */
    public static Headers of(String... namesAndValues) {
        if (namesAndValues.length % 2 != 0) throw new IllegalArgumentException("names and values must pair up");
        String[] pairs = namesAndValues.clone();
        for (int i = 0; i < pairs.length; i += 2) check(pairs[i], pairs[i + 1]);
        return new Headers(pairs);
    }

    /** The first value of {@code name}. */
    public Optional<String> get(String name) {
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i].equalsIgnoreCase(name)) return Optional.of(pairs[i + 1]);
        }
        return Optional.empty();
    }

    /** Every value of {@code name}, in order. */
    public List<String> all(String name) {
        List<String> values = new ArrayList<>(1);
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i].equalsIgnoreCase(name)) values.add(pairs[i + 1]);
        }
        return List.copyOf(values);
    }

    public boolean contains(String name) {
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i].equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Replaces every value of {@code name} with {@code value}. */
    public Headers with(String name, String value) {
        return without(name).add(name, value);
    }

    /** Appends a value, keeping any existing ones. */
    public Headers add(String name, String value) {
        check(name, value);
        String[] next = Arrays.copyOf(pairs, pairs.length + 2);
        next[pairs.length] = name;
        next[pairs.length + 1] = value;
        return new Headers(next);
    }

    /** Appends all of {@code other}. */
    public Headers addAll(Headers other) {
        if (other.pairs.length == 0) return this;
        if (pairs.length == 0) return other;
        String[] next = Arrays.copyOf(pairs, pairs.length + other.pairs.length);
        System.arraycopy(other.pairs, 0, next, pairs.length, other.pairs.length);
        return new Headers(next);
    }

    public Headers without(String name) {
        if (!contains(name)) return this;
        String[] next = new String[pairs.length];
        int n = 0;
        for (int i = 0; i < pairs.length; i += 2) {
            if (!pairs[i].equalsIgnoreCase(name)) {
                next[n++] = pairs[i];
                next[n++] = pairs[i + 1];
            }
        }
        return new Headers(Arrays.copyOf(next, n));
    }

    /** The distinct names, as first written. */
    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        outer:
        for (int i = 0; i < pairs.length; i += 2) {
            for (String seen : names) {
                if (seen.equalsIgnoreCase(pairs[i])) continue outer;
            }
            names.add(pairs[i]);
        }
        return names;
    }

    /** Calls {@code action} with every name and value, in order. */
    public void forEach(BiConsumer<String, String> action) {
        for (int i = 0; i < pairs.length; i += 2) action.accept(pairs[i], pairs[i + 1]);
    }

    /** The number of name/value pairs. */
    public int size() {
        return pairs.length / 2;
    }

    public boolean isEmpty() {
        return pairs.length == 0;
    }

    /** Rejects what would split a header line: empty names, and CR or LF anywhere. */
    private static void check(String name, String value) {
        if (name == null || value == null) throw new NullPointerException("header name and value must not be null");
        if (name.isEmpty()) throw new IllegalArgumentException("empty header name");
        if (hasLineBreak(name) || hasLineBreak(value)) throw new IllegalArgumentException(s"line break in header $name");
    }

    private static boolean hasLineBreak(String s) {
        return s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Headers h && Arrays.equals(pairs, h.pairs);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pairs);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Headers(");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) sb.append(", ");
            sb.append(pairs[i]).append(": ").append(pairs[i + 1]);
        }
        return sb.append(')').toString();
    }
}
