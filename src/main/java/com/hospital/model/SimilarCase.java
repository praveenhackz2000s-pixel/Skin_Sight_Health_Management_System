package com.hospital.model;

import java.util.List;

/**
 * A previous visit that shares symptoms with the current visit.
 * The score is a RETRIEVAL RANKING (symptom overlap), never the probability of a disease.
 */
public class SimilarCase {

    private final int visitId;
    private final String visitDate;
    private final String department;
    private final String symptoms;
    private final List<String> sharedSymptoms;
    private final double score; // Jaccard similarity, 0.0 - 1.0
    private final String confirmedDiagnosis;       // null unless a doctor confirmed it
    private final String confirmedByDoctorName;    // null unless a doctor confirmed it

    public SimilarCase(int visitId, String visitDate, String department, String symptoms,
                       List<String> sharedSymptoms, double score,
                       String confirmedDiagnosis, String confirmedByDoctorName) {
        this.visitId = visitId;
        this.visitDate = visitDate;
        this.department = department;
        this.symptoms = symptoms;
        this.sharedSymptoms = sharedSymptoms;
        this.score = score;
        this.confirmedDiagnosis = confirmedDiagnosis;
        this.confirmedByDoctorName = confirmedByDoctorName;
    }

    public int getVisitId() {
        return visitId;
    }

    public String getVisitDate() {
        return visitDate;
    }

    public String getDepartment() {
        return department;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public List<String> getSharedSymptoms() {
        return sharedSymptoms;
    }

    public double getScore() {
        return score;
    }

    public int getScorePercent() {
        return (int) Math.round(score * 100);
    }

    public String getConfirmedDiagnosis() {
        return confirmedDiagnosis;
    }

    public String getConfirmedByDoctorName() {
        return confirmedByDoctorName;
    }

    public boolean hasConfirmedDiagnosis() {
        return confirmedDiagnosis != null && !confirmedDiagnosis.isBlank();
    }
}
