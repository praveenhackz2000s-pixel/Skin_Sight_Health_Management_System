package com.hospital;

import com.hospital.database.DBConnection;
import com.hospital.database.DatabaseInitializer;
import com.hospital.web.WebServer;

import java.io.IOException;

/**
 * Starts the Smart Eye & Skin Specialist Hospital web application.
 *
 *   1. read settings (environment variables or db.properties)
 *   2. make sure the database, tables and demo data exist
 *   3. start the web server on PORT (default 8080)
 */
public class Main {

    public static void main(String[] args) {
        Config.load();
        System.out.println("=== Smart Eye & Skin Specialist Hospital - clinical decision-support PROTOTYPE ===");

        DatabaseInitializer.initializeIfNeeded();

        int port = Config.getInt("PORT", "port", 8080);
        WebServer server;
        try {
            server = new WebServer(port);
        } catch (IOException e) {
            System.err.println("Could not start the web server on port " + port + ": " + e.getMessage());
            System.err.println("Is another program already using this port? Set a different one with the PORT setting.");
            System.exit(1);
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down ...");
            server.stop();
        }));

        server.start();
        System.out.println("Database : " + DBConnection.safeUrl());
        System.out.println("Login    : " + (server.isAuthEnabled()
                ? "HTTP Basic login is ON"
                : "OFF (fine on your own PC; set APP_PASSWORD before putting the site on the internet)"));
        System.out.println("Open     : http://localhost:" + server.getPort() + "/");
        System.out.println("Press Ctrl+C to stop.");
    }
}
