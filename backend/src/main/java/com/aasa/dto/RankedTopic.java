package com.aasa.dto;

public class RankedTopic {
    private String name;
    private double priority;
    private String reason;

    public RankedTopic() {}

    public RankedTopic(String name, double priority, String reason) {
        this.name = name;
        this.priority = priority;
        this.reason = reason;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getPriority() { return priority; }
    public void setPriority(double priority) { this.priority = priority; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
