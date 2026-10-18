package com.hospital.web.handlers;

import com.hospital.database.DBConnection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * GET /health : used by hosting platforms. Returns 200 when the web server AND the database respond,
 * 503 when the database cannot be reached. It exposes no secrets, no URLs and no patient data.
 */
public class HealthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            boolean databaseUp = DBConnection.ping();
            String json = "{\"status\":\"" + (databaseUp ? "UP" : "DEGRADED") + "\",\"database\":\""
                    + (databaseUp ? "UP" : "DOWN") + "\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(databaseUp ? 200 : 503, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } finally {
            exchange.close();
        }
    }
}
