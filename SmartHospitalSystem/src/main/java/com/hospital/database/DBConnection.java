package com.hospital.database;

import com.hospital.Config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ONE place where database connections are created.
 *
 * Settings come from DB_URL, DB_USER, DB_PASSWORD (environment variables) or from
 * the private file db.properties. No connection is shared: every caller opens its own
 * connection with try-with-resources so it is always closed.
 */
public final class DBConnection {

    public static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/smart_hospital?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
    public static final String DEFAULT_DATABASE = "smart_hospital";

    /** Splits a JDBC URL into: server part, database name, query string. */
    private static final Pattern URL_PARTS = Pattern.compile("^(jdbc:[A-Za-z0-9]+://[^/?]+)/?([^?]*)(\\?.*)?$");

    static {
        DriverManager.setLoginTimeout(10);
        try {
            // Not required with modern JDBC drivers, but makes some setups more reliable.
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // The clear error message is produced later if no driver can be found.
        }
    }

    private DBConnection() {
    }

    public static String getUrl() {
        return Config.get("DB_URL", "db.url", DEFAULT_URL);
    }

    private static String getUser() {
        return Config.get("DB_USER", "db.user", "root");
    }

    private static String getPassword() {
        return Config.get("DB_PASSWORD", "db.password", "");
    }

    /** Opens a connection to the hospital database. The caller must close it. */
    public static Connection getConnection() throws SQLException {
        return open(getUrl());
    }

    /** Opens a connection to the MySQL server WITHOUT selecting a database (used to create it). */
    public static Connection getServerConnection() throws SQLException {
        return open(serverUrl(getUrl()));
    }

    private static Connection open(String url) throws SQLException {
        try {
            return DriverManager.getConnection(url, getUser(), getPassword());
        } catch (SQLException e) {
            throw friendly(e);
        }
    }

    /** Builds an error whose text is useful to a student but never contains the password. */
    private static SQLException friendly(SQLException e) {
        String message = e.getMessage() == null ? "unknown error" : e.getMessage();
        int newline = message.indexOf('\n');
        if (newline > 0) {
            message = message.substring(0, newline);
        }
        String hint = "";
        String lower = message.toLowerCase();
        if (lower.contains("no suitable driver")) {
            hint = " (MySQL Connector/J JAR not found - put it in the lib/ folder, see lib/README.md)";
        } else if (lower.contains("access denied")) {
            hint = " (check DB_USER / DB_PASSWORD or the values in db.properties)";
        } else if (lower.contains("communications link failure") || lower.contains("connection refused")) {
            hint = " (is the MySQL server running, and are host/port in DB_URL correct?)";
        }
        return new SQLException("Cannot connect to " + safeUrl() + ": " + message + hint, e.getSQLState(), e.getErrorCode());
    }

    /** The JDBC URL without any query string, safe for logs and pages. */
    public static String safeUrl() {
        String url = getUrl();
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    /** Name of the database in the configured URL. */
    public static String databaseName() {
        Matcher m = URL_PARTS.matcher(getUrl());
        if (m.matches() && m.group(2) != null && !m.group(2).isEmpty()) {
            return m.group(2);
        }
        return DEFAULT_DATABASE;
    }

    private static String serverUrl(String url) {
        Matcher m = URL_PARTS.matcher(url);
        if (!m.matches()) {
            return url;
        }
        String query = m.group(3) == null ? "" : m.group(3);
        return m.group(1) + "/" + query;
    }

    /** True when the error means "the database does not exist yet" (MySQL error 1049). */
    public static boolean isUnknownDatabase(SQLException e) {
        return e.getErrorCode() == 1049
                || (e.getMessage() != null && e.getMessage().toLowerCase().contains("unknown database"));
    }

    /** Used by /health: true when a simple query works. */
    public static boolean ping() {
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            st.execute("SELECT 1");
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /** Rolls back and ignores any secondary error. */
    public static void rollbackQuietly(Connection con) {
        try {
            con.rollback();
        } catch (SQLException ignored) {
            // nothing more we can do
        }
    }
}
