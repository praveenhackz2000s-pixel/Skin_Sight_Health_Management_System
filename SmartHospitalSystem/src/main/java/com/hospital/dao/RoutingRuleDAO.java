package com.hospital.dao;

import com.hospital.model.RoutingRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQL for the routing_rules table (the clinician-approved keyword rules). */
public class RoutingRuleDAO {

    public List<RoutingRule> findActiveByDepartment(Connection con, String department) throws SQLException {
        String sql = "SELECT rule_id, department, symptom_keyword, suggested_specialization, urgent, active "
                + "FROM routing_rules WHERE department = ? AND active = TRUE ORDER BY rule_id";
        List<RoutingRule> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, department);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new RoutingRule(rs.getInt("rule_id"), rs.getString("department"),
                            rs.getString("symptom_keyword"), rs.getString("suggested_specialization"),
                            rs.getBoolean("urgent"), rs.getBoolean("active")));
                }
            }
        }
        return result;
    }

    /** The controlled keyword list shown as check boxes on the visit form. */
    public List<String> findKeywords(Connection con, String department) throws SQLException {
        String sql = "SELECT DISTINCT symptom_keyword FROM routing_rules "
                + "WHERE department = ? AND active = TRUE ORDER BY symptom_keyword";
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, department);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        }
        return result;
    }
}
