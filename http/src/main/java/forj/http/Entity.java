package forj.http;

import java.util.Objects;
import java.util.Optional;

/**
 * An encoded body and its media type: what an {@link EntityEncoder} produces, and what
 * {@code Ok(...)} and friends put on a response. A {@code null} content type means none is set
 * (an empty body, say).
 */
public record Entity(Body body, MediaType contentType) {
    public static final Entity EMPTY = new Entity(Body.EMPTY, null);

    public Entity {
        Objects.requireNonNull(body, "body");
    }

    public Optional<MediaType> mediaType() {
        return Optional.ofNullable(contentType);
    }
}
