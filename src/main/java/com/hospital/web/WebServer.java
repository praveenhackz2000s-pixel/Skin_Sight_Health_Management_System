package com.hospital.web;

import com.hospital.web.handlers.AppointmentHandler;
import com.hospital.web.handlers.DashboardHandler;
import com.hospital.web.handlers.DoctorHandler;
import com.hospital.web.handlers.HealthHandler;
import com.hospital.web.handlers.PatientHandler;
import com.hospital.web.handlers.StaticHandler;
import com.hospital.web.handlers.VisitHandler;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wraps the JDK's built-in HttpServer and registers all routes.
 * It binds to 0.0.0.0 so it also works inside a container.
 */
public class WebServer {

    private final HttpServer server;
    private final ExecutorService executor;
    private final AuthFilter authFilter = new AuthFilter();
    private final int port;

    public WebServer(int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        this.executor = Executors.newFixedThreadPool(8);
        this.server.setExecutor(executor);

        // Longest matching context wins, so "/" only receives paths no other context claims.
        register("/", new DashboardHandler());
        register("/patients", new PatientHandler());
        register("/visits", new VisitHandler());
        register("/history", new VisitHandler());
        register("/doctors", new DoctorHandler());
        register("/workload", new DoctorHandler());
        register("/appointments", new AppointmentHandler());
        register("/static", new StaticHandler());
        register("/health", new HealthHandler());
    }

    private void register(String path, HttpHandler handler) {
        HttpContext context = server.createContext(path, handler);
        context.getFilters().add(authFilter);
    }

    public boolean isAuthEnabled() {
        return authFilter.isAuthEnabled();
    }

    public int getPort() {
        return port;
    }

    public void start() {
        server.start();
    }

    /** Stops accepting requests, waits up to 2 seconds for running ones, then frees the threads. */
    public void stop() {
        server.stop(2);
        executor.shutdown();
    }
}
