package com.hospital.database;

import com.hospital.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes the project "launchable": when the application starts it
 *   1. creates the database if it does not exist (local MySQL),
 *   2. runs schema.sql if the tables are missing,
 *   3. runs seed-demo.sql (fictional demo data) if there are no doctors yet.
 *
 * Turn it off with AUTO_INIT_DB=false. Demo data can be skipped with LOAD_DEMO_DATA=false.
 * The SQL files are the same ones you can also run by hand with the mysql command line.
 */
public final class DatabaseInitializer {

    private DatabaseInitializer() {
    }

    /** Never throws: problems are printed so the web server can still start and show a friendly error. */
    public static boolean initializeIfNeeded() {
        if (!Config.getBoolean("AUTO_INIT_DB", "auto.init.db", true)) {
            System.out.println("[init] AUTO_INIT_DB is false - skipping automatic database setup.");
            return true;
        }
        File schema = findScript("schema.sql");
        File seed = findScript("seed-demo.sql");
        if (schema == null) {
            System.out.println("[init] schema.sql not found next to the application - skipping automatic setup.");
            return true;
        }
        try {
            ensureDatabaseExists();
            try (Connection con = DBConnection.getConnection()) {
                if (!tableExists(con, "patients")) {
                    System.out.println("[init] Creating tables from " + schema.getName() + " ...");
                    runScript(con, schema);
                }
                boolean loadDemo = Config.getBoolean("LOAD_DEMO_DATA", "load.demo.data", true);
                if (loadDemo && seed != null && isEmpty(con, "doctors")) {
                    System.out.println("[init] Loading fictional demo data from " + seed.getName() + " ...");
                    runScript(con, seed);
                }
            }
            System.out.println("[init] Database ready: " + DBConnection.safeUrl());
            return true;
        } catch (SQLException | IOException e) {
            System.err.println("[init] ERROR: " + e.getMessage());
            System.err.println("[init] The website will start, but pages that need the database will show an error.");
            System.err.println("[init] Check that MySQL is running and that DB_URL / DB_USER / DB_PASSWORD (or db.properties) are correct.");
            return false;
        }
    }

    private static File findScript(String name) {
        String dir = Config.get("SQL_DIR", "sql.dir", ".");
        String[] folders = {dir, ".", "/app"};
        for (String folder : folders) {
            File f = new File(folder, name);
            if (f.isFile() && f.canRead()) {
                return f;
            }
        }
        return null;
    }

    /** Creates the database when the server is reachable but the database name is unknown. */
    private static void ensureDatabaseExists() throws SQLException {
        try {
            DBConnection.getConnection().close(); // the database already exists and is reachable
            return;
        } catch (SQLException e) {
            if (!DBConnection.isUnknownDatabase(e)) {
                throw e;
            }
        }
        String name = DBConnection.databaseName();
        if (!name.matches("[A-Za-z0-9_]+")) {
            throw new SQLException("Unsafe database name in DB_URL: " + name);
        }
        System.out.println("[init] Database '" + name + "' does not exist - creating it ...");
        try (Connection server = DBConnection.getServerConnection(); Statement st = server.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private static boolean tableExists(Connection con, String table) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        try (ResultSet rs = meta.getTables(con.getCatalog(), null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static boolean isEmpty(Connection con, String table) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    private static void runScript(Connection con, File file) throws IOException, SQLException {
        String sql = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        for (String statement : splitStatements(sql)) {
            String upper = statement.trim().toUpperCase();
            // The scripts start with CREATE DATABASE / USE for manual use with the mysql client.
            // Here we are already connected to the right database, so skip those two.
            if (upper.startsWith("CREATE DATABASE") || upper.startsWith("USE ")) {
                continue;
            }
            try (Statement st = con.createStatement()) {
                st.execute(statement);
            }
        }
    }

    /** Splits a SQL script on semicolons, ignoring semicolons inside quotes and "-- comments". */
    static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quote = 0;
        int n = sql.length();
        for (int i = 0; i < n; i++) {
            char c = sql.charAt(i);
            if (inQuote) {
                current.append(c);
                if (c == '\\' && i + 1 < n) {
                    current.append(sql.charAt(++i));
                } else if (c == quote) {
                    if (i + 1 < n && sql.charAt(i + 1) == quote) {
                        current.append(sql.charAt(++i)); // doubled quote inside a string
                    } else {
                        inQuote = false;
                    }
                }
                continue;
            }
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
                current.append('\n');
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                inQuote = true;
                quote = c;
                current.append(c);
                continue;
            }
            if (c == ';') {
                addIfNotBlank(out, current);
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        addIfNotBlank(out, current);
        return out;
    }

    private static void addIfNotBlank(List<String> out, StringBuilder sb) {
        String s = sb.toString().trim();
        if (!s.isEmpty()) {
            out.add(s);
        }
    }
}
