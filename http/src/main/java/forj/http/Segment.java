package forj.http;

import java.util.Objects;

/** One segment of a route's path: a fixed {@link Literal} or a typed {@link Var}. */
public sealed interface Segment permits Segment.Literal, Var {

    /** Matches exactly this (decoded) text. */
    record Literal(String text) implements Segment {
        public Literal {
            Objects.requireNonNull(text, "text");
            if (text.isEmpty() || text.indexOf('/') >= 0) {
                throw new IllegalArgumentException(s"a literal segment is non-empty and has no '/': \"$text\"");
            }
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
