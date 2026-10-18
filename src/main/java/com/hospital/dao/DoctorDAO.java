package com.hospital.dao;

import com.hospital.model.Doctor;
import com.hospital.model.DoctorWorkload;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQL for the doctors table, including workload counts calculated from the appointments table. */
public class DoctorDAO {

    private static final String DOCTOR_COLUMNS =
            "d.doctor_id, d.name, d.department, d.specialization, d.available, d.active";

    /** Workload = counts of appointments per status. Nothing is hardcoded. */
    private static final String WORKLOAD_SELECT =
            "SELECT " + DOCTOR_COLUMNS + ", "
                    + "COALESCE(SUM(a.status = 'WAITING'), 0) AS waiting, "
                    + "COALESCE(SUM(a.status = 'IN_PROGRESS'), 0) AS in_progress, "
                    + "COALESCE(SUM(a.status = 'COMPLETED'), 0) AS completed "
                    + "FROM doctors d LEFT JOIN appointments a ON a.doctor_id = d.doctor_id ";

    private static final String WORKLOAD_GROUP =
            " GROUP BY d.doctor_id, d.name, d.department, d.specialization, d.available, d.active ";

    public Doctor findById(Connection con, int doctorId) throws SQLException {
        String sql = "SELECT " + DOCTOR_COLUMNS + " FROM doctors d WHERE d.doctor_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapDoctor(rs) : null;
            }
        }
    }

    /** Locks the doctor row until the transaction ends (keeps queue numbers unique). */
    public Doctor findByIdForUpdate(Connection con, int doctorId) throws SQLException {
        String sql = "SELECT " + DOCTOR_COLUMNS + " FROM doctors d WHERE d.doctor_id = ? FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapDoctor(rs) : null;
            }
        }
    }

    /** All doctors, optionally only one department (pass null for all). */
    public List<Doctor> findAll(Connection con, String department) throws SQLException {
        String sql = "SELECT " + DOCTOR_COLUMNS + " FROM doctors d "
                + (department == null ? "" : "WHERE d.department = ? ")
                + "ORDER BY d.department, d.name";
        List<Doctor> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (department != null) {
                ps.setString(1, department);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapDoctor(rs));
                }
            }
        }
        return result;
    }

    /** Every doctor with workload counts, optionally for one department (pass null for all). */
    public List<DoctorWorkload> findWorkloads(Connection con, String department) throws SQLException {
        String sql = WORKLOAD_SELECT
                + (department == null ? "" : "WHERE d.department = ? ")
                + WORKLOAD_GROUP + "ORDER BY d.department, d.name";
        List<DoctorWorkload> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (department != null) {
                ps.setString(1, department);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapWorkload(rs));
                }
            }
        }
        return result;
    }

    public DoctorWorkload findWorkload(Connection con, int doctorId) throws SQLException {
        String sql = WORKLOAD_SELECT + "WHERE d.doctor_id = ?" + WORKLOAD_GROUP;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapWorkload(rs) : null;
            }
        }
    }

    /**
     * Doctors who may receive a patient: same department, active, available, and either
     * in the "General" category or having one of the recommended specializations.
     */
    public List<DoctorWorkload> findAssignable(Connection con, String department, List<String> specializations)
            throws SQLException {
        StringBuilder where = new StringBuilder(
                "WHERE d.department = ? AND d.active = TRUE AND d.available = TRUE AND (d.specialization LIKE ?");
        if (!specializations.isEmpty()) {
            where.append(" OR d.specialization IN (");
            for (int i = 0; i < specializations.size(); i++) {
                where.append(i == 0 ? "?" : ", ?");
            }
            where.append(")");
        }
        where.append(")");

        String sql = WORKLOAD_SELECT + where + WORKLOAD_GROUP + "ORDER BY d.doctor_id";
        List<DoctorWorkload> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, department);
            ps.setString(i++, Doctor.GENERAL_PREFIX + "%");
            for (String specialization : specializations) {
                ps.setString(i++, specialization);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapWorkload(rs));
                }
            }
        }
        return result;
    }

    public int updateAvailability(Connection con, int doctorId, boolean available) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE doctors SET available = ? WHERE doctor_id = ? AND active = TRUE")) {
            ps.setBoolean(1, available);
            ps.setInt(2, doctorId);
            return ps.executeUpdate();
        }
    }

    public int countAvailable(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM doctors WHERE active = TRUE AND available = TRUE");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static Doctor mapDoctor(ResultSet rs) throws SQLException {
        return new Doctor(rs.getInt("doctor_id"), rs.getString("name"), rs.getString("department"),
                rs.getString("specialization"), rs.getBoolean("available"), rs.getBoolean("active"));
    }

    private static DoctorWorkload mapWorkload(ResultSet rs) throws SQLException {
        return new DoctorWorkload(mapDoctor(rs), rs.getInt("waiting"), rs.getInt("in_progress"), rs.getInt("completed"));
    }
}
