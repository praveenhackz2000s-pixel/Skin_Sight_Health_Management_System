package com.hospital.web.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves the one stylesheet. Only the file name "style.css" is accepted, so nobody can read other files.
 * The file is found on the classpath (src/main/resources is on the classpath in the documented run command)
 * or, as a fallback, in src/main/resources/static next to the project.
 */
public class StaticHandler implements HttpHandler {

    private static final String ALLOWED_PATH = "/static/style.css";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] css = path.equals(ALLOWED_PATH) ? loadCss() : null;
            if (css == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=300");
            exchange.sendResponseHeaders(200, css.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(css);
            }
        } finally {
            exchange.close();
        }
    }

    private static byte[] loadCss() throws IOException {
        try (InputStream in = StaticHandler.class.getResourceAsStream("/static/style.css")) {
            if (in != null) {
                return in.readAllBytes();
            }
        }
        Path file = Paths.get("src", "main", "resources", "static", "style.css");
        if (Files.isReadable(file)) {
            return Files.readAllBytes(file);
        }
        return null;
    }
}
