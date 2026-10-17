package com.hospital.model;

import java.time.LocalDate;

/** Represents one eye or skin visit. Member 2 can extend this model. */
public class Visit {
    private int visitId;
    private int patientId;
    private String department;
    private String symptoms;
    private LocalDate visitDate;

    public Visit() { }

    public Visit(int patientId, String department, String symptoms, LocalDate visitDate) {
        this.patientId = patientId;
        this.department = department;
        this.symptoms = symptoms;
        this.visitDate = visitDate;
    }

    public int getVisitId() { return visitId; }
    public void setVisitId(int visitId) { this.visitId = visitId; }
    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }
}
