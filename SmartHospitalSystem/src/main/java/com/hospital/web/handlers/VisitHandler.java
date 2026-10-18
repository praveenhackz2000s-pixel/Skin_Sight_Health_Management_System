package com.hospital.web.handlers;

import com.hospital.model.Appointment;
import com.hospital.model.Doctor;
import com.hospital.model.Patient;
import com.hospital.model.Recommendation;
import com.hospital.model.SimilarCase;
import com.hospital.model.Visit;
import com.hospital.service.DoctorAssignmentService;
import com.hospital.service.HistoricalCaseService;
import com.hospital.service.PatientService;
import com.hospital.service.SymptomUtils;
import com.hospital.service.ValidationException;
import com.hospital.service.VisitService;
import com.hospital.web.FormUtils;
import com.hospital.web.HtmlPages;
import com.sun.net.httpserver.HttpExchange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hospital.web.HtmlPages.esc;

/**
 * Routes:
 *   GET  /visits/new?patientId=..[&department=EYE|SKIN]   choose department, then fill the visit form
 *   POST /visits                                          save visit + generate suggestions
 *   GET  /visits/view?id=..                               visit page: suggestions, similar cases, appointment, clinical record
 *   POST /visits/review                                   a doctor accepts / rejects a suggestion
 *   POST /visits/clinical                                 the assigned doctor saves findings, tests, confirmed diagnosis
 *   GET  /history?visitId=..                              ranked similar earlier visits
 */
public class VisitHandler extends BaseHandler {

    private final PatientService patientService = new PatientService();
    private final VisitService visitService = new VisitService();
    private final HistoricalCaseService historyService = new HistoricalCaseService();
    private final DoctorAssignmentService assignmentService = new DoctorAssignmentService();

    @Override
    protected void route(HttpExchange exchange) throws Exception {
        String path = path(exchange);
        switch (path) {
            case "/visits/new":
                if (isGet(exchange)) {
                    newVisit(exchange);
                } else {
                    methodNotAllowed(exchange, "GET");
                }
                break;
            case "/visits":
                if (isPost(exchange)) {
                    saveVisit(exchange);
                } else {
                    methodNotAllowed(exchange, "POST");
                }
                break;
            case "/visits/view":
                if (isGet(exchange)) {
                    Map<String, List<String>> q = FormUtils.parseQuery(exchange);
                    Integer id = FormUtils.positiveInt(FormUtils.first(q, "id"));
                    if (id == null) {
                        badRequest(exchange, "A valid visit ID is required, for example /visits/view?id=1.");
                    } else {
                        showVisit(exchange, id, HtmlPages.notice(FormUtils.first(q, "notice")), 200, null);
                    }
                } else {
                    methodNotAllowed(exchange, "GET");
                }
                break;
            case "/visits/review":
                if (isPost(exchange)) {
                    reviewRecommendation(exchange);
                } else {
                    methodNotAllowed(exchange, "POST");
                }
                break;
            case "/visits/clinical":
                if (isPost(exchange)) {
                    saveClinical(exchange);
                } else {
                    methodNotAllowed(exchange, "POST");
                }
                break;
            case "/history":
                if (isGet(exchange)) {
                    history(exchange);
                } else {
                    methodNotAllowed(exchange, "GET");
                }
                break;
            default:
                notFound(exchange);
        }
    }

    // ================================================================= new visit

    private void newVisit(HttpExchange exchange) throws Exception {
        Map<String, List<String>> q = FormUtils.parseQuery(exchange);
        Integer patientId = FormUtils.positiveInt(FormUtils.first(q, "patientId"));
        if (patientId == null) {
            badRequest(exchange, "A valid patient ID is required to start a visit.");
            return;
        }
        Patient patient = patientService.getPatient(patientId);
        if (patient == null) {
            sendHtml(exchange, 404, HtmlPages.errorPage("Patient not found",
                    "There is no patient with ID " + patientId + ". Register the patient first."));
            return;
        }
        String department = FormUtils.first(q, "department").toUpperCase();
        if (department.isEmpty()) {
            showDepartmentChoice(exchange, patient);
        } else if (Visit.isValidDepartment(department)) {
            showVisitForm(exchange, patient, department, new HashSet<>(), "", false, null);
        } else {
            badRequest(exchange, "Department must be EYE or SKIN.");
        }
    }

    private void showDepartmentChoice(HttpExchange exchange, Patient patient) throws Exception {
        StringBuilder b = new StringBuilder();
        b.append("<h1>New visit</h1>");
        b.append("<p class=\"lead\">Patient: <a href=\"/patients/view?id=").append(patient.getPatientId()).append("\">")
                .append(esc(patient.getName())).append("</a> (ID ").append(patient.getPatientId()).append(", ")
                .append(patient.getAge()).append(" years)</p>");
        b.append("<h2>Step 1 of 2 - choose the department</h2><div class=\"choice-grid\">");
        for (String dept : new String[]{Visit.DEPT_EYE, Visit.DEPT_SKIN}) {
            b.append("<a class=\"choice\" href=\"/visits/new?patientId=").append(patient.getPatientId())
                    .append("&amp;department=").append(dept).append("\"><strong>").append(Visit.departmentLabel(dept))
                    .append(" department</strong><span>")
                    .append(Visit.DEPT_EYE.equals(dept) ? "Vision, eye pain, redness, retina and glaucoma concerns"
                            : "Rashes, acne, itching, hair and nail concerns")
                    .append("</span></a>");
        }
        b.append("</div>");
        sendHtml(exchange, 200, HtmlPages.layout("New visit", "/patients", b.toString()));
    }

    private void showVisitForm(HttpExchange exchange, Patient patient, String department, Set<String> selected,
                               String other, boolean urgent, List<String> errors) throws Exception {
        List<String> choices = visitService.getSymptomChoices(department);
        StringBuilder b = new StringBuilder();
        b.append("<h1>New ").append(Visit.departmentLabel(department).toLowerCase()).append(" visit</h1>");
        b.append("<p class=\"lead\">Patient: <a href=\"/patients/view?id=").append(patient.getPatientId()).append("\">")
                .append(esc(patient.getName())).append("</a> (ID ").append(patient.getPatientId()).append(") &middot; ")
                .append("<a href=\"/visits/new?patientId=").append(patient.getPatientId()).append("\">change department</a></p>");
        b.append(HtmlPages.alertList(errors));
        b.append("<form method=\"post\" action=\"/visits\" class=\"card form\">");
        b.append("<input type=\"hidden\" name=\"patientId\" value=\"").append(patient.getPatientId()).append("\">");
        b.append("<input type=\"hidden\" name=\"department\" value=\"").append(department).append("\">");
        b.append("<h2>Step 2 of 2 - reported symptoms</h2>");
        if (choices.isEmpty()) {
            b.append(HtmlPages.alert("warning", "No symptom keywords are configured for this department. "
                    + "You can still type the symptoms below."));
        } else {
            b.append("<fieldset><legend>Select all that apply</legend><div class=\"checks\">");
            int i = 0;
            for (String keyword : choices) {
                String id = "symptom" + (i++);
                b.append("<label class=\"check\" for=\"").append(id).append("\"><input type=\"checkbox\" id=\"").append(id)
                        .append("\" name=\"symptom\" value=\"").append(esc(keyword)).append("\"")
                        .append(selected.contains(keyword) ? " checked" : "").append("> ").append(esc(keyword)).append("</label>");
            }
            b.append("</div></fieldset>");
        }
        b.append(HtmlPages.textarea("other", "Other symptoms (optional)", other, 3, 300,
                "Separate several symptoms with commas, for example: headache, light sensitivity"));
        b.append("<div class=\"check-single\"><label class=\"check\" for=\"urgent\"><input type=\"checkbox\" id=\"urgent\" name=\"urgent\" value=\"on\"")
                .append(urgent ? " checked" : "").append("> Reception flags this visit as urgent (needs immediate clinical review)</label></div>");
        b.append("<div class=\"form-actions\"><button type=\"submit\" class=\"btn\">Save visit and get suggestions</button> ")
                .append("<a class=\"btn btn-secondary\" href=\"/patients/view?id=").append(patient.getPatientId()).append("\">Cancel</a></div>");
        b.append("</form>");
        sendHtml(exchange, errors == null || errors.isEmpty() ? 200 : 400,
                HtmlPages.layout("New visit", "/patients", b.toString()));
    }

    private void saveVisit(HttpExchange exchange) throws Exception {
        Map<String, List<String>> form = FormUtils.parseBody(exchange);
        Integer patientId = FormUtils.positiveInt(FormUtils.first(form, "patientId"));
        String department = FormUtils.first(form, "department").trim().toUpperCase();
        List<String> selected = FormUtils.all(form, "symptom");
        String other = FormUtils.first(form, "other");
        boolean urgent = "on".equals(FormUtils.first(form, "urgent"));

        if (patientId == null) {
            badRequest(exchange, "A valid patient ID is required.");
            return;
        }
        try {
            Visit visit = visitService.createVisit(patientId, department, selected, other, urgent);
            redirect(exchange, "/visits/view?id=" + visit.getVisitId() + "&notice=visit_created");
        } catch (ValidationException e) {
            Patient patient = patientService.getPatient(patientId);
            if (patient == null) {
                sendHtml(exchange, 404, HtmlPages.errorPage("Patient not found",
                        "Patient ID " + patientId + " does not exist. Register the patient first."));
            } else if (!Visit.isValidDepartment(department)) {
                badRequest(exchange, "Department must be EYE or SKIN.");
            } else {
                Set<String> selectedSet = new HashSet<>();
                for (String s : selected) {
                    selectedSet.add(SymptomUtils.normalizeOne(s));
                }
                showVisitForm(exchange, patient, department, selectedSet, other, urgent, e.getErrors());
            }
        }
    }

    // ================================================================= visit page

    /**
     * Renders the visit page. headerHtml is trusted HTML produced by this program (an alert).
     * draft (nullable) holds what a doctor typed if saving failed: [findings, tests, diagnosis].
     */
    private void showVisit(HttpExchange exchange, int visitId, String headerHtml, int status, String[] draft) throws Exception {
        Visit visit = visitService.getVisit(visitId);
        if (visit == null) {
            sendHtml(exchange, 404, HtmlPages.errorPage("Visit not found", "There is no visit with ID " + visitId + "."));
            return;
        }
        List<Recommendation> recommendations = visitService.getRecommendations(visitId);
        List<SimilarCase> similar = historyService.findSimilarCases(visitId, 5);
        Appointment appointment = assignmentService.getCurrentAppointment(visitId);
        List<Doctor> reviewers = new ArrayList<>();
        for (Doctor d : assignmentService.listDoctors(visit.getDepartment())) {
            if (d.isActive()) {
                reviewers.add(d);
            }
        }

        StringBuilder b = new StringBuilder();
        b.append(headerHtml);
        b.append("<h1>Visit #").append(visit.getVisitId()).append("</h1>");
        b.append("<p class=\"lead\">").append(HtmlPages.departmentBadge(visit.getDepartment())).append(" ")
                .append(HtmlPages.priorityBadge(visit.getPriority())).append(" ").append(HtmlPages.statusBadge(visit.getStatus()))
                .append(" &middot; Patient <a href=\"/patients/view?id=").append(visit.getPatientId()).append("\">")
                .append(esc(visit.getPatientName())).append("</a> &middot; ").append(esc(visit.getVisitDate())).append("</p>");

        if (visit.isUrgent()) {
            b.append(HtmlPages.alert("error", "URGENT: this visit was flagged for immediate clinical review. "
                    + "Inform the doctor directly; do not wait for the routine queue."));
        }

        // ---- symptoms
        b.append("<section class=\"card\"><h2>Reported symptoms</h2>")
                .append(HtmlPages.chips(SymptomUtils.toList(visit.getSymptoms()), null)).append("</section>");

        // ---- suggestions
        b.append("<section class=\"card\"><h2>Suggested specialist category</h2>");
        b.append("<p class=\"hint\">Suggestions come from <strong>demonstration routing rules that require clinical validation</strong>. ")
                .append("They only help choose a doctor. They are <strong>not a diagnosis</strong> and must be reviewed by a doctor.</p>");
        if (recommendations.isEmpty()) {
            b.append(HtmlPages.emptyState("No suggestions were stored for this visit."));
        } else {
            b.append(HtmlPages.tableStart("Suggested category", "Why it was suggested", "Doctor review", "Review this suggestion"));
            for (Recommendation r : recommendations) {
                b.append("<tr><td><strong>").append(esc(r.getSuggestedSpecialization())).append("</strong>")
                        .append(r.isFallback() ? " " + HtmlPages.badge("No rule matched", "badge-muted") : "")
                        .append("</td><td>").append(esc(r.getReason())).append("</td><td>")
                        .append(HtmlPages.reviewBadge(r.getReviewStatus()));
                if (r.getReviewedByDoctorName() != null) {
                    b.append("<br><small>by ").append(esc(r.getReviewedByDoctorName())).append("</small>");
                }
                b.append("</td><td>");
                appendReviewForm(b, visit, r, reviewers);
                b.append("</td></tr>");
            }
            b.append(HtmlPages.tableEnd());
        }
        b.append("</section>");

        // ---- similar historical cases
        b.append("<section class=\"card\"><h2>Similar earlier visits</h2>");
        b.append("<p class=\"hint\">Ranked by symptom overlap with earlier visits of the same department. ")
                .append("The percentage is a ranking score, <strong>not the chance that this patient has a disease</strong>.</p>");
        appendSimilarTable(b, similar);
        b.append("<p><a href=\"/history?visitId=").append(visit.getVisitId()).append("\">Open the full similar-cases page</a></p></section>");

        // ---- appointment
        b.append("<section class=\"card\"><h2>Doctor and queue</h2>");
        if (appointment != null) {
            b.append("<dl class=\"details\"><dt>Doctor</dt><dd><a href=\"/doctors/view?id=").append(appointment.getDoctorId()).append("\">")
                    .append(esc(appointment.getDoctorName())).append("</a> (").append(esc(appointment.getDoctorSpecialization())).append(")</dd>")
                    .append("<dt>Queue number</dt><dd><strong>").append(appointment.getQueueLabel()).append("</strong> on ")
                    .append(esc(appointment.getAppointmentDate())).append("</dd>")
                    .append("<dt>Appointment status</dt><dd>").append(HtmlPages.statusBadge(appointment.getStatus())).append("</dd></dl>");
        } else if (visit.isOpen()) {
            b.append("<p>No doctor assigned yet.</p><p><a class=\"btn\" href=\"/appointments/new?visitId=")
                    .append(visit.getVisitId()).append("\">Assign doctor</a></p>");
        } else {
            b.append(HtmlPages.emptyState("This visit is " + HtmlPages.statusLabel(visit.getStatus()).toLowerCase() + "."));
        }
        b.append("</section>");

        // ---- clinical record
        appendClinicalSection(b, visit, appointment, draft);

        sendHtml(exchange, status, HtmlPages.layout("Visit #" + visit.getVisitId(), "/patients", b.toString()));
    }

    private void appendReviewForm(StringBuilder b, Visit visit, Recommendation r, List<Doctor> reviewers) {
        String selectId = "reviewer" + r.getRecommendationId();
        b.append("<form method=\"post\" action=\"/visits/review\" class=\"review-form\">")
                .append("<input type=\"hidden\" name=\"visitId\" value=\"").append(visit.getVisitId()).append("\">")
                .append("<input type=\"hidden\" name=\"recommendationId\" value=\"").append(r.getRecommendationId()).append("\">")
                .append("<label class=\"sr-only\" for=\"").append(selectId).append("\">Reviewing doctor</label>")
                .append("<select id=\"").append(selectId).append("\" name=\"doctorId\" required>")
                .append("<option value=\"\">Reviewing doctor...</option>");
        for (Doctor d : reviewers) {
            b.append("<option value=\"").append(d.getDoctorId()).append("\">").append(esc(d.getName()))
                    .append(" - ").append(esc(d.getSpecialization())).append("</option>");
        }
        b.append("</select> <button type=\"submit\" name=\"decision\" value=\"ACCEPTED\" class=\"btn btn-small\">Accept</button> ")
                .append("<button type=\"submit\" name=\"decision\" value=\"REJECTED\" class=\"btn btn-small btn-secondary\">Reject</button></form>");
    }

    private void appendSimilarTable(StringBuilder b, List<SimilarCase> cases) {
        if (cases.isEmpty()) {
            b.append(HtmlPages.emptyState("No earlier visits share symptoms with this visit."));
            return;
        }
        b.append(HtmlPages.tableStart("Rank", "Symptom overlap", "Shared symptoms", "Earlier visit", "Doctor-confirmed diagnosis of that visit"));
        int rank = 1;
        for (SimilarCase c : cases) {
            b.append("<tr><td>").append(rank++).append("</td><td><strong>").append(c.getScorePercent()).append("%</strong></td><td>")
                    .append(HtmlPages.chips(c.getSharedSymptoms(), new HashSet<>(c.getSharedSymptoms()))).append("</td><td>#")
                    .append(c.getVisitId()).append("<br><small>").append(esc(c.getVisitDate())).append("</small></td><td>");
            if (c.hasConfirmedDiagnosis()) {
                b.append(esc(c.getConfirmedDiagnosis())).append("<br><small>confirmed by ").append(esc(c.getConfirmedByDoctorName())).append("</small>");
            } else {
                b.append("<span class=\"muted\">No confirmed diagnosis recorded</span>");
            }
            b.append("</td></tr>");
        }
        b.append(HtmlPages.tableEnd());
    }

    private void appendClinicalSection(StringBuilder b, Visit visit, Appointment appointment, String[] draft) {
        b.append("<section class=\"card\"><h2>Clinical record</h2>");
        if (visit.isDiagnosisConfirmed()) {
            b.append("<div class=\"dx dx-confirmed\"><strong>Doctor-confirmed diagnosis</strong><p>")
                    .append(esc(visit.getConfirmedDiagnosis())).append("</p><small>Confirmed by ")
                    .append(esc(visit.getConfirmedByDoctorName())).append(" on ").append(esc(visit.getConfirmedAt())).append("</small></div>");
        } else {
            b.append("<div class=\"dx dx-none\"><strong>No doctor-confirmed diagnosis yet.</strong> ")
                    .append("The specialist category above is only a routing suggestion, not a diagnosis.</div>");
        }

        if (draft == null) {
            if (visit.getClinicalFindings() != null) {
                b.append("<h3>Clinical findings</h3>").append(HtmlPages.multiline(visit.getClinicalFindings()));
            }
            if (visit.getTestResults() != null) {
                b.append("<h3>Test results</h3>").append(HtmlPages.multiline(visit.getTestResults()));
            }
        }

        if (appointment != null) {
            String findings = draft != null ? draft[0] : nullToEmpty(visit.getClinicalFindings());
            String tests = draft != null ? draft[1] : nullToEmpty(visit.getTestResults());
            String diagnosis = draft != null ? draft[2] : nullToEmpty(visit.getConfirmedDiagnosis());
            b.append("<h3>Update clinical record</h3><p class=\"hint\">Only the assigned doctor, <strong>")
                    .append(esc(appointment.getDoctorName())).append("</strong>, can save this section.</p>");
            b.append("<form method=\"post\" action=\"/visits/clinical\" class=\"form\">")
                    .append("<input type=\"hidden\" name=\"visitId\" value=\"").append(visit.getVisitId()).append("\">")
                    .append("<input type=\"hidden\" name=\"doctorId\" value=\"").append(appointment.getDoctorId()).append("\">");
            b.append(HtmlPages.textarea("findings", "Clinical findings", findings, 4, 2000, null));
            b.append(HtmlPages.textarea("testResults", "Test results", tests, 3, 2000, null));
            b.append(HtmlPages.field("diagnosis", "Confirmed diagnosis (typed by the doctor)", "text", diagnosis,
                    "maxlength=\"500\" autocomplete=\"off\"", "Leave empty if no diagnosis is confirmed yet."));
            b.append("<div class=\"form-actions\"><button type=\"submit\" class=\"btn\">Save clinical record</button></div></form>");
        } else {
            b.append("<p class=\"hint\">Assign a doctor to this visit to enable clinical notes.</p>");
        }
        b.append("</section>");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ================================================================= review + clinical POSTs

    private void reviewRecommendation(HttpExchange exchange) throws Exception {
        Map<String, List<String>> form = FormUtils.parseBody(exchange);
        Integer visitId = FormUtils.positiveInt(FormUtils.first(form, "visitId"));
        Integer recommendationId = FormUtils.positiveInt(FormUtils.first(form, "recommendationId"));
        Integer doctorId = FormUtils.positiveInt(FormUtils.first(form, "doctorId"));
        String decision = FormUtils.first(form, "decision");
        if (visitId == null) {
            badRequest(exchange, "A valid visit ID is required.");
            return;
        }
        try {
            if (recommendationId == null) {
                throw new ValidationException("A valid suggestion is required.");
            }
            if (doctorId == null) {
                throw new ValidationException("Select the reviewing doctor.");
            }
            visitService.reviewRecommendation(visitId, recommendationId, doctorId, decision);
            redirect(exchange, "/visits/view?id=" + visitId + "&notice=review_saved");
        } catch (ValidationException e) {
            showVisit(exchange, visitId, HtmlPages.alertList(e.getErrors()), 400, null);
        }
    }

    private void saveClinical(HttpExchange exchange) throws Exception {
        Map<String, List<String>> form = FormUtils.parseBody(exchange);
        Integer visitId = FormUtils.positiveInt(FormUtils.first(form, "visitId"));
        Integer doctorId = FormUtils.positiveInt(FormUtils.first(form, "doctorId"));
        String findings = FormUtils.first(form, "findings");
        String tests = FormUtils.first(form, "testResults");
        String diagnosis = FormUtils.first(form, "diagnosis");
        if (visitId == null) {
            badRequest(exchange, "A valid visit ID is required.");
            return;
        }
        try {
            if (doctorId == null) {
                throw new ValidationException("The assigned doctor is missing from the form.");
            }
            visitService.saveClinicalNotes(visitId, doctorId, findings, tests, diagnosis);
            redirect(exchange, "/visits/view?id=" + visitId + "&notice=clinical_saved");
        } catch (ValidationException e) {
            showVisit(exchange, visitId, HtmlPages.alertList(e.getErrors()), 400, new String[]{findings, tests, diagnosis});
        }
    }

    // ================================================================= history page

    private void history(HttpExchange exchange) throws Exception {
        Map<String, List<String>> q = FormUtils.parseQuery(exchange);
        Integer visitId = FormUtils.positiveInt(FormUtils.first(q, "visitId"));
        if (visitId == null) {
            badRequest(exchange, "A valid visit ID is required, for example /history?visitId=1.");
            return;
        }
        Visit visit = visitService.getVisit(visitId);
        if (visit == null) {
            sendHtml(exchange, 404, HtmlPages.errorPage("Visit not found", "There is no visit with ID " + visitId + "."));
            return;
        }
        List<SimilarCase> cases = historyService.findSimilarCases(visitId, 10);
        Set<String> currentSymptoms = new LinkedHashSet<>(SymptomUtils.toList(visit.getSymptoms()));

        StringBuilder b = new StringBuilder();
        b.append("<h1>Similar earlier visits</h1>");
        b.append("<p class=\"lead\">For <a href=\"/visits/view?id=").append(visit.getVisitId()).append("\">visit #")
                .append(visit.getVisitId()).append("</a> (").append(esc(visit.getPatientName())).append(", ")
                .append(Visit.departmentLabel(visit.getDepartment())).append(" department)</p>");
        b.append("<section class=\"card\"><h2>Current symptoms</h2>").append(HtmlPages.chips(new ArrayList<>(currentSymptoms), null)).append("</section>");

        b.append("<section class=\"card\"><h2>Top ").append(10).append(" earlier visits by symptom overlap</h2>");
        b.append("<p class=\"hint\"><strong>How the score works:</strong> shared symptoms divided by all different symptoms in both visits ")
                .append("(Jaccard similarity). It only ranks earlier cases. A score of 40% means 40% of the symptoms overlap &ndash; ")
                .append("<strong>it is not a 40% chance of any disease.</strong> A diagnosis is displayed only when a doctor confirmed it for that earlier visit.</p>");
        if (cases.isEmpty()) {
            b.append(HtmlPages.emptyState("No earlier visit shares a symptom with this visit."));
        } else {
            b.append(HtmlPages.tableStart("Rank", "Symptom overlap", "Symptoms of the earlier visit (shared are highlighted)", "Earlier visit", "Doctor-confirmed diagnosis"));
            int rank = 1;
            for (SimilarCase c : cases) {
                b.append("<tr><td>").append(rank++).append("</td><td><strong>").append(c.getScorePercent()).append("%</strong><br>")
                        .append("<meter min=\"0\" max=\"100\" value=\"").append(c.getScorePercent()).append("\">").append(c.getScorePercent()).append("%</meter></td><td>")
                        .append(HtmlPages.chips(SymptomUtils.toList(c.getSymptoms()), new HashSet<>(c.getSharedSymptoms()))).append("</td><td>#")
                        .append(c.getVisitId()).append("<br><small>").append(esc(c.getVisitDate())).append("</small></td><td>");
                if (c.hasConfirmedDiagnosis()) {
                    b.append(esc(c.getConfirmedDiagnosis())).append("<br><small>confirmed by ").append(esc(c.getConfirmedByDoctorName())).append("</small>");
                } else {
                    b.append("<span class=\"muted\">No confirmed diagnosis recorded</span>");
                }
                b.append("</td></tr>");
            }
            b.append(HtmlPages.tableEnd());
        }
        b.append("</section>");
        sendHtml(exchange, 200, HtmlPages.layout("Similar earlier visits", "/patients", b.toString()));
    }
}
