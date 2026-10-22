package forj.http;

/** An HTTP status code and its reason phrase. Common codes are constants; {@link #of} takes any. */
public record Status(int code, String reason) {
    private static final Status[] KNOWN = new Status[600];

    public static final Status CONTINUE = known(100, "Continue");
    public static final Status SWITCHING_PROTOCOLS = known(101, "Switching Protocols");

    public static final Status OK = known(200, "OK");
    public static final Status CREATED = known(201, "Created");
    public static final Status ACCEPTED = known(202, "Accepted");
    public static final Status NO_CONTENT = known(204, "No Content");
    public static final Status PARTIAL_CONTENT = known(206, "Partial Content");

    public static final Status MOVED_PERMANENTLY = known(301, "Moved Permanently");
    public static final Status FOUND = known(302, "Found");
    public static final Status SEE_OTHER = known(303, "See Other");
    public static final Status NOT_MODIFIED = known(304, "Not Modified");
    public static final Status TEMPORARY_REDIRECT = known(307, "Temporary Redirect");
    public static final Status PERMANENT_REDIRECT = known(308, "Permanent Redirect");

    public static final Status BAD_REQUEST = known(400, "Bad Request");
    public static final Status UNAUTHORIZED = known(401, "Unauthorized");
    public static final Status FORBIDDEN = known(403, "Forbidden");
    public static final Status NOT_FOUND = known(404, "Not Found");
    public static final Status METHOD_NOT_ALLOWED = known(405, "Method Not Allowed");
    public static final Status NOT_ACCEPTABLE = known(406, "Not Acceptable");
    public static final Status REQUEST_TIMEOUT = known(408, "Request Timeout");
    public static final Status CONFLICT = known(409, "Conflict");
    public static final Status GONE = known(410, "Gone");
    public static final Status LENGTH_REQUIRED = known(411, "Length Required");
    public static final Status PRECONDITION_FAILED = known(412, "Precondition Failed");
    public static final Status CONTENT_TOO_LARGE = known(413, "Content Too Large");
    public static final Status URI_TOO_LONG = known(414, "URI Too Long");
    public static final Status UNSUPPORTED_MEDIA_TYPE = known(415, "Unsupported Media Type");
    public static final Status UNPROCESSABLE_CONTENT = known(422, "Unprocessable Content");
    public static final Status TOO_MANY_REQUESTS = known(429, "Too Many Requests");

    public static final Status INTERNAL_SERVER_ERROR = known(500, "Internal Server Error");
    public static final Status NOT_IMPLEMENTED = known(501, "Not Implemented");
    public static final Status BAD_GATEWAY = known(502, "Bad Gateway");
    public static final Status SERVICE_UNAVAILABLE = known(503, "Service Unavailable");
    public static final Status GATEWAY_TIMEOUT = known(504, "Gateway Timeout");

    public Status {
        if (code < 100 || code > 599) throw new IllegalArgumentException(s"status out of range: $code");
        if (reason == null) reason = "";
    }

    /** The constant for a known code, or a new status with an empty reason. */
    public static Status of(int code) {
        if (code >= 100 && code < KNOWN.length && KNOWN[code] != null) return KNOWN[code];
        return new Status(code, "");
    }

    private static Status known(int code, String reason) {
        return KNOWN[code] = new Status(code, reason);
    }

    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }

    public boolean isClientError() {
        return code >= 400 && code < 500;
    }

    public boolean isServerError() {
        return code >= 500;
    }

    @Override
    public String toString() {
        return reason.isEmpty() ? Integer.toString(code) : s"$code $reason";
    }
}
