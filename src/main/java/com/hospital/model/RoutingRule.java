package com.hospital.model;

/**
 * One explicit routing rule: "if the symptoms contain this keyword, suggest this specialist category".
 * Rules are written by people (not learned by a model) and must be approved by clinicians.
 */
public class RoutingRule {

    private int ruleId;
    private String department;
    private String symptomKeyword;
    private String suggestedSpecialization;
    private boolean urgent; // a "red flag" keyword: the visit is marked URGENT for immediate clinical review
    private boolean active;

    public RoutingRule() {
    }

    public RoutingRule(int ruleId, String department, String symptomKeyword,
                       String suggestedSpecialization, boolean urgent, boolean active) {
        this.ruleId = ruleId;
        this.department = department;
        this.symptomKeyword = symptomKeyword;
        this.suggestedSpecialization = suggestedSpecialization;
        this.urgent = urgent;
        this.active = active;
    }

    public int getRuleId() {
        return ruleId;
    }

    public void setRuleId(int ruleId) {
        this.ruleId = ruleId;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getSymptomKeyword() {
        return symptomKeyword;
    }

    public void setSymptomKeyword(String symptomKeyword) {
        this.symptomKeyword = symptomKeyword;
    }

    public String getSuggestedSpecialization() {
        return suggestedSpecialization;
    }

    public void setSuggestedSpecialization(String suggestedSpecialization) {
        this.suggestedSpecialization = suggestedSpecialization;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public void setUrgent(boolean urgent) {
        this.urgent = urgent;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
