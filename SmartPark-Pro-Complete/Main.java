import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.Executors;

/** Starts the web server and routes browser requests to the right component. */
public class Main {
    private static final ParkingService parkingService = new ParkingService();

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String configuredPort = System.getenv("PORT");
        if (configuredPort != null) {
            try { port = Integer.parseInt(configuredPort); }
            catch (NumberFormatException ignored) { System.out.println("Invalid PORT setting; using 8080."); }
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", Main::handleRequest);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("SmartPark Pro is running at http://localhost:" + port);
        System.out.println("Keep this terminal open. Press Ctrl+C to stop.");
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if (path.equals("/") && method.equals("GET")) {
                servePublicFile(exchange, "index.html", "text/html; charset=utf-8"); return;
            }
            if (path.equals("/favicon.svg") && method.equals("GET")) {
                servePublicFile(exchange, "favicon.svg", "image/svg+xml"); return;
            }
            if (path.equals("/style.css") && method.equals("GET")) {
                servePublicFile(exchange, "style.css", "text/css; charset=utf-8"); return;
            }
            if (path.equals("/script.js") && method.equals("GET")) {
                servePublicFile(exchange, "script.js", "application/javascript; charset=utf-8"); return;
            }
            if (path.equals("/config.json") && method.equals("GET")) {
                servePublicFile(exchange, "config.json", "application/json; charset=utf-8"); return;
            }
            if (path.startsWith("/api/")) {
                parkingService.handleApi(exchange, path, method); return;
            }
            HttpUtils.send(exchange, 404, "text/plain; charset=utf-8", "Not found");
        } catch (Exception exception) {
            HttpUtils.sendJson(exchange, 500, Map.of("success", false,
                    "message", "Server error: " + (exception.getMessage() == null ? "Unexpected error" : exception.getMessage())));
        }
    }

    private static void servePublicFile(HttpExchange exchange, String filename, String contentType) throws IOException {
        File file = new File("public", filename);
        if (!file.isFile()) {
            HttpUtils.send(exchange, 500, "text/plain; charset=utf-8",
                    "Missing file: public/" + filename + ". Open the project root folder in VS Code.");
            return;
        }
        byte[] contents = Files.readAllBytes(file.toPath());
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        HttpUtils.sendBytes(exchange, 200, contentType, contents);
    }
}
