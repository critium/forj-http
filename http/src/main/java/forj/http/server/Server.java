package forj.http.server;

/**
 * A running server. Closing it drains: it stops accepting connections, lets requests in flight
 * finish for up to {@link ServerConfig#shutdownGrace()}, then stops.
 *
 * <pre>{@code
 * try (Server server = backend.start(ServerConfig.fromEnvironment(), app)) {
 *     server.await();
 * }
 * }</pre>
 */
public interface Server extends AutoCloseable {

    /** The port it listens on: the configured one, or the one picked when configured with 0. */
    int port();

    /** Blocks until the server has stopped, from {@link #close()} or a shutdown hook. */
    void await() throws InterruptedException;

    /** Drains and stops. Calling it again does nothing. */
    @Override
    void close();
}
