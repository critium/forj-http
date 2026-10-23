package forj.http.json;

import forj.http.Body;
import forj.http.DecodeFailure;
import forj.http.Entity;
import forj.http.EntityDecoder;
import forj.http.EntityEncoder;
import forj.http.MediaType;
import forj.http.Request;
import forj.http.Status;
import forj.json.Codec;
import forj.json.Json;
import forj.json.spi.JsonBackend;
import forj.json.spi.JsonException;

/**
 * JSON for forj-http, on forj-codec. It's explicit at each use, never a blanket given, so
 * {@code Ok("hi")} stays {@code text/plain} with this imported:
 *
 * <pre>{@code
 * import static forj.http.json.HttpJson.*;
 *
 * POST("/users").respond(req -> {
 *     NewUser body = req.json();                 // the type comes from the typed local
 *     return Created(json(repo.add(body)));      // application/json
 * })
 * }</pre>
 *
 * <p>The codec ({@code record User(...) derives Codec}) and the {@link JsonBackend} (forj's own
 * parser unless a closer given picks another) are found at compile time.
 *
 * <p>A body that isn't valid JSON for the type is a {@link DecodeFailure}: 400, with the parser's
 * message (what's wrong and where) as the detail. A {@code Content-Type} that isn't JSON
 * ({@code application/json} or any {@code +json}) is 415; a request without one is read as JSON.
 */
public final class HttpJson {
    private HttpJson() {
    }

    /** {@code value} as a JSON entity, for {@code Ok(json(user))}. */
    public static <A> Entity json(A value)(using Codec<A> codec, JsonBackend backend) {
        return new Entity(Body.of(Json.encode(value)), MediaType.JSON);
    }

    /**
     * The request body as an {@code A}: {@code User u = req.json();}. A {@code Request} also
     * fits {@link #json(Object) json(A)}; as in javac, this more specific one wins.
     *
     * @throws DecodeFailure if it isn't one
     */
    extension <A> A json(Request request)(using Codec<A> codec, JsonBackend backend) {
        // explicit: nothing tells forj A for a call used as a receiver
        return decoder()(using codec, backend).decode(request.body(), request.headers());
    }

    /** For an app that wants {@code A} to always be JSON: {@code given EntityEncoder<User> users = HttpJson.encoder();}. */
    public static <A> EntityEncoder<A> encoder()(using Codec<A> codec, JsonBackend backend) {
        return value -> json(value);
    }

    /** The decoder behind {@code req.json()}, for {@code req.as()} with a local given. */
    public static <A> EntityDecoder<A> decoder()(using Codec<A> codec, JsonBackend backend) {
        return (body, headers) -> {
            EntityDecoder.mediaType(headers).ifPresent(type -> {
                if (!isJson(type)) throw new DecodeFailure(Status.UNSUPPORTED_MEDIA_TYPE, s"expected JSON, got $type");
            });
            try {
                return body instanceof Body.Bytes bytes ? Json.decode(bytes.bytes()) : Json.decode(body.stream());
            } catch (JsonException e) {
                throw new DecodeFailure(s"invalid JSON: ${e.getMessage()}", e);
            }
        };
    }

    private static boolean isJson(MediaType type) {
        return type.matches(MediaType.JSON) || (type.type().equals("application") && type.subtype().endsWith("+json"));
    }
}
