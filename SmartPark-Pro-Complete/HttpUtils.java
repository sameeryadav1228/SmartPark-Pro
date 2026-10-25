import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/** Small HTTP, form-data and JSON helper methods. */
public final class HttpUtils {
    private HttpUtils() {}

    public static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> result = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length > 0) result.put(decode(parts[0]), parts.length > 1 ? decode(parts[1]) : "");
        }
        return result;
    }

    public static Map<String, String> readQuery(HttpExchange exchange) {
        Map<String, String> result = new HashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return result;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(decode(parts[0]), parts.length > 1 ? decode(parts[1]) : "");
        }
        return result;
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException exception) { return value; }
    }

    public static String clean(String value) { return value == null ? "" : value.trim(); }

    public static void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", toJson(value));
    }

    public static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        sendBytes(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    public static void sendBytes(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
    }

    public static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) {
            return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r") + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringJoiner joiner = new StringJoiner(",", "{", "}");
            for (Map.Entry<?, ?> entry : map.entrySet())
                joiner.add(toJson(String.valueOf(entry.getKey())) + ":" + toJson(entry.getValue()));
            return joiner.toString();
        }
        if (value instanceof Iterable<?> items) {
            StringJoiner joiner = new StringJoiner(",", "[", "]");
            for (Object item : items) joiner.add(toJson(item));
            return joiner.toString();
        }
        return toJson(value.toString());
    }
}
