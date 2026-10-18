package com.hospital.web.handlers;

import com.hospital.model.Patient;
import com.hospital.model.Visit;
import com.hospital.service.PatientService;
import com.hospital.service.ValidationException;
import com.hospital.service.VisitService;
import com.hospital.web.FormUtils;
import com.hospital.web.HtmlPages;
import com.sun.net.httpserver.HttpExchange;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.hospital.web.HtmlPages.esc;

/** Patient registration, search and the patient details / visit history page. */
public class PatientHandler extends BaseHandler {

    private final PatientService patientService = new PatientService();
    private final VisitService visitService = new VisitService();

    @Override
    protected void route(HttpExchange exchange) throws Exception {
        String path = path(exchange);
        switch (path) {
            case "/patients/new":
                if (isGet(exchange)) {
                    Map<String, List<String>> q = FormUtils.parseQuery(exchange);
                    showRegisterForm(exchange, FormUtils.first(q, "name"), "", "", null);
                } else {
                    methodNotAllowed(exchange, "GET");
                }
                break;
            case "/patients":
                if (isPost(exchange)) {
                    register(exchange);
                } else if (isGet(exchange)) {
                    redirect(exchange, "/patients/search");
                } else {
                    methodNotAllowed(exchange, "GET, POST");
                }
                break;
            case "/patients/search":
                if (isGet(exchange)) {
                    search(exchange);
                } else {
                    methodNotAllowed(exchange, "GET");
                }
                break;
            case "/patients/view":
                if (isGet(exchange)) {
                    view(exchange);
                } else {
                    methodNotAllowed(exchange, "GET");
                }
                break;
            default:
                notFound(exchange);
        }
    }

    // ----------------------------------------------------------------- registration

    private void showRegisterForm(HttpExchange exchange, String name, String age, String phone,
                                  List<String> errors) throws Exception {
        StringBuilder b = new StringBuilder();
        b.append("<h1>Register new patient</h1>");
        b.append("<p class=\"lead\">Search first (<a href=\"/patients/search\">Find Patient</a>) so the same person is not registered twice.</p>");
        b.append(HtmlPages.alertList(errors));
        b.append("<form method=\"post\" action=\"/patients\" class=\"card form\">");
        b.append(HtmlPages.field("name", "Full name", "text", name, "required maxlength=\"100\" autocomplete=\"off\"", null));
        b.append(HtmlPages.field("age", "Age (years)", "number", age,
                "required min=\"" + PatientService.MIN_AGE + "\" max=\"" + PatientService.MAX_AGE + "\" inputmode=\"numeric\"", null));
        b.append(HtmlPages.field("phone", "Phone number", "tel", phone,
                "required maxlength=\"25\" autocomplete=\"off\" inputmode=\"tel\"", "7 to 15 digits. A leading + is allowed."));
        b.append("<div class=\"form-actions\"><button type=\"submit\" class=\"btn\">Register patient</button> ")
                .append("<a class=\"btn btn-secondary\" href=\"/\">Cancel</a></div></form>");
        sendHtml(exchange, errors == null || errors.isEmpty() ? 200 : 400,
                HtmlPages.layout("Register patient", "/patients/new", b.toString()));
    }

    private void register(HttpExchange exchange) throws Exception {
        Map<String, List<String>> form = FormUtils.parseBody(exchange);
        String name = FormUtils.first(form, "name");
        String age = FormUtils.first(form, "age");
        String phone = FormUtils.first(form, "phone");
        try {
            PatientService.RegisterResult result = patientService.register(name, age, phone);
            String code = result.isCreated() ? "patient_created" : "patient_exists";
            redirect(exchange, "/patients/view?id=" + result.getPatient().getPatientId() + "&notice=" + code);
        } catch (ValidationException e) {
            showRegisterForm(exchange, name, age, phone, e.getErrors());
        }
    }

    // ----------------------------------------------------------------- search

    private void search(HttpExchange exchange) throws Exception {
        Map<String, List<String>> query = FormUtils.parseQuery(exchange);
        boolean searched = query.containsKey("q");
        String term = FormUtils.first(query, "q");

        List<String> errors = null;
        List<Patient> results = Collections.emptyList();
        if (searched) {
            try {
                results = patientService.search(term);
            } catch (ValidationException e) {
                errors = e.getErrors();
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("<h1>Find patient</h1>");
        b.append("<form method=\"get\" action=\"/patients/search\" class=\"card form search-form\">");
        b.append(HtmlPages.field("q", "Patient ID, name or phone number", "search", term,
                "maxlength=\"100\" autocomplete=\"off\" autofocus", null));
        b.append("<div class=\"form-actions\"><button type=\"submit\" class=\"btn\">Search</button></div></form>");
        b.append(HtmlPages.alertList(errors));

        if (searched && errors == null) {
            b.append("<section class=\"card\"><h2>Results</h2>");
            if (results.isEmpty()) {
                b.append(HtmlPages.emptyState("No patient matches \"" + term.trim() + "\"."));
                String link = term.trim().matches(".*\\d.*") ? "/patients/new" : "/patients/new?name=" + FormUtils.urlEncode(term.trim());
                b.append("<p><a class=\"btn\" href=\"").append(link).append("\">Register as a new patient</a></p>");
            } else {
                b.append(HtmlPages.tableStart("ID", "Name", "Age", "Phone", "Action"));
                for (Patient p : results) {
                    b.append("<tr><td>").append(p.getPatientId()).append("</td><td>").append(esc(p.getName())).append("</td>")
                            .append("<td>").append(p.getAge()).append("</td><td>").append(esc(p.getPhone())).append("</td>")
                            .append("<td><a class=\"btn btn-small\" href=\"/patients/view?id=").append(p.getPatientId()).append("\">Open</a> ")
                            .append("<a class=\"btn btn-small btn-secondary\" href=\"/visits/new?patientId=").append(p.getPatientId()).append("\">New visit</a></td></tr>");
                }
                b.append(HtmlPages.tableEnd());
                if (results.size() == 50) {
                    b.append("<p class=\"hint\">Only the first 50 matches are shown. Type more of the name or phone number to narrow the search.</p>");
                }
            }
            b.append("</section>");
        }
        sendHtml(exchange, 200, HtmlPages.layout("Find patient", "/patients/search", b.toString()));
    }

    // ----------------------------------------------------------------- details

    private void view(HttpExchange exchange) throws Exception {
        Map<String, List<String>> query = FormUtils.parseQuery(exchange);
        Integer id = FormUtils.positiveInt(FormUtils.first(query, "id"));
        if (id == null) {
            badRequest(exchange, "A valid patient ID is required, for example /patients/view?id=1.");
            return;
        }
        Patient patient = patientService.getPatient(id);
        if (patient == null) {
            sendHtml(exchange, 404, HtmlPages.errorPage("Patient not found", "There is no patient with ID " + id + "."));
            return;
        }
        List<Visit> visits = visitService.getVisitsForPatient(id);

        StringBuilder b = new StringBuilder();
        b.append(HtmlPages.notice(FormUtils.first(query, "notice")));
        b.append("<h1>").append(esc(patient.getName())).append("</h1>");
        b.append("<section class=\"card\"><h2>Patient details</h2><dl class=\"details\">")
                .append("<dt>Patient ID</dt><dd>").append(patient.getPatientId()).append("</dd>")
                .append("<dt>Age</dt><dd>").append(patient.getAge()).append(" years</dd>")
                .append("<dt>Phone</dt><dd>").append(esc(patient.getPhone())).append("</dd>")
                .append("<dt>Registered</dt><dd>").append(esc(patient.getCreatedAt())).append("</dd></dl>")
                .append("<p><a class=\"btn\" href=\"/visits/new?patientId=").append(patient.getPatientId()).append("\">Start new visit</a></p></section>");

        b.append("<section class=\"card\"><h2>Visit history</h2>");
        if (visits.isEmpty()) {
            b.append(HtmlPages.emptyState("This patient has no visits yet."));
        } else {
            b.append(HtmlPages.tableStart("Visit", "Date", "Department", "Priority", "Symptoms", "Status", "Confirmed diagnosis"));
            for (Visit v : visits) {
                b.append("<tr><td><a href=\"/visits/view?id=").append(v.getVisitId()).append("\">#").append(v.getVisitId()).append("</a></td>")
                        .append("<td>").append(esc(v.getVisitDate())).append("</td>")
                        .append("<td>").append(HtmlPages.departmentBadge(v.getDepartment())).append("</td>")
                        .append("<td>").append(HtmlPages.priorityBadge(v.getPriority())).append("</td>")
                        .append("<td>").append(esc(v.getSymptoms())).append("</td>")
                        .append("<td>").append(HtmlPages.statusBadge(v.getStatus())).append("</td>")
                        .append("<td>").append(v.isDiagnosisConfirmed()
                                ? esc(v.getConfirmedDiagnosis()) + "<br><small>by " + esc(v.getConfirmedByDoctorName()) + "</small>"
                                : "<span class=\"muted\">None confirmed</span>")
                        .append("</td></tr>");
            }
            b.append(HtmlPages.tableEnd());
        }
        b.append("</section>");
        sendHtml(exchange, 200, HtmlPages.layout(patient.getName(), "/patients", b.toString()));
    }
}
