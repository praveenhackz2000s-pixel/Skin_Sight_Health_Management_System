package com.hospital.dao;

import com.hospital.model.Patient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL for the patients table. Every method receives an open Connection from the service layer. */
public class PatientDAO {

    private static final String COLUMNS =
            "patient_id, name, age, phone, DATE_FORMAT(created_at, '%d %b %Y %H:%i') AS created_text";

    /** Inserts a patient and returns the generated patient ID. */
    public int insert(Connection con, Patient patient) throws SQLException {
        String sql = "INSERT INTO patients (name, age, phone) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, patient.getName());
            ps.setInt(2, patient.getAge());
            ps.setString(3, patient.getPhone());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Patient was saved but no patient ID was returned.");
    }

    public Patient findById(Connection con, int patientId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM patients WHERE patient_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Used to avoid registering the same person twice (same name and same phone). */
    public Patient findByNameAndPhone(Connection con, String name, String phone) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM patients WHERE LOWER(name) = LOWER(?) AND phone = ? "
                + "ORDER BY patient_id LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Searches by exact patient ID, part of the name, or part of the phone number. */
    public List<Patient> search(Connection con, String term, int limit) throws SQLException {
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        conditions.add("name LIKE ?");
        args.add("%" + escapeLike(term) + "%");

        if (term.matches("\\d{1,9}")) {
            conditions.add("patient_id = ?");
            args.add(Integer.parseInt(term));
        }
        String digits = term.replaceAll("\\D", "");
        if (digits.length() >= 3) {
            conditions.add("phone LIKE ?");
            args.add("%" + digits + "%");
        }

        String sql = "SELECT " + COLUMNS + " FROM patients WHERE " + String.join(" OR ", conditions)
                + " ORDER BY name, patient_id LIMIT ?";
        List<Patient> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (Object arg : args) {
                ps.setObject(i++, arg);
            }
            ps.setInt(i, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public List<Patient> findRecent(Connection con, int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM patients ORDER BY patient_id DESC LIMIT ?";
        List<Patient> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public int count(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM patients");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Patient map(ResultSet rs) throws SQLException {
        return new Patient(rs.getInt("patient_id"), rs.getString("name"), rs.getInt("age"),
                rs.getString("phone"), rs.getString("created_text"));
    }
}
