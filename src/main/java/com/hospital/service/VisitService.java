package com.hospital.service;

import com.hospital.dao.AppointmentDAO;
import com.hospital.dao.DoctorDAO;
import com.hospital.dao.PatientDAO;
import com.hospital.dao.RecommendationDAO;
import com.hospital.dao.RoutingRuleDAO;
import com.hospital.dao.VisitDAO;
import com.hospital.database.DBConnection;
import com.hospital.model.Appointment;
import com.hospital.model.Doctor;
import com.hospital.model.Patient;
import com.hospital.model.Recommendation;
import com.hospital.model.Visit;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Business rules for visits: creating a visit, rule suggestions, doctor review and clinical notes. */
public class VisitService {

    private static final int MAX_SYMPTOM_TEXT = 500;
    private static final int MAX_OTHER_INPUT = 300;
    private static final int MAX_NOTES = 2000;
    private static final int MAX_DIAGNOSIS = 500;

    private final PatientDAO patientDAO = new PatientDAO();
    private final VisitDAO visitDAO = new VisitDAO();
    private final RoutingRuleDAO ruleDAO = new RoutingRuleDAO();
    private final RecommendationDAO recommendationDAO = new RecommendationDAO();
    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final DoctorDAO doctorDAO = new DoctorDAO();
    private final SpecialistMatcher matcher = new SpecialistMatcher();

    /**
     * Saves a new visit and generates the rule-based suggestions in ONE transaction:
     * either the visit and its suggestions are both saved, or nothing is saved.
     */
    public Visit createVisit(int patientId, String department, List<String> selectedKeywords,
                             String otherSymptoms, boolean urgentFlag) throws ValidationException, SQLException {
        String dept = department == null ? "" : department.trim().toUpperCase(Locale.ROOT);
        if (!Visit.isValidDepartment(dept)) {
            throw new ValidationException("Department must be EYE or SKIN.");
        }

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Patient patient = patientDAO.findById(con, patientId);
                if (patient == null) {
                    throw new ValidationException("Patient ID " + patientId + " does not exist. Register the patient first.");
                }

                // Only controlled keywords of THIS department are accepted from the check boxes.
                Set<String> allowed = new HashSet<>(ruleDAO.findKeywords(con, dept));
                List<String> errors = new ArrayList<>();
                Set<String> symptoms = new LinkedHashSet<>();
                if (selectedKeywords != null) {
                    for (String raw : selectedKeywords) {
                        String keyword = SymptomUtils.normalizeOne(raw);
                        if (keyword.isEmpty()) {
                            continue;
                        }
                        if (allowed.contains(keyword)) {
                            symptoms.add(keyword);
                        } else {
                            errors.add("\"" + keyword + "\" is not a valid " + Visit.departmentLabel(dept) + " symptom option.");
                        }
                    }
                }
                if (otherSymptoms != null && otherSymptoms.length() > MAX_OTHER_INPUT) {
                    errors.add("Other symptoms must be at most " + MAX_OTHER_INPUT + " characters.");
                } else {
                    symptoms.addAll(SymptomUtils.toList(otherSymptoms));
                }
                if (symptoms.isEmpty() && errors.isEmpty()) {
                    errors.add("Select or type at least one symptom.");
                }
                if (symptoms.size() > SymptomUtils.MAX_PHRASES) {
                    errors.add("Please enter at most " + SymptomUtils.MAX_PHRASES + " symptoms.");
                }
                String joined = SymptomUtils.join(symptoms);
                if (joined.length() > MAX_SYMPTOM_TEXT) {
                    errors.add("The symptom list is too long.");
                }
                if (!errors.isEmpty()) {
                    throw new ValidationException(errors);
                }

                SpecialistMatcher.MatchResult match = matcher.findMatches(con, dept, joined);

                Visit visit = new Visit();
                visit.setPatientId(patientId);
                visit.setDepartment(dept);
                visit.setSymptoms(joined);
                visit.setPriority(urgentFlag || match.isUrgent() ? Visit.PRIORITY_URGENT : Visit.PRIORITY_ROUTINE);
                visit.setStatus(Visit.STATUS_PENDING);
                int visitId = visitDAO.insert(con, visit);
                visit.setVisitId(visitId);

                matcher.saveRecommendations(con, visitId, match);
                con.commit();
                return visit;
            } catch (ValidationException | SQLException | RuntimeException e) {
                DBConnection.rollbackQuietly(con);
                throw e;
            }
        }
    }

    public Visit getVisit(int visitId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return visitDAO.findById(con, visitId);
        }
    }

    public List<Visit> getVisitsForPatient(int patientId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return visitDAO.findByPatient(con, patientId);
        }
    }

    public List<Visit> getPendingVisits(int limit) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return visitDAO.findPendingAssignment(con, limit);
        }
    }

    public List<Recommendation> getRecommendations(int visitId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return recommendationDAO.findByVisit(con, visitId);
        }
    }

    /** The controlled symptom keywords of a department (they come from the routing rules). */
    public List<String> getSymptomChoices(String department) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return ruleDAO.findKeywords(con, department);
        }
    }

    public int countRegisteredToday() throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return visitDAO.countRegisteredToday(con);
        }
    }

    public int countPendingAssignment() throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return visitDAO.countByStatus(con, Visit.STATUS_PENDING);
        }
    }

    /** A doctor of the same department accepts or rejects a rule suggestion. */
    public void reviewRecommendation(int visitId, int recommendationId, int doctorId, String decision)
            throws ValidationException, SQLException {
        if (!Recommendation.isValidDecision(decision)) {
            throw new ValidationException("Choose Accept or Reject.");
        }
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Visit visit = visitDAO.findById(con, visitId);
                if (visit == null) {
                    throw new ValidationException("Visit " + visitId + " was not found.");
                }
                Recommendation rec = recommendationDAO.findById(con, recommendationId);
                if (rec == null || rec.getVisitId() != visitId) {
                    throw new ValidationException("That suggestion does not belong to this visit.");
                }
                Doctor doctor = doctorDAO.findById(con, doctorId);
                if (doctor == null || !doctor.isActive()) {
                    throw new ValidationException("Select an active doctor to record the review.");
                }
                if (!doctor.getDepartment().equals(visit.getDepartment())) {
                    throw new ValidationException("Only a doctor of the " + Visit.departmentLabel(visit.getDepartment())
                            + " department can review this suggestion.");
                }
                recommendationDAO.updateReview(con, recommendationId, decision, doctorId);
                con.commit();
            } catch (ValidationException | SQLException | RuntimeException e) {
                DBConnection.rollbackQuietly(con);
                throw e;
            }
        }
    }

    /**
     * Saves findings, test results and (optionally) the confirmed diagnosis.
     * Only the doctor who is assigned to the visit may do this. A diagnosis is only ever
     * stored from a doctor's own input - never from the rule engine.
     */
    public void saveClinicalNotes(int visitId, int doctorId, String findings, String testResults, String diagnosis)
            throws ValidationException, SQLException {
        String f = cleanText(findings);
        String t = cleanText(testResults);
        String d = cleanText(diagnosis).replace('\n', ' ');

        List<String> errors = new ArrayList<>();
        if (f.length() > MAX_NOTES) {
            errors.add("Clinical findings must be at most " + MAX_NOTES + " characters.");
        }
        if (t.length() > MAX_NOTES) {
            errors.add("Test results must be at most " + MAX_NOTES + " characters.");
        }
        if (d.length() > MAX_DIAGNOSIS) {
            errors.add("Diagnosis must be at most " + MAX_DIAGNOSIS + " characters.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Visit visit = visitDAO.findById(con, visitId);
                if (visit == null) {
                    throw new ValidationException("Visit " + visitId + " was not found.");
                }
                Appointment appointment = appointmentDAO.findLatestValidByVisit(con, visitId);
                if (appointment == null) {
                    throw new ValidationException("A doctor must be assigned to this visit before clinical notes can be saved.");
                }
                if (appointment.getDoctorId() != doctorId) {
                    throw new ValidationException("Only the assigned doctor (" + appointment.getDoctorName()
                            + ") can record findings or a diagnosis for this visit.");
                }
                Doctor doctor = doctorDAO.findById(con, doctorId);
                if (doctor == null || !doctor.isActive()) {
                    throw new ValidationException("The assigned doctor is not active.");
                }

                visitDAO.updateClinicalNotes(con, visitId, f.isEmpty() ? null : f, t.isEmpty() ? null : t);
                if (!d.isEmpty()) {
                    visitDAO.confirmDiagnosis(con, visitId, d, doctorId);
                }
                con.commit();
            } catch (ValidationException | SQLException | RuntimeException e) {
                DBConnection.rollbackQuietly(con);
                throw e;
            }
        }
    }

    /** Trims, unifies line endings and removes control characters (keeps new lines and tabs). */
    private static String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\n\t]]", "").trim();
    }
}
