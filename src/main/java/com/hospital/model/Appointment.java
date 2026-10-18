package com.hospital.model;

/** A place in a doctor's queue for one visit. */
public class Appointment {

    public static final String WAITING = "WAITING";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    private int appointmentId;
    private int visitId;
    private int doctorId;
    private int queueNumber;
    private String appointmentDate; // formatted by SQL
    private String status = WAITING;

    // Filled by joins, for display only
    private String doctorName;
    private String doctorSpecialization;
    private int patientId;
    private String patientName;
    private String department;
    private String symptoms;
    private String priority;

    public static boolean isValidStatus(String status) {
        return WAITING.equals(status) || IN_PROGRESS.equals(status)
                || COMPLETED.equals(status) || CANCELLED.equals(status);
    }

    /** Active = the patient is still in the doctor's queue. */
    public boolean isActive() {
        return WAITING.equals(status) || IN_PROGRESS.equals(status);
    }

    public boolean isUrgent() {
        return Visit.PRIORITY_URGENT.equals(priority);
    }

    /** Queue number as shown to people, for example Q-003. */
    public String getQueueLabel() {
        return String.format("Q-%03d", queueNumber);
    }

    public int getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(int appointmentId) {
        this.appointmentId = appointmentId;
    }

    public int getVisitId() {
        return visitId;
    }

    public void setVisitId(int visitId) {
        this.visitId = visitId;
    }

    public int getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(int doctorId) {
        this.doctorId = doctorId;
    }

    public int getQueueNumber() {
        return queueNumber;
    }

    public void setQueueNumber(int queueNumber) {
        this.queueNumber = queueNumber;
    }

    public String getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(String appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public String getDoctorSpecialization() {
        return doctorSpecialization;
    }

    public void setDoctorSpecialization(String doctorSpecialization) {
        this.doctorSpecialization = doctorSpecialization;
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
}
