package com.hospital.dao;

import com.hospital.model.Recommendation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQL for the recommendations table (rule-based suggestions awaiting doctor review). */
public class RecommendationDAO {

    private static final String SELECT =
            "SELECT r.recommendation_id, r.visit_id, r.suggested_specialization, r.reason, r.review_status, "
                    + "d.name AS reviewer_name, DATE_FORMAT(r.created_at, '%d %b %Y %H:%i') AS created_text "
                    + "FROM recommendations r LEFT JOIN doctors d ON d.doctor_id = r.reviewed_by_doctor_id ";

    public void insert(Connection con, int visitId, Recommendation rec) throws SQLException {
        String sql = "INSERT INTO recommendations (visit_id, suggested_specialization, reason, review_status) "
                + "VALUES (?, ?, ?, 'PENDING')";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, visitId);
            ps.setString(2, rec.getSuggestedSpecialization());
            ps.setString(3, rec.getReason());
            ps.executeUpdate();
        }
    }

    public List<Recommendation> findByVisit(Connection con, int visitId) throws SQLException {
        List<Recommendation> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(SELECT + "WHERE r.visit_id = ? ORDER BY r.recommendation_id")) {
            ps.setInt(1, visitId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public Recommendation findById(Connection con, int recommendationId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SELECT + "WHERE r.recommendation_id = ?")) {
            ps.setInt(1, recommendationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public void updateReview(Connection con, int recommendationId, String decision, int doctorId) throws SQLException {
        String sql = "UPDATE recommendations SET review_status = ?, reviewed_by_doctor_id = ? WHERE recommendation_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, decision);
            ps.setInt(2, doctorId);
            ps.setInt(3, recommendationId);
            ps.executeUpdate();
        }
    }

    private static Recommendation map(ResultSet rs) throws SQLException {
        Recommendation r = new Recommendation();
        r.setRecommendationId(rs.getInt("recommendation_id"));
        r.setVisitId(rs.getInt("visit_id"));
        r.setSuggestedSpecialization(rs.getString("suggested_specialization"));
        r.setReason(rs.getString("reason"));
        r.setFallback(r.getReason() != null && r.getReason().startsWith("No specific routing rule matched"));
        r.setReviewStatus(rs.getString("review_status"));
        r.setReviewedByDoctorName(rs.getString("reviewer_name"));
        r.setCreatedAt(rs.getString("created_text"));
        return r;
    }
}
