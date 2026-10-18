package com.hospital.model;

/** One hospital visit (past or current). Stores symptoms, findings and the doctor-confirmed diagnosis. */
public class Visit {

    public static final String DEPT_EYE = "EYE";
    public static final String DEPT_SKIN = "SKIN";

    public static final String PRIORITY_ROUTINE = "ROUTINE";
    public static final String PRIORITY_URGENT = "URGENT";

    public static final String STATUS_PENDING = "PENDING_ASSIGNMENT";
    public static final String STATUS_ASSIGNED = "ASSIGNED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private int visitId;
    private int patientId;
    private String patientName;      // filled by joins, for display
    private String department;
    private String symptoms;         // normalized, comma separated
    private String priority = PRIORITY_ROUTINE;
    private String clinicalFindings;
    private String testResults;
    private String confirmedDiagnosis;
    private int confirmedByDoctorId; // 0 = nobody has confirmed a diagnosis
    private String confirmedByDoctorName;
    private String confirmedAt;
    private String visitDate;
    private String status = STATUS_PENDING;

    public static boolean isValidDepartment(String department) {
        return DEPT_EYE.equals(department) || DEPT_SKIN.equals(department);
    }

    public static String departmentLabel(String department) {
        return DEPT_EYE.equals(department) ? "Eye" : DEPT_SKIN.equals(department) ? "Skin" : String.valueOf(department);
    }

    /** A diagnosis only counts when an authorized doctor entered it. Rule suggestions never count. */
    public boolean isDiagnosisConfirmed() {
        return confirmedDiagnosis != null && !confirmedDiagnosis.isBlank() && confirmedByDoctorId > 0;
    }

    public boolean isUrgent() {
        return PRIORITY_URGENT.equals(priority);
    }

    public boolean isOpen() {
        return !STATUS_COMPLETED.equals(status) && !STATUS_CANCELLED.equals(status);
    }

    public int getVisitId() {
        return visitId;
    }

    public void setVisitId(int visitId) {
        this.visitId = visitId;
    }

    public int getPatientId() {
        return patientId;
    }

    public void setPatientId(int patientId) {
        this.patientId = patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getClinicalFindings() {
        return clinicalFindings;
    }

    public void setClinicalFindings(String clinicalFindings) {
        this.clinicalFindings = clinicalFindings;
    }

    public String getTestResults() {
        return testResults;
    }

    public void setTestResults(String testResults) {
        this.testResults = testResults;
    }

    public String getConfirmedDiagnosis() {
        return confirmedDiagnosis;
    }

    public void setConfirmedDiagnosis(String confirmedDiagnosis) {
        this.confirmedDiagnosis = confirmedDiagnosis;
    }

    public int getConfirmedByDoctorId() {
        return confirmedByDoctorId;
    }

    public void setConfirmedByDoctorId(int confirmedByDoctorId) {
        this.confirmedByDoctorId = confirmedByDoctorId;
    }

    public String getConfirmedByDoctorName() {
        return confirmedByDoctorName;
    }

    public void setConfirmedByDoctorName(String confirmedByDoctorName) {
        this.confirmedByDoctorName = confirmedByDoctorName;
    }

    public String getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(String confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(String visitDate) {
        this.visitDate = visitDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
