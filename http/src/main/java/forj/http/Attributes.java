package forj.http;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed values carried with a request, such as the request id or the authenticated user, set
 * by middleware and read by handlers. Keys compare by identity, so two libraries never clash
 * even if they pick the same name.
 */
public final class Attributes {
    public static final Attributes EMPTY = new Attributes(new Object[0]);

    /** A key for values of type {@code T}; declare it once, as a constant. */
    public static final class Key<T> {
        private final String name;

        private Key(String name) {
            this.name = name;
        }

        public static <T> Key<T> of(String name) {
            return new Key<>(Objects.requireNonNull(name, "name"));
        }

        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return s"Key($name)";
        }
    }

    private final Object[] entries; // key, value, key, value, ...

    private Attributes(Object[] entries) {
        this.entries = entries;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(Key<T> key) {
        for (int i = 0; i < entries.length; i += 2) {
            if (entries[i] == key) return Optional.of((T) entries[i + 1]);
        }
        return Optional.empty();
    }

    /** These attributes with {@code key} set to {@code value}, replacing any earlier value. */
    public <T> Attributes with(Key<T> key, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        for (int i = 0; i < entries.length; i += 2) {
            if (entries[i] == key) {
                Object[] next = entries.clone();
                next[i + 1] = value;
                return new Attributes(next);
            }
        }
        Object[] next = Arrays.copyOf(entries, entries.length + 2);
        next[entries.length] = key;
        next[entries.length + 1] = value;
        return new Attributes(next);
    }

    public Attributes without(Key<?> key) {
        for (int i = 0; i < entries.length; i += 2) {
            if (entries[i] == key) {
                Object[] next = new Object[entries.length - 2];
                System.arraycopy(entries, 0, next, 0, i);
                System.arraycopy(entries, i + 2, next, i, entries.length - i - 2);
                return new Attributes(next);
            }
        }
        return this;
    }

    public boolean isEmpty() {
        return entries.length == 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Attributes(");
        for (int i = 0; i < entries.length; i += 2) {
            if (i > 0) sb.append(", ");
            sb.append(((Key<?>) entries[i]).name()).append('=').append(entries[i + 1]);
        }
        return sb.append(')').toString();
    }
}
