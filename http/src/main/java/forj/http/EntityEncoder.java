package forj.http;

import forj.data.Unit;
import java.util.function.Function;

/**
 * How to write an {@code A} as a body: a type class, so {@code Ok(value)} finds the encoder for
 * the value's type at compile time.
 *
 * <p>The instances here are the defaults, found without imports. A closer {@code given} (in the
 * handler, the class, or imported) wins. There is deliberately no JSON instance: JSON comes from
 * {@code forj-http-json}'s explicit {@code json(value)}, so a {@code String} is never turned
 * into JSON by an import.
 */
@FunctionalInterface
public interface EntityEncoder<A> {

    Entity encode(A value);

    /** An encoder for {@code B}, through {@code f}. */
    default <B> EntityEncoder<B> contramap(Function<? super B, ? extends A> f) {
        return b -> encode(f.apply(b));
    }

    /** An encoder writing {@code toBytes(value)} with media type {@code type}. */
    static <A> EntityEncoder<A> of(MediaType type, Function<? super A, byte[]> toBytes) {
        return a -> new Entity(Body.of(toBytes.apply(a)), type);
    }

    /** UTF-8, {@code text/plain; charset=utf-8}. */
    given EntityEncoder<String> string = s -> new Entity(Body.of(s), MediaType.TEXT_PLAIN);

    /** As is, {@code application/octet-stream}. */
    given EntityEncoder<byte[]> bytes = b -> new Entity(Body.of(b), MediaType.OCTET_STREAM);

    /** No body and no content type. */
    given EntityEncoder<Unit> unit = u -> Entity.EMPTY;

    /** An already encoded entity, unchanged: how {@code Ok(json(user))} works. */
    given EntityEncoder<Entity> entity = e -> e;
}
