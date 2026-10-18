package com.hospital.service;

import com.hospital.dao.AppointmentDAO;
import com.hospital.dao.DoctorDAO;
import com.hospital.dao.RecommendationDAO;
import com.hospital.dao.VisitDAO;
import com.hospital.database.DBConnection;
import com.hospital.model.Appointment;
import com.hospital.model.Doctor;
import com.hospital.model.DoctorWorkload;
import com.hospital.model.Recommendation;
import com.hospital.model.Visit;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Doctor assignment, queue numbers, workload and appointment status changes.
 *
 * Eligible doctor = same department + active + available + (General category OR one of the
 * non-rejected recommended specializations). For routine assignment the eligible doctor with the
 * fewest ACTIVE appointments (waiting + in progress) is chosen. Urgent visits are always placed ahead
 * of routine ones in the queue, so workload balancing never delays a clinical emergency.
 */
public class DoctorAssignmentService {

    private final DoctorDAO doctorDAO = new DoctorDAO();
    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final VisitDAO visitDAO = new VisitDAO();
    private final RecommendationDAO recommendationDAO = new RecommendationDAO();

    /** Everything the appointment page needs to show for one visit. */
    public static class AssignmentOptions {
        private final Visit visit;
        private final List<Recommendation> recommendations;
        private final List<DoctorWorkload> eligibleDoctors; // sorted: lowest workload first
        private final Appointment activeAppointment;        // null when none

        public AssignmentOptions(Visit visit, List<Recommendation> recommendations,
                                 List<DoctorWorkload> eligibleDoctors, Appointment activeAppointment) {
            this.visit = visit;
            this.recommendations = recommendations;
            this.eligibleDoctors = eligibleDoctors;
            this.activeAppointment = activeAppointment;
        }

        public Visit getVisit() {
            return visit;
        }

        public List<Recommendation> getRecommendations() {
            return recommendations;
        }

        public List<DoctorWorkload> getEligibleDoctors() {
            return eligibleDoctors;
        }

        public Appointment getActiveAppointment() {
            return activeAppointment;
        }
    }

    /** Result of an assignment: either an appointment was created, or the visit stays pending. */
    public static class AssignmentResult {
        private final boolean assigned;
        private final Appointment appointment;

        public AssignmentResult(boolean assigned, Appointment appointment) {
            this.assigned = assigned;
            this.appointment = appointment;
        }

        public boolean isAssigned() {
            return assigned;
        }

        public Appointment getAppointment() {
            return appointment;
        }
    }

    // ----------------------------------------------------------------- reading

    public List<Doctor> listDoctors(String department) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return doctorDAO.findAll(con, department);
        }
    }

    public Doctor getDoctor(int doctorId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return doctorDAO.findById(con, doctorId);
        }
    }

    public List<DoctorWorkload> getWorkloads(String department) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return doctorDAO.findWorkloads(con, department);
        }
    }

    public DoctorWorkload getWorkload(int doctorId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return doctorDAO.findWorkload(con, doctorId);
        }
    }

    /** Appointment list; activeOnly = waiting and in-progress patients in the order they will be seen. */
    public List<Appointment> getAppointments(Integer doctorId, String department, boolean activeOnly) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return appointmentDAO.findList(con, doctorId, department, activeOnly, 200);
        }
    }

    public List<Appointment> getRecentCompleted(int doctorId, int limit) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return appointmentDAO.findByDoctorAndStatus(con, doctorId, Appointment.COMPLETED, limit);
        }
    }

    /** The assigned (not cancelled) appointment of a visit, or null. */
    public Appointment getCurrentAppointment(int visitId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return appointmentDAO.findLatestValidByVisit(con, visitId);
        }
    }

    public int countWaiting() throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return appointmentDAO.countByStatus(con, Appointment.WAITING);
        }
    }

    public int countInProgress() throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return appointmentDAO.countByStatus(con, Appointment.IN_PROGRESS);
        }
    }

    public int countAvailableDoctors() throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return doctorDAO.countAvailable(con);
        }
    }

    /** Returns null when the visit does not exist. */
    public AssignmentOptions getAssignmentOptions(int visitId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            Visit visit = visitDAO.findById(con, visitId);
            if (visit == null) {
                return null;
            }
            List<Recommendation> recommendations = recommendationDAO.findByVisit(con, visitId);
            List<DoctorWorkload> eligible = computeEligible(con, visit);
            Appointment active = appointmentDAO.findActiveByVisit(con, visitId);
            return new AssignmentOptions(visit, recommendations, eligible, active);
        }
    }

    // ----------------------------------------------------------------- assigning

    /**
     * Creates an appointment for a visit.
     *
     * @param requestedDoctorId a doctor chosen by reception, or 0 to auto-assign the least busy eligible doctor
     * @return assigned = false when no eligible doctor is available (the visit stays PENDING_ASSIGNMENT)
     */
    public AssignmentResult assign(int visitId, int requestedDoctorId) throws ValidationException, SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Visit visit = visitDAO.findByIdForUpdate(con, visitId); // blocks double booking
                if (visit == null) {
                    throw new ValidationException("Visit " + visitId + " was not found.");
                }
                if (!visit.isOpen()) {
                    throw new ValidationException("This visit is already " + visit.getStatus().toLowerCase().replace('_', ' ')
                            + " and cannot be assigned.");
                }
                Appointment existing = appointmentDAO.findActiveByVisit(con, visitId);
                if (existing != null) {
                    throw new ValidationException("This visit already has an active appointment with "
                            + existing.getDoctorName() + " (" + existing.getQueueLabel()
                            + "). Cancel it first if you need to reschedule.");
                }

                List<DoctorWorkload> eligible = computeEligible(con, visit);
                if (eligible.isEmpty()) {
                    con.commit();
                    return new AssignmentResult(false, null);
                }

                DoctorWorkload chosen = null;
                if (requestedDoctorId > 0) {
                    for (DoctorWorkload candidate : eligible) {
                        if (candidate.getDoctor().getDoctorId() == requestedDoctorId) {
                            chosen = candidate;
                        }
                    }
                    if (chosen == null) {
                        throw new ValidationException("The selected doctor is not eligible for this visit "
                                + "(wrong department or specialization, inactive, or not available).");
                    }
                } else {
                    chosen = eligible.get(0); // lowest active workload
                }

                // Lock the doctor row, then re-check, so queue numbers stay unique and unavailable doctors are never booked.
                Doctor doctor = doctorDAO.findByIdForUpdate(con, chosen.getDoctor().getDoctorId());
                if (doctor == null || !doctor.isActive() || !doctor.isAvailable()) {
                    throw new ValidationException("The doctor just became unavailable. Please try again.");
                }

                Appointment appointment = new Appointment();
                appointment.setVisitId(visitId);
                appointment.setDoctorId(doctor.getDoctorId());
                appointment.setQueueNumber(appointmentDAO.nextQueueNumber(con, doctor.getDoctorId()));
                int appointmentId = appointmentDAO.insert(con, appointment);
                visitDAO.updateStatus(con, visitId, Visit.STATUS_ASSIGNED);
                con.commit();

                return new AssignmentResult(true, appointmentDAO.findById(con, appointmentId));
            } catch (SQLIntegrityConstraintViolationException e) {
                DBConnection.rollbackQuietly(con);
                throw new ValidationException("The appointment conflicts with an existing one. Please refresh the page and try again.");
            } catch (ValidationException | SQLException | RuntimeException e) {
                DBConnection.rollbackQuietly(con);
                throw e;
            }
        }
    }

    /** Eligible doctors for a visit, least busy first. */
    private List<DoctorWorkload> computeEligible(Connection con, Visit visit) throws SQLException {
        List<String> specializations = new ArrayList<>();
        for (Recommendation rec : recommendationDAO.findByVisit(con, visit.getVisitId())) {
            if (!Recommendation.REJECTED.equals(rec.getReviewStatus())) {
                specializations.add(rec.getSuggestedSpecialization());
            }
        }
        List<DoctorWorkload> eligible = doctorDAO.findAssignable(con, visit.getDepartment(), specializations);
        eligible.sort(Comparator.comparingInt(DoctorWorkload::getActiveCount)
                .thenComparingInt(w -> w.getDoctor().getDoctorId()));
        return eligible;
    }

    // ----------------------------------------------------------------- status changes

    /**
     * Allowed moves: WAITING -> IN_PROGRESS or CANCELLED, IN_PROGRESS -> COMPLETED or CANCELLED.
     * The visit status follows the appointment. A cancelled appointment puts the visit back to
     * PENDING_ASSIGNMENT, which is how rescheduling works (book a new appointment afterwards).
     */
    public void updateStatus(int appointmentId, String newStatus) throws ValidationException, SQLException {
        if (!Appointment.isValidStatus(newStatus)) {
            throw new ValidationException("Unknown appointment status.");
        }
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Appointment appointment = appointmentDAO.findForUpdate(con, appointmentId);
                if (appointment == null) {
                    throw new ValidationException("Appointment " + appointmentId + " was not found.");
                }
                if (!isAllowedMove(appointment.getStatus(), newStatus)) {
                    throw new ValidationException("An appointment that is " + appointment.getStatus().toLowerCase().replace('_', ' ')
                            + " cannot be changed to " + newStatus.toLowerCase().replace('_', ' ') + ".");
                }
                appointmentDAO.updateStatus(con, appointmentId, newStatus);

                String visitStatus;
                switch (newStatus) {
                    case Appointment.IN_PROGRESS:
                        visitStatus = Visit.STATUS_IN_PROGRESS;
                        break;
                    case Appointment.COMPLETED:
                        visitStatus = Visit.STATUS_COMPLETED;
                        break;
                    default: // CANCELLED
                        visitStatus = Visit.STATUS_PENDING;
                        break;
                }
                visitDAO.updateStatus(con, appointment.getVisitId(), visitStatus);
                con.commit();
            } catch (ValidationException | SQLException | RuntimeException e) {
                DBConnection.rollbackQuietly(con);
                throw e;
            }
        }
    }

    private static boolean isAllowedMove(String from, String to) {
        if (Appointment.WAITING.equals(from)) {
            return Appointment.IN_PROGRESS.equals(to) || Appointment.CANCELLED.equals(to);
        }
        if (Appointment.IN_PROGRESS.equals(from)) {
            return Appointment.COMPLETED.equals(to) || Appointment.CANCELLED.equals(to);
        }
        return false;
    }

    /** Marks a doctor as available / not available (for example on leave). */
    public void setDoctorAvailability(int doctorId, boolean available) throws ValidationException, SQLException {
        try (Connection con = DBConnection.getConnection()) {
            if (doctorDAO.updateAvailability(con, doctorId, available) == 0) {
                Doctor doctor = doctorDAO.findById(con, doctorId);
                if (doctor == null) {
                    throw new ValidationException("Doctor " + doctorId + " was not found.");
                }
                if (!doctor.isActive()) {
                    throw new ValidationException("Inactive doctors cannot be changed.");
                }
                // otherwise the value was already what was requested: nothing to do
            }
        }
    }
}
