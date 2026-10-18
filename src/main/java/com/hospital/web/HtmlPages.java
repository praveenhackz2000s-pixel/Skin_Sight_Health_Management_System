package com.hospital.web;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared HTML building blocks: the page layout (header, navigation, footer), alerts, badges and form fields.
 * Every piece of dynamic text goes through esc() so user input can never inject HTML.
 */
public final class HtmlPages {

    public static final String HOSPITAL_NAME = "Smart Eye & Skin Specialist Hospital";

    private static final String[][] NAV = {
            {"/", "Dashboard"},
            {"/patients/new", "Register Patient"},
            {"/patients/search", "Find Patient"},
            {"/doctors", "Doctors"},
            {"/appointments", "Appointments"},
            {"/workload", "Workload"}
    };

    private HtmlPages() {
    }

    /** Escapes text for safe use inside HTML content and attribute values. */
    public static String esc(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Wraps page content in the common layout. activePath highlights the matching navigation link. */
    public static String layout(String title, String activePath, String body) {
        StringBuilder sb = new StringBuilder(body.length() + 2048);
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
                .append("<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>").append(esc(title)).append(" - ").append(esc(HOSPITAL_NAME)).append("</title>\n")
                .append("<link rel=\"stylesheet\" href=\"/static/style.css?v=3\">\n")
                .append("</head>\n<body>\n")
                .append("<a class=\"skip-link\" href=\"#main\">Skip to content</a>\n")
                .append("<header class=\"site-header\"><div class=\"container header-inner\">")
                .append("<a class=\"brand\" href=\"/\"><img class=\"brand-mark\" src=\"/static/logo-mark.png?v=1\" alt=\"\" aria-hidden=\"true\">")
                .append("<span class=\"brand-text\">").append(esc(HOSPITAL_NAME)).append("</span></a>")
                .append("<nav aria-label=\"Main navigation\"><ul class=\"nav\">");
        for (String[] item : NAV) {
            boolean active = item[0].equals("/") ? "/".equals(activePath) : activePath != null && activePath.startsWith(item[0]);
            sb.append("<li><a href=\"").append(item[0]).append("\"")
                    .append(active ? " class=\"active\" aria-current=\"page\"" : "")
                    .append(">").append(esc(item[1])).append("</a></li>");
        }
        sb.append("</ul></nav></div></header>\n")
                .append("<main id=\"main\" class=\"container\">\n").append(body).append("\n</main>\n")
                .append("</body>\n</html>\n");
        return sb.toString();
    }

    public static String errorPage(String heading, String message) {
        String body = "<section class=\"card narrow\"><h1>" + esc(heading) + "</h1><p>" + esc(message)
                + "</p><p><a class=\"btn\" href=\"/\">Back to dashboard</a></p></section>";
        return layout(heading, "", body);
    }

    // ----------------------------------------------------------------- alerts

    /** type: success, error, warning or info. The message is escaped. */
    public static String alert(String type, String message) {
        String role = "error".equals(type) || "warning".equals(type) ? "alert" : "status";
        return "<div class=\"alert alert-" + type + "\" role=\"" + role + "\">" + esc(message) + "</div>";
    }

    /** A red box listing validation problems; returns "" when the list is empty. */
    public static String alertList(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<div class=\"alert alert-error\" role=\"alert\"><strong>Please fix the following:</strong><ul>");
        for (String e : errors) {
            sb.append("<li>").append(esc(e)).append("</li>");
        }
        return sb.append("</ul></div>").toString();
    }

    /** Messages shown after a redirect. Only fixed codes are accepted, so URLs cannot inject text. */
    public static String notice(String code) {
        if (code == null) {
            return "";
        }
        switch (code) {
            case "patient_created":
                return alert("success", "Patient registered. The patient ID below can be used at every counter.");
            case "patient_exists":
                return alert("info", "This patient is already registered (same name and phone). No duplicate was created.");
            case "visit_created":
                return alert("success", "Visit saved. Specialist suggestions and similar earlier cases were generated below.");
            case "assigned":
                return alert("success", "Doctor assigned and queue number created.");
            case "no_doctor":
                return alert("warning", "No eligible, available doctor right now. The visit stays PENDING and can be assigned later.");
            case "review_saved":
                return alert("success", "Review saved.");
            case "clinical_saved":
                return alert("success", "Clinical record saved.");
            case "status_updated":
                return alert("success", "Appointment status updated.");
            case "availability_updated":
                return alert("success", "Doctor availability updated.");
            default:
                return "";
        }
    }

    // ----------------------------------------------------------------- badges and small pieces

    public static String badge(String text, String cssClass) {
        return "<span class=\"badge " + cssClass + "\">" + esc(text) + "</span>";
    }

    public static String departmentBadge(String department) {
        return "EYE".equals(department) ? badge("Eye", "badge-eye") : badge("Skin", "badge-skin");
    }

    public static String priorityBadge(String priority) {
        return "URGENT".equals(priority) ? badge("URGENT", "badge-urgent") : badge("Routine", "badge-muted");
    }

    public static String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        String text = status.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Works for both visit and appointment statuses. */
    public static String statusBadge(String status) {
        String css;
        switch (status == null ? "" : status) {
            case "COMPLETED":
                css = "badge-ok";
                break;
            case "IN_PROGRESS":
                css = "badge-info";
                break;
            case "WAITING":
            case "ASSIGNED":
                css = "badge-wait";
                break;
            case "CANCELLED":
                css = "badge-muted";
                break;
            default:
                css = "badge-warn"; // PENDING_ASSIGNMENT
        }
        return badge(statusLabel(status), css);
    }

    public static String reviewBadge(String reviewStatus) {
        switch (reviewStatus == null ? "" : reviewStatus) {
            case "ACCEPTED":
                return badge("Accepted by doctor", "badge-ok");
            case "REJECTED":
                return badge("Rejected by doctor", "badge-muted");
            default:
                return badge("Awaiting doctor review", "badge-warn");
        }
    }

    public static String availabilityBadge(boolean available, boolean active) {
        if (!active) {
            return badge("Inactive", "badge-muted");
        }
        return available ? badge("Available", "badge-ok") : badge("Not available", "badge-warn");
    }

    /** Symptom phrases as small "chips". Phrases listed in highlight get a stronger colour. */
    public static String chips(List<String> symptoms, Set<String> highlight) {
        StringBuilder sb = new StringBuilder("<ul class=\"chips\">");
        for (String s : symptoms) {
            boolean marked = highlight != null && highlight.contains(s);
            sb.append("<li class=\"chip").append(marked ? " chip-shared" : "").append("\">").append(esc(s)).append("</li>");
        }
        return sb.append("</ul>").toString();
    }

    /** Free text with line breaks preserved (CSS white-space: pre-wrap). */
    public static String multiline(String text) {
        return "<p class=\"pre\">" + esc(text) + "</p>";
    }

    public static String emptyState(String message) {
        return "<p class=\"empty\">" + esc(message) + "</p>";
    }

    /** Opening tag of a table that scrolls sideways on small screens. */
    public static String tableStart(String... headings) {
        StringBuilder sb = new StringBuilder("<div class=\"table-wrap\"><table><thead><tr>");
        for (String h : headings) {
            sb.append("<th scope=\"col\">").append(esc(h)).append("</th>");
        }
        return sb.append("</tr></thead><tbody>").toString();
    }

    public static String tableEnd() {
        return "</tbody></table></div>";
    }

    /** Label + input + optional hint. attrs is trusted markup written by the programmer, never user input. */
    public static String field(String id, String label, String type, String value, String attrs, String hint) {
        StringBuilder sb = new StringBuilder("<div class=\"field\"><label for=\"").append(id).append("\">")
                .append(esc(label)).append("</label><input id=\"").append(id).append("\" name=\"").append(id)
                .append("\" type=\"").append(type).append("\" value=\"").append(esc(value)).append("\" ")
                .append(attrs == null ? "" : attrs).append(">");
        if (hint != null && !hint.isEmpty()) {
            sb.append("<small>").append(esc(hint)).append("</small>");
        }
        return sb.append("</div>").toString();
    }

    public static String textarea(String id, String label, String value, int rows, int maxLength, String hint) {
        StringBuilder sb = new StringBuilder("<div class=\"field\"><label for=\"").append(id).append("\">")
                .append(esc(label)).append("</label><textarea id=\"").append(id).append("\" name=\"").append(id)
                .append("\" rows=\"").append(rows).append("\" maxlength=\"").append(maxLength).append("\">")
                .append(esc(value)).append("</textarea>");
        if (hint != null && !hint.isEmpty()) {
            sb.append("<small>").append(esc(hint)).append("</small>");
        }
        return sb.append("</div>").toString();
    }

    /** Small POST form with hidden fields and one button (used for status changes and toggles). */
    public static String postButton(String action, String[][] hiddenFields, String buttonText, String cssClass) {
        StringBuilder sb = new StringBuilder("<form method=\"post\" action=\"").append(action).append("\" class=\"inline-form\">");
        for (String[] pair : hiddenFields) {
            sb.append("<input type=\"hidden\" name=\"").append(pair[0]).append("\" value=\"").append(esc(pair[1])).append("\">");
        }
        return sb.append("<button type=\"submit\" class=\"btn btn-small ").append(cssClass).append("\">")
                .append(esc(buttonText)).append("</button></form>").toString();
    }
}
