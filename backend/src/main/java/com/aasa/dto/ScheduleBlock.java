package com.aasa.dto;

public class ScheduleBlock {
    private String time;
    private String topic;
    private String activity;

    public ScheduleBlock() {}

    public ScheduleBlock(String time, String topic, String activity) {
        this.time = time;
        this.topic = topic;
        this.activity = activity;
    }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getActivity() { return activity; }
    public void setActivity(String activity) { this.activity = activity; }
}
