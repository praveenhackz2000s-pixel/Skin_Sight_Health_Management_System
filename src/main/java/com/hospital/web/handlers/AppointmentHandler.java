package com.hospital.web.handlers;

import com.hospital.model.Appointment;
import com.hospital.model.Doctor;
import com.hospital.model.DoctorWorkload;
import com.hospital.model.Recommendation;
import com.hospital.model.Visit;
import com.hospital.service.DoctorAssignmentService;
import com.hospital.service.ValidationException;
import com.hospital.web.FormUtils;
import com.hospital.web.HtmlPages;
import com.sun.net.httpserver.HttpExchange;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.hospital.web.HtmlPages.esc;

/**
 * Routes:
 *   GET  /appointments                list / waiting queue (filters: department, doctorId, show=all)
 *   GET  /appointments/new?visitId=   choose a doctor for a visit
 *   POST /appointments                create the appointment (auto = least busy eligible doctor)
 *   POST /appointments/status         start, complete or cancel an appointment
 */
public class AppointmentHandler extends BaseHandler {

    /** Only these local addresses are accepted as "go back to" targets (prevents open redirects). */
    private static final Pattern SAFE_BACK =
            Pattern.compile("^/(appointments|workload|doctors|visits)(/[a-z]+)?(\\?[A-Za-z0-9=&_.-]*)?$");

    private final DoctorAssignmentService assignmentService = new DoctorAssignmentService();

    @Override
    protected void route(HttpExchange exchange) throws Exception {
        String path = path(exchange);
        switch (path) {
            case "/appointments":
                if (isGet(exchange)) {
                    list(exchange);
                } else if (isPost(exchange)) {
                    create(exchange);
                } else {
                    methodNotAllowed(exchange, "GET, POST");
                }
                break;
            case "/appointments/new":
                if (isGet(exchange)) {
                    Map<String, List<String>> q = FormUtils.parseQuery(exchange);
                    Integer visitId = FormUtils.positiveInt(FormUtils.first(q, "visitId"));
                    if (visitId == null) {
                        badRequest(exchange, "A valid visit ID is required, for example /appointments/new?visitId=1.");
                    } else {
                        showAssignForm(exchange, visitId, Collections.emptyList(), 200);
                    }
                } else {
                    methodNotAllowed(exchange, "GET");
                }
                break;
            case "/appointments/status":
                if (isPost(exchange)) {
                    changeStatus(exchange);
                } else {
                    methodNotAllowed(exchange, "POST");
                }
                break;
            default:
                notFound(exchange);
        }
    }

    // ================================================================= list / queue

    private void list(HttpExchange exchange) throws Exception {
        Map<String, List<String>> q = FormUtils.parseQuery(exchange);
        String department = FormUtils.first(q, "department").toUpperCase();
        if (!Visit.isValidDepartment(department)) {
            department = null;
        }
        Integer doctorId = FormUtils.positiveInt(FormUtils.first(q, "doctorId"));
        boolean showAll = "all".equals(FormUtils.first(q, "show"));

        List<Appointment> appointments = assignmentService.getAppointments(doctorId, department, !showAll);
        List<Doctor> doctors = assignmentService.listDoctors(null);

        StringBuilder b = new StringBuilder();
        b.append(HtmlPages.notice(FormUtils.first(q, "notice")));
        b.append("<h1>").append(showAll ? "All appointments" : "Waiting queue").append("</h1>");
        b.append("<p class=\"lead\">").append(showAll
                ? "Newest first. Completed and cancelled appointments are included."
                : "Patients still to be seen, grouped by doctor: in consultation first, then urgent visits, then by queue number.")
                .append("</p>");

        b.append("<form method=\"get\" action=\"/appointments\" class=\"card filters\">")
                .append("<div class=\"field\"><label for=\"department\">Department</label><select id=\"department\" name=\"department\">")
                .append(option("", "All departments", department == null ? "" : department))
                .append(option("EYE", "Eye", department == null ? "" : department))
                .append(option("SKIN", "Skin", department == null ? "" : department)).append("</select></div>")
                .append("<div class=\"field\"><label for=\"doctorId\">Doctor</label><select id=\"doctorId\" name=\"doctorId\">")
                .append(option("", "All doctors", doctorId == null ? "" : String.valueOf(doctorId)));
        for (Doctor d : doctors) {
            b.append(option(String.valueOf(d.getDoctorId()), d.getName(), doctorId == null ? "" : String.valueOf(doctorId)));
        }
        b.append("</select></div>")
                .append("<div class=\"field\"><label for=\"show\">Show</label><select id=\"show\" name=\"show\">")
                .append(option("active", "Waiting and in progress", showAll ? "all" : "active"))
                .append(option("all", "Everything (history)", showAll ? "all" : "active")).append("</select></div>")
                .append("<div class=\"form-actions\"><button type=\"submit\" class=\"btn\">Apply filters</button></div></form>");

        b.append("<section class=\"card\">");
        if (appointments.isEmpty()) {
            b.append(HtmlPages.emptyState(showAll ? "No appointments match these filters." : "Nobody is waiting for these filters."));
        } else {
            b.append(appointmentTable(appointments, true, "/appointments"));
        }
        b.append("</section>");
        sendHtml(exchange, 200, HtmlPages.layout(showAll ? "All appointments" : "Waiting queue", "/appointments", b.toString()));
    }

    private static String option(String value, String label, String selectedValue) {
        return "<option value=\"" + esc(value) + "\"" + (value.equals(selectedValue) ? " selected" : "") + ">" + esc(label) + "</option>";
    }

    /**
     * Appointment table shared with the doctor page.
     * back = local address the browser returns to after a status button was used.
     */
    public static String appointmentTable(List<Appointment> appointments, boolean showDoctor, String back) {
        StringBuilder b = new StringBuilder();
        if (showDoctor) {
            b.append(HtmlPages.tableStart("Doctor", "Queue", "Date", "Patient", "Dept", "Priority", "Symptoms", "Status", "Actions"));
        } else {
            b.append(HtmlPages.tableStart("Queue", "Date", "Patient", "Dept", "Priority", "Symptoms", "Status", "Actions"));
        }
        for (Appointment a : appointments) {
            b.append("<tr").append(a.isUrgent() && a.isActive() ? " class=\"row-urgent\"" : "").append(">");
            if (showDoctor) {
                b.append("<td><a href=\"/doctors/view?id=").append(a.getDoctorId()).append("\">").append(esc(a.getDoctorName())).append("</a></td>");
            }
            b.append("<td><strong>").append(a.getQueueLabel()).append("</strong></td>")
                    .append("<td>").append(esc(a.getAppointmentDate())).append("</td>")
                    .append("<td><a href=\"/visits/view?id=").append(a.getVisitId()).append("\">").append(esc(a.getPatientName()))
                    .append("</a><br><small>visit #").append(a.getVisitId()).append("</small></td>")
                    .append("<td>").append(HtmlPages.departmentBadge(a.getDepartment())).append("</td>")
                    .append("<td>").append(HtmlPages.priorityBadge(a.getPriority())).append("</td>")
                    .append("<td>").append(esc(a.getSymptoms())).append("</td>")
                    .append("<td>").append(HtmlPages.statusBadge(a.getStatus())).append("</td><td class=\"nowrap\">");
            String id = String.valueOf(a.getAppointmentId());
            if (Appointment.WAITING.equals(a.getStatus())) {
                b.append(HtmlPages.postButton("/appointments/status", new String[][]{{"appointmentId", id}, {"status", Appointment.IN_PROGRESS}, {"back", back}}, "Start", ""));
                b.append(HtmlPages.postButton("/appointments/status", new String[][]{{"appointmentId", id}, {"status", Appointment.CANCELLED}, {"back", back}}, "Cancel", "btn-secondary"));
            } else if (Appointment.IN_PROGRESS.equals(a.getStatus())) {
                b.append(HtmlPages.postButton("/appointments/status", new String[][]{{"appointmentId", id}, {"status", Appointment.COMPLETED}, {"back", back}}, "Complete", ""));
                b.append(HtmlPages.postButton("/appointments/status", new String[][]{{"appointmentId", id}, {"status", Appointment.CANCELLED}, {"back", back}}, "Cancel", "btn-secondary"));
            } else {
                b.append("<span class=\"muted\">-</span>");
            }
            b.append("</td></tr>");
        }
        return b.append(HtmlPages.tableEnd()).toString();
    }

    // ================================================================= assign a doctor

    private void showAssignForm(HttpExchange exchange, int visitId, List<String> errors, int status) throws Exception {
        DoctorAssignmentService.AssignmentOptions options = assignmentService.getAssignmentOptions(visitId);
        if (options == null) {
            sendHtml(exchange, 404, HtmlPages.errorPage("Visit not found", "There is no visit with ID " + visitId + "."));
            return;
        }
        Visit visit = options.getVisit();

        StringBuilder b = new StringBuilder();
        b.append("<h1>Assign doctor</h1>");
        b.append(HtmlPages.alertList(errors));
        b.append("<section class=\"card\"><h2>Visit #").append(visit.getVisitId()).append("</h2><p>")
                .append(HtmlPages.departmentBadge(visit.getDepartment())).append(" ").append(HtmlPages.priorityBadge(visit.getPriority()))
                .append(" ").append(HtmlPages.statusBadge(visit.getStatus())).append(" &middot; ")
                .append("<a href=\"/patients/view?id=").append(visit.getPatientId()).append("\">").append(esc(visit.getPatientName()))
                .append("</a></p><p><strong>Symptoms:</strong> ").append(esc(visit.getSymptoms())).append("</p>");
        if (visit.isUrgent()) {
            b.append(HtmlPages.alert("error", "URGENT: inform the doctor directly. Urgent patients are placed ahead of routine patients in the doctor's queue."));
        }
        b.append("<h3>Suggested specialist category</h3><ul>");
        for (Recommendation r : options.getRecommendations()) {
            b.append("<li><strong>").append(esc(r.getSuggestedSpecialization())).append("</strong> ")
                    .append(HtmlPages.reviewBadge(r.getReviewStatus())).append("</li>");
        }
        b.append("</ul><p class=\"hint\">Suggestions are decision support only. Doctors of the General category and of the suggested categories "
                + "(that a doctor did not reject) are eligible.</p>");
        b.append("<p><a href=\"/visits/view?id=").append(visit.getVisitId()).append("\">Back to visit</a></p></section>");

        Appointment active = options.getActiveAppointment();
        if (active != null) {
            b.append(HtmlPages.alert("info", "This visit already has an active appointment: " + active.getDoctorName()
                    + ", queue " + active.getQueueLabel() + ". To reschedule, cancel it first on the Appointments page."));
        } else if (!visit.isOpen()) {
            b.append(HtmlPages.alert("info", "This visit is " + HtmlPages.statusLabel(visit.getStatus()).toLowerCase() + " and cannot be assigned."));
        } else if (options.getEligibleDoctors().isEmpty()) {
            b.append(HtmlPages.alert("warning", "No eligible doctor is available right now. The visit stays PENDING and appears on the dashboard. "
                    + "Check doctor availability on the Doctors page, then come back."));
            b.append("<p><a class=\"btn btn-secondary\" href=\"/doctors?department=").append(visit.getDepartment()).append("\">Open doctors</a></p>");
        } else {
            b.append("<form method=\"post\" action=\"/appointments\" class=\"card form\"><h2>Eligible doctors</h2>")
                    .append("<input type=\"hidden\" name=\"visitId\" value=\"").append(visit.getVisitId()).append("\">")
                    .append("<fieldset><legend>Choose how to assign</legend>")
                    .append("<label class=\"radio\"><input type=\"radio\" name=\"doctorId\" value=\"auto\" checked> ")
                    .append("<strong>Auto-assign</strong> the eligible doctor with the fewest active patients</label>");
            b.append(HtmlPages.tableStart("", "Doctor", "Specialization", "Active patients", "Waiting", "In progress"));
            for (DoctorWorkload w : options.getEligibleDoctors()) {
                Doctor d = w.getDoctor();
                b.append("<tr><td><input type=\"radio\" name=\"doctorId\" value=\"").append(d.getDoctorId()).append("\" id=\"doc").append(d.getDoctorId())
                        .append("\" aria-label=\"Assign to ").append(esc(d.getName())).append("\"></td>")
                        .append("<td><label for=\"doc").append(d.getDoctorId()).append("\">").append(esc(d.getName())).append("</label></td>")
                        .append("<td>").append(esc(d.getSpecialization())).append("</td><td><strong>").append(w.getActiveCount()).append("</strong></td>")
                        .append("<td>").append(w.getWaiting()).append("</td><td>").append(w.getInProgress()).append("</td></tr>");
            }
            b.append(HtmlPages.tableEnd()).append("</fieldset>")
                    .append("<div class=\"form-actions\"><button type=\"submit\" class=\"btn\">Assign and create queue number</button></div></form>");
        }
        sendHtml(exchange, status, HtmlPages.layout("Assign doctor", "/appointments", b.toString()));
    }

    private void create(HttpExchange exchange) throws Exception {
        Map<String, List<String>> form = FormUtils.parseBody(exchange);
        Integer visitId = FormUtils.positiveInt(FormUtils.first(form, "visitId"));
        String doctorText = FormUtils.first(form, "doctorId").trim();
        if (visitId == null) {
            badRequest(exchange, "A valid visit ID is required.");
            return;
        }
        try {
            int doctorId = 0;
            if (!doctorText.isEmpty() && !doctorText.equals("auto")) {
                Integer parsed = FormUtils.positiveInt(doctorText);
                if (parsed == null) {
                    throw new ValidationException("Choose a doctor from the list, or use auto-assign.");
                }
                doctorId = parsed;
            }
            DoctorAssignmentService.AssignmentResult result = assignmentService.assign(visitId, doctorId);
            redirect(exchange, "/visits/view?id=" + visitId + "&notice=" + (result.isAssigned() ? "assigned" : "no_doctor"));
        } catch (ValidationException e) {
            showAssignForm(exchange, visitId, e.getErrors(), 400);
        }
    }

    // ================================================================= status buttons

    private void changeStatus(HttpExchange exchange) throws Exception {
        Map<String, List<String>> form = FormUtils.parseBody(exchange);
        Integer appointmentId = FormUtils.positiveInt(FormUtils.first(form, "appointmentId"));
        String status = FormUtils.first(form, "status");
        String back = FormUtils.first(form, "back");
        if (!SAFE_BACK.matcher(back).matches()) {
            back = "/appointments";
        }
        if (appointmentId == null) {
            badRequest(exchange, "A valid appointment ID is required.");
            return;
        }
        try {
            assignmentService.updateStatus(appointmentId, status);
            redirect(exchange, back + (back.contains("?") ? "&" : "?") + "notice=status_updated");
        } catch (ValidationException e) {
            sendHtml(exchange, 400, HtmlPages.layout("Cannot change status", "/appointments",
                    HtmlPages.alertList(e.getErrors()) + "<p><a class=\"btn\" href=\"" + esc(back) + "\">Back</a></p>"));
        }
    }
}
