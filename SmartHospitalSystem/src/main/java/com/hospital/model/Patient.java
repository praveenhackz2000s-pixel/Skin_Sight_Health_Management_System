package com.hospital.model;

/** A person registered at the reception counter. */
public class Patient {

    private int patientId;
    private String name;
    private int age;
    private String phone;
    private String createdAt; // already formatted by SQL for display

    public Patient() {
    }

    public Patient(String name, int age, String phone) {
        this.name = name;
        this.age = age;
        this.phone = phone;
    }

    public Patient(int patientId, String name, int age, String phone, String createdAt) {
        this(name, age, phone);
        this.patientId = patientId;
        this.createdAt = createdAt;
    }

    public int getPatientId() {
        return patientId;
    }

    public void setPatientId(int patientId) {
        this.patientId = patientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
