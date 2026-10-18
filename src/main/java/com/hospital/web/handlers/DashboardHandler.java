package com.hospital.web.handlers;

import com.hospital.model.Patient;
import com.hospital.model.Visit;
import com.hospital.service.DoctorAssignmentService;
import com.hospital.service.PatientService;
import com.hospital.service.VisitService;
import com.hospital.web.HtmlPages;
import com.sun.net.httpserver.HttpExchange;

import java.util.List;

import static com.hospital.web.HtmlPages.esc;

/** GET / : summary cards, visits waiting for a doctor, and recently registered patients. */
public class DashboardHandler extends BaseHandler {

    private final PatientService patientService = new PatientService();
    private final VisitService visitService = new VisitService();
    private final DoctorAssignmentService assignmentService = new DoctorAssignmentService();

    @Override
    protected void route(HttpExchange exchange) throws Exception {
        String path = path(exchange);
        if (path.equals("/favicon.ico")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if (!path.equals("/")) {
            notFound(exchange);
            return;
        }
        if (!isGet(exchange)) {
            methodNotAllowed(exchange, "GET");
            return;
        }

        int patients = patientService.countPatients();
        int visitsToday = visitService.countRegisteredToday();
        int pending = visitService.countPendingAssignment();
        int waiting = assignmentService.countWaiting();
        int inProgress = assignmentService.countInProgress();
        int doctorsAvailable = assignmentService.countAvailableDoctors();
        List<Visit> pendingVisits = visitService.getPendingVisits(10);
        List<Patient> recent = patientService.recentPatients(5);

        StringBuilder b = new StringBuilder();
        b.append("<h1>Reception dashboard</h1>");
        b.append("<p class=\"lead\">Register patients, record visits, review rule-based suggestions and assign doctors.</p>");
        b.append("<p class=\"actions\"><a class=\"btn\" href=\"/patients/new\">Register new patient</a> ")
                .append("<a class=\"btn btn-secondary\" href=\"/patients/search\">Find existing patient</a></p>");

        b.append("<section aria-label=\"Summary\" class=\"stats\">");
        stat(b, "Patients registered", patients, "/patients/search");
        stat(b, "Visits registered today", visitsToday, "/appointments?show=all");
        stat(b, "Visits pending a doctor", pending, "#pending");
        stat(b, "Patients waiting", waiting, "/appointments");
        stat(b, "Patients in consultation", inProgress, "/appointments");
        stat(b, "Doctors available", doctorsAvailable, "/workload");
        b.append("</section>");

        b.append("<section class=\"card\" id=\"pending\"><h2>Visits waiting for a doctor</h2>");
        if (pendingVisits.isEmpty()) {
            b.append(HtmlPages.emptyState("No visits are waiting for assignment."));
        } else {
            b.append(HtmlPages.tableStart("Visit", "Patient", "Department", "Priority", "Symptoms", "Action"));
            for (Visit v : pendingVisits) {
                b.append("<tr><td><a href=\"/visits/view?id=").append(v.getVisitId()).append("\">#").append(v.getVisitId()).append("</a></td>")
                        .append("<td><a href=\"/patients/view?id=").append(v.getPatientId()).append("\">").append(esc(v.getPatientName())).append("</a></td>")
                        .append("<td>").append(HtmlPages.departmentBadge(v.getDepartment())).append("</td>")
                        .append("<td>").append(HtmlPages.priorityBadge(v.getPriority())).append("</td>")
                        .append("<td>").append(esc(v.getSymptoms())).append("</td>")
                        .append("<td><a class=\"btn btn-small\" href=\"/appointments/new?visitId=").append(v.getVisitId()).append("\">Assign doctor</a></td></tr>");
            }
            b.append(HtmlPages.tableEnd());
        }
        b.append("</section>");

        b.append("<section class=\"card\"><h2>Recently registered patients</h2>");
        if (recent.isEmpty()) {
            b.append(HtmlPages.emptyState("No patients registered yet."));
        } else {
            b.append(HtmlPages.tableStart("ID", "Name", "Age", "Registered"));
            for (Patient p : recent) {
                b.append("<tr><td>").append(p.getPatientId()).append("</td>")
                        .append("<td><a href=\"/patients/view?id=").append(p.getPatientId()).append("\">").append(esc(p.getName())).append("</a></td>")
                        .append("<td>").append(p.getAge()).append("</td><td>").append(esc(p.getCreatedAt())).append("</td></tr>");
            }
            b.append(HtmlPages.tableEnd());
        }
        b.append("</section>");

        sendHtml(exchange, 200, HtmlPages.layout("Dashboard", "/", b.toString()));
    }

    private static void stat(StringBuilder b, String label, int value, String href) {
        b.append("<a class=\"stat\" href=\"").append(href).append("\"><span class=\"stat-value\">").append(value)
                .append("</span><span class=\"stat-label\">").append(esc(label)).append("</span></a>");
    }
}
