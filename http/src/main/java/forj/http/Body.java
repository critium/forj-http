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
 * <p>Two kinds of writer, by what a flush means:
 * <ul>
 *   <li>{@link #writer(Writer, long) Body.writer}: the body is sent when the writer returns.
 *       Its flushes are ignored, so an encoder that flushes when it finishes (forj-codec's
 *       {@code Json.encode(value, out)} does) doesn't cost anything: a small body still goes
 *       out in one write with a {@code Content-Length}, and a large one streams as the
 *       server's buffer fills, so memory stays bounded either way.</li>
 *   <li>{@link #streaming(Writer) Body.streaming}: each {@code flush()} sends what's written
 *       so far, before the writer returns. For server-sent events, streamed tokens, progress
 *       reports. It changes when the client sees the bytes, not how much memory is used.</li>
 * </ul>
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

    /**
     * A body written to the connection when the response is sent; {@code length} is -1 when
     * unknown. The writer's flushes are ignored: everything is sent once it returns (or as the
     * server's buffer fills). For early sends, use {@link #streaming(Writer)}.
     */
    static Body writer(Writer writer, long length) {
        return new Output(writer, length, false);
    }

    /**
     * A body whose writer sends what it has written each time it calls {@code flush()}: server-sent
     * events, streamed tokens, progress. Its length is unknown, so it's sent chunked.
     */
    static Body streaming(Writer writer) {
        return new Output(writer, -1, true);
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

    /**
     * Writes the whole body to {@code out}; doesn't close it. A backend calls it once, then
     * closes {@code out} itself, and doesn't flush before that (see {@link #writer}).
     */
    void writeTo(OutputStream out) throws IOException;

    /** Writes a streamed response body. It mustn't close {@code out}; closing it does nothing. */
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

    /** A written body; {@code flushes} is whether the writer's flushes reach the connection. */
    record Output(Writer writer, long length, boolean flushes) implements Body {
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
            writer.writeTo(new Guarded(out, flushes));
        }
    }

    /**
     * What a writer writes to: everything passes through, but {@code close()} does nothing (the
     * backend closes the connection's stream), and so does {@code flush()} unless the body
     * streams. A flush before the end makes a server send the headers at once and go chunked,
     * where it would otherwise send a small body in one write with a {@code Content-Length}.
     */
    final class Guarded extends OutputStream {
        private final OutputStream out;
        private final boolean flushes;

        private Guarded(OutputStream out, boolean flushes) {
            this.out = out;
            this.flushes = flushes;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            if (flushes) out.flush();
        }

        @Override
        public void close() {
        }
    }
}
