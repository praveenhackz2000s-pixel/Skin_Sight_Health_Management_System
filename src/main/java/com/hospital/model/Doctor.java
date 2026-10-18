package com.hospital.model;

/** A doctor of the EYE or SKIN department. */
public class Doctor {

    /** Doctors whose specialization starts with this word may treat any case of their department. */
    public static final String GENERAL_PREFIX = "General";

    private int doctorId;
    private String name;
    private String department;
    private String specialization;
    private boolean available; // currently accepting patients
    private boolean active;    // still working at the hospital

    public Doctor() {
    }

    public Doctor(int doctorId, String name, String department, String specialization,
                  boolean available, boolean active) {
        this.doctorId = doctorId;
        this.name = name;
        this.department = department;
        this.specialization = specialization;
        this.available = available;
        this.active = active;
    }

    /** True for the "general" category (General Ophthalmology, General Dermatology). */
    public boolean isGeneral() {
        return specialization != null && specialization.startsWith(GENERAL_PREFIX);
    }

    public int getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(int doctorId) {
        this.doctorId = doctorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
