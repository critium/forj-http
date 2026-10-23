package forj.http;

import forj.data.Unit;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.function.Function;

/**
 * How to read an {@code A} from a body: a type class, so {@code User u = req.as();} finds the
 * decoder for {@code User} at compile time.
 *
 * <p>Decoding blocks while it reads the body, which is fine on a request's virtual thread. A
 * body that can't be decoded throws {@link DecodeFailure}, which the
 * {@link forj.http.middleware.Middlewares#errors() errors} middleware answers with a 400 (or
 * 415) problem response.
 */
@FunctionalInterface
public interface EntityDecoder<A> {

    /** @throws DecodeFailure if the body isn't a valid {@code A} */
    A decode(Body body, Headers headers);

    /** A decoder for {@code B}: this one, then {@code f}. An exception from {@code f} is not wrapped. */
    default <B> EntityDecoder<B> map(Function<? super A, ? extends B> f) {
        return (body, headers) -> f.apply(decode(body, headers));
    }

    /** The text in the {@code Content-Type} charset, UTF-8 when none is given. */
    given EntityDecoder<String> string = (body, headers) -> new String(body.bytes(), charset(headers));

    given EntityDecoder<byte[]> bytes = (body, headers) -> body.bytes();

    /** Ignores the body. */
    given EntityDecoder<Unit> unit = (body, headers) -> Unit.UNIT;

    /** The {@code Content-Type}'s media type, if there is one. @throws DecodeFailure if it doesn't parse */
    static java.util.Optional<MediaType> mediaType(Headers headers) {
        return headers.get("Content-Type").map(value -> {
            try {
                return MediaType.parse(value);
            } catch (IllegalArgumentException e) {
                throw new DecodeFailure(e.getMessage());
            }
        });
    }

    /** The {@code Content-Type}'s charset, or UTF-8. @throws DecodeFailure (415) for an unknown charset */
    static Charset charset(Headers headers) {
        try {
            return mediaType(headers).flatMap(MediaType::charset).orElse(StandardCharsets.UTF_8);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new DecodeFailure(Status.UNSUPPORTED_MEDIA_TYPE, s"unsupported charset: ${e.getMessage()}");
        }
    }
}
