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
 * Serves approved static assets. Only explicitly allowed file names can be requested.
 * The file is found on the classpath (src/main/resources is on the classpath in the documented run command)
 * or, as a fallback, in src/main/resources/static next to the project.
 */
public class StaticHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String fileName;
            String contentType;
            if (path.equals("/static/style.css")) {
                fileName = "style.css";
                contentType = "text/css; charset=UTF-8";
            } else if (path.equals("/static/logo-mark.png")) {
                fileName = "logo-mark.png";
                contentType = "image/png";
            } else {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] content = loadAsset(fileName);
            if (content == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=300");
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(content);
            }
        } finally {
            exchange.close();
        }
    }

    private static byte[] loadAsset(String fileName) throws IOException {
        try (InputStream in = StaticHandler.class.getResourceAsStream("/static/" + fileName)) {
            if (in != null) {
                return in.readAllBytes();
            }
        }
        Path file = Paths.get("src", "main", "resources", "static", fileName);
        if (Files.isReadable(file)) {
            return Files.readAllBytes(file);
        }
        return null;
    }
}
