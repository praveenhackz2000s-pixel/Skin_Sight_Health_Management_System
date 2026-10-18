package com.hospital;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Reads application settings.
 *
 * Order of priority for every setting:
 *   1. Environment variable (used when deployed, e.g. DB_URL, DB_USER, DB_PASSWORD, PORT)
 *   2. Private file "db.properties" in the project folder (used on a developer's own PC, never committed to Git)
 *   3. Built-in default
 */
public final class Config {

    private static final Properties FILE_PROPS = new Properties();
    private static boolean loaded = false;

    private Config() {
    }

    /** Loads db.properties once (path can be changed with the APP_CONFIG environment variable). */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        String path = System.getenv("APP_CONFIG");
        if (path == null || path.isBlank()) {
            path = "db.properties";
        }
        Path file = Paths.get(path);
        if (Files.isReadable(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                FILE_PROPS.load(in);
                System.out.println("[config] Loaded local settings from " + file.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("[config] Could not read " + file + ": " + e.getMessage());
            }
        }
    }

    public static String get(String envName, String propKey, String defaultValue) {
        load();
        String value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        value = FILE_PROPS.getProperty(propKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return defaultValue;
    }

    public static boolean getBoolean(String envName, String propKey, boolean defaultValue) {
        String value = get(envName, propKey, null);
        if (value == null) {
            return defaultValue;
        }
        return value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("yes");
    }

    public static int getInt(String envName, String propKey, int defaultValue) {
        String value = get(envName, propKey, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
