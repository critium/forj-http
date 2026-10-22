package forj.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A message body: bytes in memory, a blocking {@link InputStream} (how a backend hands over a
 * request body), or a {@link Writer} that writes straight to the connection (a streamed
 * response). Blocking is fine: each request runs on its own virtual thread.
 *
 * <p>A streamed body ({@link Input}) can be read once. Reading methods throw
 * {@link UncheckedIOException} when the underlying stream fails.
 */
public sealed interface Body permits Body.Bytes, Body.Input, Body.Output {
    Body EMPTY = new Bytes(new byte[0]);

    static Body of(byte[] bytes) {
        return bytes.length == 0 ? EMPTY : new Bytes(bytes);
    }

    /** The string's UTF-8 bytes. Sets no content type: that's the entity encoder's job. */
    static Body of(String text) {
        return of(text.getBytes(StandardCharsets.UTF_8));
    }

    /** A stream read once; {@code length} is -1 when unknown. */
    static Body of(InputStream in, long length) {
        return new Input(in, length);
    }

    /** A body written to the connection when the response is sent; {@code length} is -1 when unknown. */
    static Body writer(Writer writer, long length) {
        return new Output(writer, length);
    }

    /** The length in bytes, or -1 when not known up front. */
    long length();

    /** All of the body, in memory. */
    byte[] bytes();

    /** All of the body, decoded as UTF-8. */
    default String text() {
        return new String(bytes(), StandardCharsets.UTF_8);
    }

    /** The body as a stream. */
    InputStream stream();

    /** Writes the whole body to {@code out}; doesn't close it. */
    void writeTo(OutputStream out) throws IOException;

    /** Writes a streamed response body. */
    @FunctionalInterface
    interface Writer {
        void writeTo(OutputStream out) throws IOException;
    }

    record Bytes(byte[] bytes) implements Body {
        public Bytes {
            Objects.requireNonNull(bytes, "bytes");
        }

        @Override
        public long length() {
            return bytes.length;
        }

        @Override
        public InputStream stream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            out.write(bytes);
        }

        @Override
        public String toString() {
            return s"Body.Bytes(${bytes.length} bytes)";
        }
    }

    record Input(InputStream in, long length) implements Body {
        public Input {
            Objects.requireNonNull(in, "in");
        }

        @Override
        public byte[] bytes() {
            try {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public InputStream stream() {
            return in;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            in.transferTo(out);
        }
    }

    record Output(Writer writer, long length) implements Body {
        public Output {
            Objects.requireNonNull(writer, "writer");
        }

        @Override
        public byte[] bytes() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                writer.writeTo(out);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return out.toByteArray();
        }

        @Override
        public InputStream stream() {
            return new ByteArrayInputStream(bytes());
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            writer.writeTo(out);
        }
    }
}
