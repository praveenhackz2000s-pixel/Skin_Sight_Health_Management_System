package com.hospital.dao;

import com.hospital.model.Visit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL for the visits table. Every method receives an open Connection from the service layer. */
public class VisitDAO {

    /** Shared SELECT used by this DAO and by HistoryDAO (patient name and confirming doctor are joined in). */
    public static final String SELECT_BASE =
            "SELECT v.visit_id, v.patient_id, p.name AS patient_name, v.department, v.symptoms, v.priority, "
                    + "v.clinical_findings, v.test_results, v.confirmed_diagnosis, v.confirmed_by_doctor_id, "
                    + "d.name AS confirmed_by_name, "
                    + "DATE_FORMAT(v.confirmed_at, '%d %b %Y %H:%i') AS confirmed_text, "
                    + "DATE_FORMAT(v.visit_date, '%d %b %Y %H:%i') AS visit_text, v.status "
                    + "FROM visits v "
                    + "JOIN patients p ON p.patient_id = v.patient_id "
                    + "LEFT JOIN doctors d ON d.doctor_id = v.confirmed_by_doctor_id ";

    public int insert(Connection con, Visit visit) throws SQLException {
        String sql = "INSERT INTO visits (patient_id, department, symptoms, priority, status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, visit.getPatientId());
            ps.setString(2, visit.getDepartment());
            ps.setString(3, visit.getSymptoms());
            ps.setString(4, visit.getPriority());
            ps.setString(5, visit.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Visit was saved but no visit ID was returned.");
    }

    public Visit findById(Connection con, int visitId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SELECT_BASE + "WHERE v.visit_id = ?")) {
            ps.setInt(1, visitId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Locks the visit row until the transaction ends, so two receptionists cannot book the same visit twice. */
    public Visit findByIdForUpdate(Connection con, int visitId) throws SQLException {
        try (PreparedStatement lock = con.prepareStatement("SELECT visit_id FROM visits WHERE visit_id = ? FOR UPDATE")) {
            lock.setInt(1, visitId);
            try (ResultSet rs = lock.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
            }
        }
        return findById(con, visitId);
    }

    public List<Visit> findByPatient(Connection con, int patientId) throws SQLException {
        String sql = SELECT_BASE + "WHERE v.patient_id = ? ORDER BY v.visit_date DESC, v.visit_id DESC";
        List<Visit> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    /** Visits that still need a doctor. Urgent ones come first. */
    public List<Visit> findPendingAssignment(Connection con, int limit) throws SQLException {
        String sql = SELECT_BASE + "WHERE v.status = ? "
                + "ORDER BY (v.priority = 'URGENT') DESC, v.visit_date ASC, v.visit_id ASC LIMIT ?";
        List<Visit> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, Visit.STATUS_PENDING);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public void updateStatus(Connection con, int visitId, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE visits SET status = ? WHERE visit_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, visitId);
            ps.executeUpdate();
        }
    }

    public void updateClinicalNotes(Connection con, int visitId, String findings, String testResults) throws SQLException {
        String sql = "UPDATE visits SET clinical_findings = ?, test_results = ? WHERE visit_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, findings);
            ps.setString(2, testResults);
            ps.setInt(3, visitId);
            ps.executeUpdate();
        }
    }

    /** Stores the diagnosis typed by a doctor, together with who confirmed it and when. */
    public void confirmDiagnosis(Connection con, int visitId, String diagnosis, int doctorId) throws SQLException {
        String sql = "UPDATE visits SET confirmed_diagnosis = ?, confirmed_by_doctor_id = ?, confirmed_at = NOW() "
                + "WHERE visit_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, diagnosis);
            ps.setInt(2, doctorId);
            ps.setInt(3, visitId);
            ps.executeUpdate();
        }
    }

    public int countRegisteredToday(Connection con) throws SQLException {
        return count(con, "SELECT COUNT(*) FROM visits WHERE DATE(visit_date) = CURDATE()");
    }

    public int countByStatus(Connection con, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM visits WHERE status = ?")) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int count(Connection con, String sql) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Converts one result row (from SELECT_BASE) into a Visit object. */
    public static Visit map(ResultSet rs) throws SQLException {
        Visit v = new Visit();
        v.setVisitId(rs.getInt("visit_id"));
        v.setPatientId(rs.getInt("patient_id"));
        v.setPatientName(rs.getString("patient_name"));
        v.setDepartment(rs.getString("department"));
        v.setSymptoms(rs.getString("symptoms"));
        v.setPriority(rs.getString("priority"));
        v.setClinicalFindings(rs.getString("clinical_findings"));
        v.setTestResults(rs.getString("test_results"));
        v.setConfirmedDiagnosis(rs.getString("confirmed_diagnosis"));
        int doctorId = rs.getInt("confirmed_by_doctor_id");
        v.setConfirmedByDoctorId(rs.wasNull() ? 0 : doctorId);
        v.setConfirmedByDoctorName(rs.getString("confirmed_by_name"));
        v.setConfirmedAt(rs.getString("confirmed_text"));
        v.setVisitDate(rs.getString("visit_text"));
        v.setStatus(rs.getString("status"));
        return v;
    }
}
