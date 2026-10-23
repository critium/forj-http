package forj.http;

import java.util.Objects;

/**
 * An expected failure with an HTTP answer: throw it (or raise it in {@code F}) from a handler,
 * and the {@link forj.http.middleware.Middlewares#errors errors} middleware responds with its
 * status and message as a problem response.
 *
 * <p>It's an outcome, not a bug, so it carries no stack trace, which makes it cheap to throw.
 */
public class HttpFailure extends RuntimeException {
    private final Status status;

    public HttpFailure(Status status, String detail) {
        this(status, detail, null);
    }

    public HttpFailure(Status status, String detail, Throwable cause) {
        super(detail, cause, false, false);
        this.status = Objects.requireNonNull(status, "status");
    }

    public Status status() {
        return status;
    }

    /** The problem response for this failure; its message is the detail. */
    public Response toResponse() {
        return new Problem(status, getMessage()).toResponse();
    }
}
