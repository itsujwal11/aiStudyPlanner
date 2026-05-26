package com.aasa.dto;

public class StudyPlanResponse {
    private String plan;

    public StudyPlanResponse() {}

    public StudyPlanResponse(String plan) {
        this.plan = plan;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }
}
