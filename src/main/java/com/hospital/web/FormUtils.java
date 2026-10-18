package com.hospital.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Helpers to read URL query strings and submitted HTML forms (application/x-www-form-urlencoded). */
public final class FormUtils {

    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int MAX_PAIRS = 300;

    private FormUtils() {
    }

    /** Thrown when a browser sends an unreasonably large form. */
    public static class RequestTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;

        public RequestTooLargeException() {
            super("Request body too large");
        }
    }

    public static Map<String, List<String>> parseQuery(HttpExchange exchange) {
        return parse(exchange.getRequestURI().getRawQuery());
    }

    public static Map<String, List<String>> parseBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_BODY_BYTES) {
                    throw new RequestTooLargeException();
                }
            }
            return parse(out.toString(StandardCharsets.UTF_8));
        }
    }

    private static Map<String, List<String>> parse(String raw) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        int pairs = 0;
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            if (++pairs > MAX_PAIRS) {
                break;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq >= 0 ? pair.substring(0, eq) : pair);
            String value = eq >= 0 ? decode(pair.substring(eq + 1)) : "";
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return map;
    }

    private static String decode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return text; // malformed %-escape: keep the raw text, validation will reject it later
        }
    }

    /** First value of a parameter, or "" when missing. */
    public static String first(Map<String, List<String>> params, String key) {
        List<String> values = params.get(key);
        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    public static List<String> all(Map<String, List<String>> params, String key) {
        List<String> values = params.get(key);
        return values == null ? new ArrayList<>() : values;
    }

    /** Parses a positive whole number; returns null when missing or invalid. */
    public static Integer positiveInt(String text) {
        if (text == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(text.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String urlEncode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
