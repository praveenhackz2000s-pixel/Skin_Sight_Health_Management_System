package com.hospital.dao;

import com.hospital.model.Appointment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL for the appointments table. Every method receives an open Connection from the service layer. */
public class AppointmentDAO {

    private static final String SELECT_BASE =
            "SELECT a.appointment_id, a.visit_id, a.doctor_id, a.queue_number, "
                    + "DATE_FORMAT(a.appointment_date, '%d %b %Y') AS date_text, a.status, "
                    + "doc.name AS doctor_name, doc.specialization AS doctor_specialization, "
                    + "v.department, v.symptoms, v.priority, v.patient_id, p.name AS patient_name "
                    + "FROM appointments a "
                    + "JOIN doctors doc ON doc.doctor_id = a.doctor_id "
                    + "JOIN visits v ON v.visit_id = a.visit_id "
                    + "JOIN patients p ON p.patient_id = v.patient_id ";

    /** Next queue number for this doctor today: 1, 2, 3 ... (call while the doctor row is locked). */
    public int nextQueueNumber(Connection con, int doctorId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(queue_number), 0) + 1 FROM appointments "
                + "WHERE doctor_id = ? AND appointment_date = CURDATE()";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    public int insert(Connection con, Appointment appointment) throws SQLException {
        String sql = "INSERT INTO appointments (visit_id, doctor_id, queue_number, appointment_date, status) "
                + "VALUES (?, ?, ?, CURDATE(), 'WAITING')";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, appointment.getVisitId());
            ps.setInt(2, appointment.getDoctorId());
            ps.setInt(3, appointment.getQueueNumber());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Appointment was saved but no appointment ID was returned.");
    }

    public Appointment findById(Connection con, int appointmentId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SELECT_BASE + "WHERE a.appointment_id = ?")) {
            ps.setInt(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** The waiting or in-progress appointment of a visit, or null. */
    public Appointment findActiveByVisit(Connection con, int visitId) throws SQLException {
        String sql = SELECT_BASE + "WHERE a.visit_id = ? AND a.status IN ('WAITING', 'IN_PROGRESS') LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, visitId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** The newest appointment of a visit that was not cancelled (this is the "assigned doctor"). */
    public Appointment findLatestValidByVisit(Connection con, int visitId) throws SQLException {
        String sql = SELECT_BASE + "WHERE a.visit_id = ? AND a.status <> 'CANCELLED' "
                + "ORDER BY a.appointment_id DESC LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, visitId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * Queue / appointment list.
     * activeOnly = true  -> only WAITING and IN_PROGRESS, grouped by doctor, in the order patients will be seen
     *                       (in progress first, then urgent, then queue number).
     * activeOnly = false -> everything, newest first.
     */
    public List<Appointment> findList(Connection con, Integer doctorId, String department, boolean activeOnly, int limit)
            throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_BASE).append("WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (doctorId != null) {
            sql.append("AND a.doctor_id = ? ");
            args.add(doctorId);
        }
        if (department != null) {
            sql.append("AND v.department = ? ");
            args.add(department);
        }
        if (activeOnly) {
            sql.append("AND a.status IN ('WAITING', 'IN_PROGRESS') ")
                    .append("ORDER BY doc.name, a.appointment_date, "
                            + "FIELD(a.status, 'IN_PROGRESS', 'WAITING'), (v.priority = 'URGENT') DESC, a.queue_number ");
        } else {
            sql.append("ORDER BY a.appointment_date DESC, a.appointment_id DESC ");
        }
        sql.append("LIMIT ?");
        args.add(limit);

        List<Appointment> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int i = 1;
            for (Object arg : args) {
                ps.setObject(i++, arg);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    /** Most recent appointments of one doctor with a given status (used for "recently completed"). */
    public List<Appointment> findByDoctorAndStatus(Connection con, int doctorId, String status, int limit) throws SQLException {
        String sql = SELECT_BASE + "WHERE a.doctor_id = ? AND a.status = ? ORDER BY a.appointment_id DESC LIMIT ?";
        List<Appointment> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setString(2, status);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    /** Locks one appointment row and returns only its basic fields (id, visit, doctor, status). */
    public Appointment findForUpdate(Connection con, int appointmentId) throws SQLException {
        String sql = "SELECT appointment_id, visit_id, doctor_id, status FROM appointments WHERE appointment_id = ? FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Appointment a = new Appointment();
                a.setAppointmentId(rs.getInt("appointment_id"));
                a.setVisitId(rs.getInt("visit_id"));
                a.setDoctorId(rs.getInt("doctor_id"));
                a.setStatus(rs.getString("status"));
                return a;
            }
        }
    }

    public void updateStatus(Connection con, int appointmentId, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE appointments SET status = ? WHERE appointment_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, appointmentId);
            ps.executeUpdate();
        }
    }

    public int countByStatus(Connection con, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM appointments WHERE status = ?")) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static Appointment map(ResultSet rs) throws SQLException {
        Appointment a = new Appointment();
        a.setAppointmentId(rs.getInt("appointment_id"));
        a.setVisitId(rs.getInt("visit_id"));
        a.setDoctorId(rs.getInt("doctor_id"));
        a.setQueueNumber(rs.getInt("queue_number"));
        a.setAppointmentDate(rs.getString("date_text"));
        a.setStatus(rs.getString("status"));
        a.setDoctorName(rs.getString("doctor_name"));
        a.setDoctorSpecialization(rs.getString("doctor_specialization"));
        a.setDepartment(rs.getString("department"));
        a.setSymptoms(rs.getString("symptoms"));
        a.setPriority(rs.getString("priority"));
        a.setPatientId(rs.getInt("patient_id"));
        a.setPatientName(rs.getString("patient_name"));
        return a;
    }
}
