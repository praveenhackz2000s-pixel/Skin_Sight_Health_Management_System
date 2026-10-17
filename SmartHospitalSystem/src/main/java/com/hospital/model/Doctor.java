package com.hospital.model;

/** Represents a doctor in the hospital directory. */
public class Doctor {
    private int doctorId;
    private String name;
    private String department;
    private String specialization;
    private boolean active = true;

    public Doctor() { }

    public Doctor(int doctorId, String name, String department, String specialization) {
        this.doctorId = doctorId;
        this.name = name;
        this.department = department;
        this.specialization = specialization;
    }

    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
