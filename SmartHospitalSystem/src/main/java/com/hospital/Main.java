package com.hospital;

import com.hospital.database.DBConnection;
import com.hospital.ui.AppointmentModule;
import com.hospital.ui.PatientModule;
import com.hospital.ui.VisitModule;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Scanner;

/** Application entry point. Connect the completed team modules here. */
public class Main {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                System.out.println("\n=== SMART EYE & SKIN HOSPITAL ===");
                System.out.println("1. Patient registration/search");
                System.out.println("2. Visits and medical history");
                System.out.println("3. Doctors and appointments");
                System.out.println("4. Test database connection");
                System.out.println("0. Exit");
                System.out.print("Choose an option: ");
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1" -> PatientModule.run(scanner);
                    case "2" -> VisitModule.run(scanner);
                    case "3" -> AppointmentModule.run(scanner);
                    case "4" -> testDatabaseConnection();
                    case "0" -> running = false;
                    default -> System.out.println("Invalid option. Try again.");
                }
            }
            System.out.println("Application closed.");
        }
    }

    private static void testDatabaseConnection() {
        try (Connection connection = DBConnection.getConnection()) {
            System.out.println("Database connected successfully: "
                    + connection.getCatalog());
        } catch (SQLException e) {
            System.out.println("Database connection failed: " + e.getMessage());
        }
    }
}
