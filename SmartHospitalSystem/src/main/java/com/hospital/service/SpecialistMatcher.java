package com.hospital.service;

import com.hospital.dao.RecommendationDAO;
import com.hospital.dao.RoutingRuleDAO;
import com.hospital.model.Recommendation;
import com.hospital.model.RoutingRule;
import com.hospital.model.Visit;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rule-based specialist suggestion.
 *
 * This is NOT machine learning and NOT a diagnosis. It simply checks the reported symptoms against
 * explicit rows of the routing_rules table ("keyword -> specialist category") that clinicians approve.
 * If nothing matches, it suggests a general department review instead of inventing a specialist.
 */
public class SpecialistMatcher {

    public static final String GENERAL_EYE = "General Ophthalmology";
    public static final String GENERAL_SKIN = "General Dermatology";

    private static final int MAX_REASON_LENGTH = 990;

    private final RoutingRuleDAO ruleDAO = new RoutingRuleDAO();
    private final RecommendationDAO recommendationDAO = new RecommendationDAO();

    /** What the matcher found for one visit. */
    public static class MatchResult {
        private final List<Recommendation> recommendations;
        private final boolean urgent;
        private final boolean fallback;

        public MatchResult(List<Recommendation> recommendations, boolean urgent, boolean fallback) {
            this.recommendations = recommendations;
            this.urgent = urgent;
            this.fallback = fallback;
        }

        public List<Recommendation> getRecommendations() {
            return recommendations;
        }

        /** True when at least one matched rule is a red-flag (urgent) keyword. */
        public boolean isUrgent() {
            return urgent;
        }

        /** True when no rule matched and the general category was suggested. */
        public boolean isFallback() {
            return fallback;
        }
    }

    /** Matches symptoms with the active rules of the department. Nothing is written to the database. */
    public MatchResult findMatches(Connection con, String department, String symptoms) throws SQLException {
        Set<String> symptomSet = SymptomUtils.toSet(symptoms);
        String text = SymptomUtils.join(symptomSet);

        Map<String, List<String>> keywordsBySpecialization = new LinkedHashMap<>();
        Map<String, List<String>> urgentKeywordsBySpecialization = new LinkedHashMap<>();
        boolean urgent = false;

        for (RoutingRule rule : ruleDAO.findActiveByDepartment(con, department)) {
            String keyword = SymptomUtils.normalizeOne(rule.getSymptomKeyword());
            if (keyword.isEmpty() || !matches(symptomSet, text, keyword)) {
                continue;
            }
            String specialization = rule.getSuggestedSpecialization();
            addOnce(keywordsBySpecialization, specialization, keyword);
            if (rule.isUrgent()) {
                urgent = true;
                addOnce(urgentKeywordsBySpecialization, specialization, keyword);
            }
        }

        List<Recommendation> recommendations = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : keywordsBySpecialization.entrySet()) {
            String specialization = entry.getKey();
            StringBuilder reason = new StringBuilder("Symptom keyword(s) matched a routing rule: ")
                    .append(quote(entry.getValue())).append(". ");
            List<String> redFlags = urgentKeywordsBySpecialization.get(specialization);
            if (redFlags != null) {
                reason.append("Red-flag symptom(s) ").append(quote(redFlags))
                        .append(" - flagged for urgent clinical review. ");
            }
            reason.append("Demonstration rule - requires clinical validation.");
            recommendations.add(new Recommendation(specialization, cut(reason.toString()), false));
        }

        boolean fallback = recommendations.isEmpty();
        if (fallback) {
            String general = Visit.DEPT_EYE.equals(department) ? GENERAL_EYE : GENERAL_SKIN;
            recommendations.add(new Recommendation(general,
                    "No specific routing rule matched the reported symptoms. General department review is suggested; "
                            + "reception or a doctor should decide the next step.", true));
        }
        return new MatchResult(recommendations, urgent, fallback);
    }

    /** Stores the suggestions (status PENDING) so a doctor can review them. */
    public void saveRecommendations(Connection con, int visitId, MatchResult result) throws SQLException {
        for (Recommendation rec : result.getRecommendations()) {
            recommendationDAO.insert(con, visitId, rec);
        }
    }

    /** A keyword matches when it is one of the symptom phrases, or appears inside a phrase as whole words. */
    private static boolean matches(Set<String> symptomSet, String text, String keyword) {
        if (symptomSet.contains(keyword)) {
            return true;
        }
        Pattern wholePhrase = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(keyword) + "(?![\\p{L}\\p{N}])");
        return wholePhrase.matcher(text).find();
    }

    private static void addOnce(Map<String, List<String>> map, String key, String value) {
        List<String> list = map.computeIfAbsent(key, k -> new ArrayList<>());
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private static String quote(List<String> keywords) {
        List<String> quoted = new ArrayList<>();
        for (String k : keywords) {
            quoted.add("'" + k + "'");
        }
        return String.join(", ", quoted);
    }

    private static String cut(String text) {
        return text.length() <= MAX_REASON_LENGTH ? text : text.substring(0, MAX_REASON_LENGTH);
    }
}
