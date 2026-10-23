package forj.http;

import java.util.Objects;

/**
 * An RFC 9457 problem response ({@code application/problem+json}): how forj-http reports
 * errors. Written by hand, since {@code http} has no JSON dependency.
 *
 * <pre>{@code {"type":"about:blank","title":"Bad Request","status":400,"detail":"..."}}</pre>
 *
 * @param detail what went wrong for this request, or {@code null} to say nothing more than the
 *     status (as for a 500, whose cause must not leak)
 */
public record Problem(Status status, String detail) {

    public Problem {
        Objects.requireNonNull(status, "status");
    }

    public Response toResponse() {
        return Response.of(status)
                .withHeader("Content-Type", MediaType.PROBLEM_JSON.toString())
                .withBody(toJson());
    }

    public String toJson() {
        String title = status.reason().isEmpty() ? Integer.toString(status.code()) : status.reason();
        StringBuilder sb = new StringBuilder(96).append("{\"type\":\"about:blank\",\"title\":");
        quote(sb, title).append(",\"status\":").append(status.code());
        if (detail != null) quote(sb.append(",\"detail\":"), detail);
        return sb.append('}').toString();
    }

    private static StringBuilder quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"');
    }
}
