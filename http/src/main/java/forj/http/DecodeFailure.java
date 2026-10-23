package forj.http;

/**
 * A body that couldn't be decoded: 400 by default, 415 for a media type or charset the decoder
 * doesn't support. The message is shown to the client, so make it say what's wrong and where
 * (a JSON decoder includes the path: {@code missing field 'name' at $.users[3]}).
 */
public final class DecodeFailure extends HttpFailure {

    public DecodeFailure(String detail) {
        this(Status.BAD_REQUEST, detail, null);
    }

    public DecodeFailure(String detail, Throwable cause) {
        this(Status.BAD_REQUEST, detail, cause);
    }

    public DecodeFailure(Status status, String detail) {
        this(status, detail, null);
    }

    public DecodeFailure(Status status, String detail, Throwable cause) {
        super(status, detail, cause);
    }
}
