package com.hospital.web;

import com.hospital.Config;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Runs before every page handler.
 *
 *  1. Adds security headers (no inline scripts/styles, no framing, no sniffing).
 *  2. Rejects form posts that come from another website (Origin must match Host).
 *  3. If APP_PASSWORD is set, requires HTTP Basic login (user APP_USER, default "staff").
 *     /health and the stylesheet stay open so hosting health checks and the login page style work.
 *
 * Locally you may leave APP_PASSWORD empty. For any public deployment you MUST set it.
 */
public class AuthFilter extends Filter {

    private final String expectedUser;
    private final String expectedPassword;

    public AuthFilter() {
        this.expectedUser = Config.get("APP_USER", "app.user", "staff");
        this.expectedPassword = Config.get("APP_PASSWORD", "app.password", "");
    }

    public boolean isAuthEnabled() {
        return !expectedPassword.isEmpty();
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "same-origin");
        headers.set("Content-Security-Policy",
                "default-src 'none'; style-src 'self'; img-src 'self' data:; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");

        String path = exchange.getRequestURI().getPath();

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && !sameOrigin(exchange)) {
            reject(exchange, 403, "Cross-site form submissions are not allowed.");
            return;
        }

        boolean open = path.equals("/health") || path.equals("/static/style.css");
        if (isAuthEnabled() && !open && !isAuthorized(exchange)) {
            headers.set("WWW-Authenticate", "Basic realm=\"Smart Hospital (demo)\", charset=\"UTF-8\"");
            reject(exchange, 401, "Login required.");
            return;
        }
        chain.doFilter(exchange);
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String user = decoded.substring(0, colon);
        String password = decoded.substring(colon + 1);
        // Both comparisons always run, and each one takes the same time whatever the input.
        boolean userOk = constantTimeEquals(user, expectedUser);
        boolean passwordOk = constantTimeEquals(password, expectedPassword);
        return userOk && passwordOk;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** A browser always sends Origin on cross-site POSTs. If present it must point at this same host. */
    private static boolean sameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isEmpty()) {
            return true; // not a browser cross-site request (curl, older browsers, same-site form)
        }
        String host = exchange.getRequestHeaders().getFirst("Host");
        try {
            String authority = URI.create(origin).getAuthority();
            return authority != null && host != null && authority.equalsIgnoreCase(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void reject(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Override
    public String description() {
        return "Security headers, same-origin check and optional Basic login";
    }
}
