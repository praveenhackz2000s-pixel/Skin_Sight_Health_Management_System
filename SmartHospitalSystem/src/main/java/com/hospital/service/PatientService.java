package com.hospital.service;

import com.hospital.dao.PatientDAO;
import com.hospital.database.DBConnection;
import com.hospital.model.Patient;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Business rules for patient registration and search (validation, duplicate check). */
public class PatientService {

    public static final int MIN_AGE = 0;
    public static final int MAX_AGE = 120;

    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}][\\p{L} .'\\-]{1,99}$");

    private final PatientDAO patientDAO = new PatientDAO();

    /** Result of a registration attempt: either a new patient, or the already registered one. */
    public static class RegisterResult {
        private final Patient patient;
        private final boolean created;

        public RegisterResult(Patient patient, boolean created) {
            this.patient = patient;
            this.created = created;
        }

        public Patient getPatient() {
            return patient;
        }

        /** false = a patient with the same name and phone already existed, nothing new was saved. */
        public boolean isCreated() {
            return created;
        }
    }

    /** Validates the form values, avoids duplicates, and saves the patient. */
    public RegisterResult register(String name, String ageText, String phone) throws ValidationException, SQLException {
        List<String> errors = new ArrayList<>();

        String cleanName = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (cleanName.isEmpty()) {
            errors.add("Patient name is required.");
        } else if (!NAME_PATTERN.matcher(cleanName).matches()) {
            errors.add("Name must be 2-100 characters and may contain only letters, spaces, dots, apostrophes and hyphens.");
        }

        int age = -1;
        try {
            age = Integer.parseInt(ageText == null ? "" : ageText.trim());
            if (age < MIN_AGE || age > MAX_AGE) {
                errors.add("Age must be between " + MIN_AGE + " and " + MAX_AGE + ".");
            }
        } catch (NumberFormatException e) {
            errors.add("Age must be a whole number.");
        }

        String cleanPhone = normalizePhone(phone);
        if (cleanPhone == null) {
            errors.add("Phone number must contain 7 to 15 digits (a leading + is allowed).");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        try (Connection con = DBConnection.getConnection()) {
            Patient existing = patientDAO.findByNameAndPhone(con, cleanName, cleanPhone);
            if (existing != null) {
                return new RegisterResult(existing, false);
            }
            Patient patient = new Patient(cleanName, age, cleanPhone);
            int id = patientDAO.insert(con, patient);
            patient.setPatientId(id);
            return new RegisterResult(patientDAO.findById(con, id), true);
        }
    }

    public Patient getPatient(int patientId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return patientDAO.findById(con, patientId);
        }
    }

    /** Search by patient ID, name or phone. An empty search is rejected. */
    public List<Patient> search(String term) throws ValidationException, SQLException {
        String clean = term == null ? "" : term.trim();
        if (clean.isEmpty()) {
            throw new ValidationException("Enter a patient ID, name or phone number to search.");
        }
        if (clean.length() > 100) {
            throw new ValidationException("Search text is too long.");
        }
        try (Connection con = DBConnection.getConnection()) {
            return patientDAO.search(con, clean, 50);
        }
    }

    public List<Patient> recentPatients(int limit) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return patientDAO.findRecent(con, limit);
        }
    }

    public int countPatients() throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return patientDAO.count(con);
        }
    }

    /**
     * Keeps digits and an optional leading +. Returns null when the number does not have 7-15 digits
     * or contains characters other than digits, spaces, dashes, dots and brackets.
     */
    static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        if (!trimmed.matches("^\\+?[0-9 ()\\-.]{6,25}$")) {
            return null;
        }
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.length() < 7 || digits.length() > 15) {
            return null;
        }
        return (trimmed.startsWith("+") ? "+" : "") + digits;
    }
}
