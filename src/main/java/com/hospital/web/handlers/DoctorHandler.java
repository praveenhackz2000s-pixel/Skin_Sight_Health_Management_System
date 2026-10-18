package com.hospital.web.handlers;

import com.hospital.model.Appointment;
import com.hospital.model.Doctor;
import com.hospital.model.DoctorWorkload;
import com.hospital.model.Visit;
import com.hospital.service.DoctorAssignmentService;
import com.hospital.service.ValidationException;
import com.hospital.web.FormUtils;
import com.hospital.web.HtmlPages;
import com.sun.net.httpserver.HttpExchange;

import java.util.List;
import java.util.Map;

import static com.hospital.web.HtmlPages.esc;

/**
 * Routes:
 *   GET  /doctors[?department=EYE|SKIN]   doctor directory with active patient counts
 *   POST /doctors/availability            mark a doctor available / not available
 *   GET  /doctors/view?id=..              one doctor: workload, queue, recently completed
 *   GET  /workload[?department=..]        workload of every doctor
 */
public class DoctorHandler extends BaseHandler {

    private final DoctorAssignmentService assignmentService = new DoctorAssignmentService();

    @Override
    protected void route(HttpExchange exchange) throws Exception {
        String path = path(exchange);
        switch (path) {
            case "/doctors":
                if (isGet(exchange)) {
                    directory(exchange);
                } else {
                    methodNotAllowed(exchange, "GET");
                }
                break;
            case "/doctors/availability":
                if (isPost(exchange)) {
                    changeAvailability(exchange);
                } else {
                    methodNotAllowed(exchange, "POST");
                }
                break;
            case "/doctors/view":
                if (isGet(exchange)) {
                    doctorPage(exchange);
                } else {
                    methodNotAllowed(exchange, "GET");
                }
                break;
            case "/workload":
                if (isGet(exchange)) {
                    workload(exchange);
                } else {
                    methodNotAllowed(exchange, "GET");
                }
                break;
            default:
                notFound(exchange);
        }
    }

    private static String departmentParam(String raw) {
        String d = raw == null ? "" : raw.trim().toUpperCase();
        return Visit.isValidDepartment(d) ? d : null;
    }

    private static String filterLinks(String basePath, String department) {
        return "<nav class=\"filter-links\" aria-label=\"Filter by department\">"
                + filterLink(basePath, null, "All", department == null)
                + filterLink(basePath, Visit.DEPT_EYE, "Eye", Visit.DEPT_EYE.equals(department))
                + filterLink(basePath, Visit.DEPT_SKIN, "Skin", Visit.DEPT_SKIN.equals(department)) + "</nav>";
    }

    private static String filterLink(String basePath, String dept, String label, boolean active) {
        String href = dept == null ? basePath : basePath + "?department=" + dept;
        return "<a href=\"" + href + "\"" + (active ? " class=\"active\" aria-current=\"true\"" : "") + ">" + label + "</a>";
    }

    // ================================================================= directory

    private void directory(HttpExchange exchange) throws Exception {
        Map<String, List<String>> q = FormUtils.parseQuery(exchange);
        String department = departmentParam(FormUtils.first(q, "department"));
        List<DoctorWorkload> doctors = assignmentService.getWorkloads(department);

        StringBuilder b = new StringBuilder();
        b.append(HtmlPages.notice(FormUtils.first(q, "notice")));
        b.append("<h1>Doctors</h1>");
        b.append(filterLinks("/doctors", department));
        b.append("<section class=\"card\">");
        if (doctors.isEmpty()) {
            b.append(HtmlPages.emptyState("No doctors found."));
        } else {
            b.append(HtmlPages.tableStart("ID", "Doctor", "Department", "Specialization", "Status", "Active patients", "Actions"));
            for (DoctorWorkload w : doctors) {
                Doctor d = w.getDoctor();
                b.append("<tr><td>").append(d.getDoctorId()).append("</td>")
                        .append("<td><a href=\"/doctors/view?id=").append(d.getDoctorId()).append("\">").append(esc(d.getName())).append("</a></td>")
                        .append("<td>").append(HtmlPages.departmentBadge(d.getDepartment())).append("</td>")
                        .append("<td>").append(esc(d.getSpecialization())).append("</td>")
                        .append("<td>").append(HtmlPages.availabilityBadge(d.isAvailable(), d.isActive())).append("</td>")
                        .append("<td><strong>").append(w.getActiveCount()).append("</strong></td><td class=\"nowrap\">");
                if (d.isActive()) {
                    String[][] fields = {{"doctorId", String.valueOf(d.getDoctorId())},
                            {"available", d.isAvailable() ? "false" : "true"}, {"department", department == null ? "" : department}};
                    b.append(HtmlPages.postButton("/doctors/availability", fields,
                            d.isAvailable() ? "Mark not available" : "Mark available", "btn-secondary"));
                } else {
                    b.append("<span class=\"muted\">-</span>");
                }
                b.append("</td></tr>");
            }
            b.append(HtmlPages.tableEnd());
        }
        b.append("</section>");
        sendHtml(exchange, 200, HtmlPages.layout("Doctors", "/doctors", b.toString()));
    }

    private void changeAvailability(HttpExchange exchange) throws Exception {
        Map<String, List<String>> form = FormUtils.parseBody(exchange);
        Integer doctorId = FormUtils.positiveInt(FormUtils.first(form, "doctorId"));
        String available = FormUtils.first(form, "available");
        String department = departmentParam(FormUtils.first(form, "department"));
        if (doctorId == null || !(available.equals("true") || available.equals("false"))) {
            badRequest(exchange, "A valid doctor and availability value are required.");
            return;
        }
        try {
            assignmentService.setDoctorAvailability(doctorId, available.equals("true"));
            redirect(exchange, "/doctors?" + (department == null ? "" : "department=" + department + "&") + "notice=availability_updated");
        } catch (ValidationException e) {
            sendHtml(exchange, 400, HtmlPages.layout("Doctors", "/doctors",
                    HtmlPages.alertList(e.getErrors()) + "<p><a class=\"btn\" href=\"/doctors\">Back to doctors</a></p>"));
        }
    }

    // ================================================================= single doctor

    private void doctorPage(HttpExchange exchange) throws Exception {
        Map<String, List<String>> q = FormUtils.parseQuery(exchange);
        Integer id = FormUtils.positiveInt(FormUtils.first(q, "id"));
        if (id == null) {
            badRequest(exchange, "A valid doctor ID is required, for example /doctors/view?id=1.");
            return;
        }
        DoctorWorkload w = assignmentService.getWorkload(id);
        if (w == null) {
            sendHtml(exchange, 404, HtmlPages.errorPage("Doctor not found", "There is no doctor with ID " + id + "."));
            return;
        }
        Doctor d = w.getDoctor();
        List<Appointment> queue = assignmentService.getAppointments(id, null, true);
        List<Appointment> completed = assignmentService.getRecentCompleted(id, 10);

        StringBuilder b = new StringBuilder();
        b.append(HtmlPages.notice(FormUtils.first(q, "notice")));
        b.append("<h1>").append(esc(d.getName())).append("</h1>");
        b.append("<p class=\"lead\">").append(HtmlPages.departmentBadge(d.getDepartment())).append(" ")
                .append(esc(d.getSpecialization())).append(" ").append(HtmlPages.availabilityBadge(d.isAvailable(), d.isActive())).append("</p>");
        b.append("<section aria-label=\"Workload\" class=\"stats\">");
        stat(b, "Waiting", w.getWaiting());
        stat(b, "In consultation", w.getInProgress());
        stat(b, "Active patients", w.getActiveCount());
        stat(b, "Completed", w.getCompleted());
        b.append("</section>");

        b.append("<section class=\"card\"><h2>Assigned patients - waiting queue</h2>");
        if (queue.isEmpty()) {
            b.append(HtmlPages.emptyState("No patients are waiting for this doctor."));
        } else {
            b.append(AppointmentHandler.appointmentTable(queue, false, "/doctors/view?id=" + id));
        }
        b.append("</section>");

        b.append("<section class=\"card\"><h2>Recently completed</h2>");
        if (completed.isEmpty()) {
            b.append(HtmlPages.emptyState("No completed appointments yet."));
        } else {
            b.append(AppointmentHandler.appointmentTable(completed, false, "/doctors/view?id=" + id));
        }
        b.append("</section>");
        sendHtml(exchange, 200, HtmlPages.layout(d.getName(), "/doctors", b.toString()));
    }

    private static void stat(StringBuilder b, String label, int value) {
        b.append("<div class=\"stat\"><span class=\"stat-value\">").append(value)
                .append("</span><span class=\"stat-label\">").append(esc(label)).append("</span></div>");
    }

    // ================================================================= workload

    private void workload(HttpExchange exchange) throws Exception {
        Map<String, List<String>> q = FormUtils.parseQuery(exchange);
        String department = departmentParam(FormUtils.first(q, "department"));
        List<DoctorWorkload> list = assignmentService.getWorkloads(department);
        int max = 1;
        for (DoctorWorkload w : list) {
            max = Math.max(max, w.getActiveCount());
        }

        StringBuilder b = new StringBuilder();
        b.append("<h1>Doctor workload</h1>");
        b.append("<p class=\"lead\">Counts are calculated live from the appointments table. Active = waiting + in progress.</p>");
        b.append(filterLinks("/workload", department));
        b.append("<section class=\"card\">");
        if (list.isEmpty()) {
            b.append(HtmlPages.emptyState("No doctors found."));
        } else {
            b.append(HtmlPages.tableStart("Doctor", "Department", "Specialization", "Availability", "Waiting", "In progress", "Active patients", "Completed"));
            for (DoctorWorkload w : list) {
                Doctor d = w.getDoctor();
                b.append("<tr><td><a href=\"/doctors/view?id=").append(d.getDoctorId()).append("\">").append(esc(d.getName())).append("</a></td>")
                        .append("<td>").append(HtmlPages.departmentBadge(d.getDepartment())).append("</td>")
                        .append("<td>").append(esc(d.getSpecialization())).append("</td>")
                        .append("<td>").append(HtmlPages.availabilityBadge(d.isAvailable(), d.isActive())).append("</td>")
                        .append("<td>").append(w.getWaiting()).append("</td><td>").append(w.getInProgress()).append("</td>")
                        .append("<td><strong>").append(w.getActiveCount()).append("</strong> <meter min=\"0\" max=\"").append(max)
                        .append("\" value=\"").append(w.getActiveCount()).append("\">").append(w.getActiveCount()).append("</meter></td>")
                        .append("<td>").append(w.getCompleted()).append("</td></tr>");
            }
            b.append(HtmlPages.tableEnd());
        }
        b.append("</section>");
        sendHtml(exchange, 200, HtmlPages.layout("Workload", "/workload", b.toString()));
    }
}
