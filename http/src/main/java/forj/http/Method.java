package forj.http;

import java.util.Objects;

/** An HTTP request method. The standard ones are constants; {@link #of} accepts any token. */
public record Method(String name) {
    public static final Method GET = new Method("GET");
    public static final Method HEAD = new Method("HEAD");
    public static final Method POST = new Method("POST");
    public static final Method PUT = new Method("PUT");
    public static final Method PATCH = new Method("PATCH");
    public static final Method DELETE = new Method("DELETE");
    public static final Method OPTIONS = new Method("OPTIONS");

    public Method {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) throw new IllegalArgumentException("empty method name");
    }

    /** The constant for a standard method, so backends don't allocate one per request. */
    public static Method of(String name) {
        return switch (name) {
            case "GET" -> GET;
            case "HEAD" -> HEAD;
            case "POST" -> POST;
            case "PUT" -> PUT;
            case "PATCH" -> PATCH;
            case "DELETE" -> DELETE;
            case "OPTIONS" -> OPTIONS;
            default -> new Method(name);
        };
    }

    @Override
    public String toString() {
        return name;
    }
}
