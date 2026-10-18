package com.hospital.model;

/** A doctor together with appointment counts that are calculated from the appointments table. */
public class DoctorWorkload {

    private final Doctor doctor;
    private final int waiting;
    private final int inProgress;
    private final int completed;

    public DoctorWorkload(Doctor doctor, int waiting, int inProgress, int completed) {
        this.doctor = doctor;
        this.waiting = waiting;
        this.inProgress = inProgress;
        this.completed = completed;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public int getWaiting() {
        return waiting;
    }

    public int getInProgress() {
        return inProgress;
    }

    public int getCompleted() {
        return completed;
    }

    /** Active patients = waiting + in progress. This number drives routine assignment. */
    public int getActiveCount() {
        return waiting + inProgress;
    }
}
