package com.hospital.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Shared JDBC connection factory used by every DAO. */
public final class DBConnection {
    private DBConnection() {
        // Utility class: prevent object creation.
    }

    public static Connection getConnection() throws SQLException {
        Properties properties = new Properties();
        Path configFile = Path.of("db.properties");

        if (!Files.exists(configFile)) {
            throw new SQLException(
                "db.properties was not found in the project root. "
                + "Copy db.properties.example to db.properties and add your local credentials."
            );
        }

        try (InputStream input = Files.newInputStream(configFile)) {
            properties.load(input);
        } catch (IOException e) {
            throw new SQLException("Could not read db.properties.", e);
        }

        String url = properties.getProperty("db.url");
        String username = properties.getProperty("db.username");
        String password = properties.getProperty("db.password");

        if (url == null || username == null || password == null
                || url.isBlank() || username.isBlank()) {
            throw new SQLException("Database configuration is incomplete.");
        }

        return DriverManager.getConnection(url, username, password);
    }
}
