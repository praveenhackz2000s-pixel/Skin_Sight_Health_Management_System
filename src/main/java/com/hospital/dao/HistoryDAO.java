package com.hospital.dao;

import com.hospital.model.Visit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Retrieves earlier visits of the same department so they can be compared with a new visit. */
public class HistoryDAO {

    /**
     * Returns earlier (non-cancelled) visits of one department, newest first, excluding the current visit.
     * The limit keeps the comparison fast even when the hospital has many years of data.
     */
    public List<Visit> findHistoricalVisits(Connection con, String department, int excludeVisitId, int limit)
            throws SQLException {
        String sql = VisitDAO.SELECT_BASE
                + "WHERE v.department = ? AND v.visit_id <> ? AND v.status <> 'CANCELLED' AND v.symptoms <> '' "
                + "ORDER BY v.visit_date DESC, v.visit_id DESC LIMIT ?";
        List<Visit> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, department);
            ps.setInt(2, excludeVisitId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(VisitDAO.map(rs));
                }
            }
        }
        return result;
    }
}
