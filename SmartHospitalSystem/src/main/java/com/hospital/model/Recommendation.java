package com.hospital.model;

/**
 * A specialist-category SUGGESTION produced by the routing rules.
 * It is decision support only: it is not a diagnosis and must be reviewed by a doctor.
 */
public class Recommendation {

    /** Waiting for a doctor to look at it. */
    public static final String PENDING = "PENDING";
    /** A doctor agreed that the suggested category is suitable. */
    public static final String ACCEPTED = "ACCEPTED";
    /** A doctor disagreed; this suggestion is ignored when choosing eligible doctors. */
    public static final String REJECTED = "REJECTED";

    private int recommendationId;
    private int visitId;
    private String suggestedSpecialization;
    private String reason;
    private String reviewStatus = PENDING;
    private String reviewedByDoctorName;
    private String createdAt;
    private boolean fallback; // true when no rule matched and the general category was suggested

    public Recommendation() {
    }

    public Recommendation(String suggestedSpecialization, String reason, boolean fallback) {
        this.suggestedSpecialization = suggestedSpecialization;
        this.reason = reason;
        this.fallback = fallback;
    }

    public static boolean isValidDecision(String decision) {
        return ACCEPTED.equals(decision) || REJECTED.equals(decision);
    }

    public int getRecommendationId() {
        return recommendationId;
    }

    public void setRecommendationId(int recommendationId) {
        this.recommendationId = recommendationId;
    }

    public int getVisitId() {
        return visitId;
    }

    public void setVisitId(int visitId) {
        this.visitId = visitId;
    }

    public String getSuggestedSpecialization() {
        return suggestedSpecialization;
    }

    public void setSuggestedSpecialization(String suggestedSpecialization) {
        this.suggestedSpecialization = suggestedSpecialization;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewedByDoctorName() {
        return reviewedByDoctorName;
    }

    public void setReviewedByDoctorName(String reviewedByDoctorName) {
        this.reviewedByDoctorName = reviewedByDoctorName;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isFallback() {
        return fallback;
    }

    public void setFallback(boolean fallback) {
        this.fallback = fallback;
    }
}
