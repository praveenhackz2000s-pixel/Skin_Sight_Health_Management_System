package com.hospital.web.handlers;

import com.hospital.web.FormUtils;
import com.hospital.web.HtmlPages;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/**
 * Common behaviour of every page handler: one place for sending HTML, redirects, 404/405 pages and
 * for turning unexpected errors into friendly pages (no stack traces, no SQL, no credentials in the browser).
 * Subclasses only implement route().
 */
public abstract class BaseHandler implements HttpHandler {

    /** Look at the path and method of the request and produce a response. */
    protected abstract void route(HttpExchange exchange) throws Exception;

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (FormUtils.RequestTooLargeException e) {
            safeSend(exchange, 413, HtmlPages.errorPage("Request too large", "The submitted form was too large."));
        } catch (SQLException e) {
            // Details go to the server log only, never to the browser.
            System.err.println("[db] " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                    + " failed: " + e.getMessage());
            safeSend(exchange, 500, HtmlPages.errorPage("Database problem",
                    "The database could not complete your request. Please try again in a moment. "
                            + "If this keeps happening, ask the system administrator to check the database connection."));
        } catch (Exception e) {
            System.err.println("[error] " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                    + " failed: " + e);
            safeSend(exchange, 500, HtmlPages.errorPage("Something went wrong",
                    "An unexpected error occurred. Please go back and try again."));
        } finally {
            exchange.close();
        }
    }

    // ----------------------------------------------------------------- helpers for subclasses

    /** Request path without a trailing slash ("/patients/" becomes "/patients"). */
    protected static String path(HttpExchange exchange) {
        String p = exchange.getRequestURI().getPath();
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    protected static boolean isGet(HttpExchange exchange) {
        return "GET".equalsIgnoreCase(exchange.getRequestMethod());
    }

    protected static boolean isPost(HttpExchange exchange) {
        return "POST".equalsIgnoreCase(exchange.getRequestMethod());
    }

    protected void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Post/Redirect/Get: after a successful form submit the browser is sent to a normal page. */
    protected void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
    }

    protected void notFound(HttpExchange exchange) throws IOException {
        sendHtml(exchange, 404, HtmlPages.errorPage("Page not found", "The page you asked for does not exist."));
    }

    protected void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        sendHtml(exchange, 405, HtmlPages.errorPage("Method not allowed", "This address does not accept that kind of request."));
    }

    protected void badRequest(HttpExchange exchange, String message) throws IOException {
        sendHtml(exchange, 400, HtmlPages.errorPage("Invalid request", message));
    }

    /** Used while reporting an error: never let a second failure escape. */
    private void safeSend(HttpExchange exchange, int status, String html) {
        try {
            sendHtml(exchange, status, html);
        } catch (IOException | RuntimeException ignored) {
            // response may already be partly sent; nothing else to do
        }
    }
}
