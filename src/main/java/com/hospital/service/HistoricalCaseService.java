package com.hospital.service;

import com.hospital.dao.HistoryDAO;
import com.hospital.dao.VisitDAO;
import com.hospital.database.DBConnection;
import com.hospital.model.SimilarCase;
import com.hospital.model.Visit;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds earlier visits with similar symptoms.
 *
 * Method (simple and documented): Jaccard similarity of the two symptom sets
 *     similarity = number of shared symptoms / number of unique symptoms in either set
 * The result is only a RANKING of earlier cases. 40% overlap does NOT mean "40% chance of a disease".
 * A diagnosis is shown only when an authorized doctor confirmed it.
 */
public class HistoricalCaseService {

    /** Upper bound of earlier visits that are compared (keeps the page fast). */
    private static final int MAX_COMPARED_VISITS = 2000;

    private final VisitDAO visitDAO = new VisitDAO();
    private final HistoryDAO historyDAO = new HistoryDAO();

    /** Ranked similar cases for a visit ID (empty list when the visit does not exist). */
    public List<SimilarCase> findSimilarCases(int visitId, int limit) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            Visit current = visitDAO.findById(con, visitId);
            if (current == null) {
                return new ArrayList<>();
            }
            return findSimilarCases(con, current, limit);
        }
    }

    public List<SimilarCase> findSimilarCases(Connection con, Visit current, int limit) throws SQLException {
        Set<String> currentSymptoms = SymptomUtils.toSet(current.getSymptoms());
        List<SimilarCase> cases = new ArrayList<>();
        if (currentSymptoms.isEmpty()) {
            return cases;
        }

        List<Visit> history = historyDAO.findHistoricalVisits(con, current.getDepartment(),
                current.getVisitId(), MAX_COMPARED_VISITS);
        for (Visit past : history) {
            Set<String> pastSymptoms = SymptomUtils.toSet(past.getSymptoms());
            Set<String> shared = new LinkedHashSet<>(currentSymptoms);
            shared.retainAll(pastSymptoms);
            if (shared.isEmpty()) {
                continue; // nothing in common
            }
            double score = jaccard(currentSymptoms, pastSymptoms);
            boolean confirmed = past.isDiagnosisConfirmed();
            cases.add(new SimilarCase(past.getVisitId(), past.getVisitDate(), past.getDepartment(),
                    past.getSymptoms(), new ArrayList<>(shared), score,
                    confirmed ? past.getConfirmedDiagnosis() : null,
                    confirmed ? past.getConfirmedByDoctorName() : null));
        }

        // List.sort is stable, so cases with the same score stay newest-first.
        cases.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (cases.size() > limit) {
            return new ArrayList<>(cases.subList(0, limit));
        }
        return cases;
    }

    /** Jaccard similarity of two sets: |A intersect B| / |A union B|. Returns 0 when both are empty. */
    public static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }
}
