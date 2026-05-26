package com.aasa.dto;

import java.util.List;

public class StudyPlanRequest {
    private List<TopicMetric> topics;
    private TimeConstraints timeConstraints;

    public StudyPlanRequest() {}

    public StudyPlanRequest(List<TopicMetric> topics, TimeConstraints timeConstraints) {
        this.topics = topics;
        this.timeConstraints = timeConstraints;
    }

    public List<TopicMetric> getTopics() { return topics; }
    public void setTopics(List<TopicMetric> topics) { this.topics = topics; }
    public TimeConstraints getTimeConstraints() { return timeConstraints; }
    public void setTimeConstraints(TimeConstraints timeConstraints) { this.timeConstraints = timeConstraints; }

    public static class TopicMetric {
        private String name;
        private double weakness;
        private double importance;
        private double difficulty;
        private double mastery;
        private List<String> prerequisites;

        public TopicMetric() {}

        public TopicMetric(String name, double weakness, double importance, double difficulty,
                            double mastery, List<String> prerequisites) {
            this.name = name;
            this.weakness = weakness;
            this.importance = importance;
            this.difficulty = difficulty;
            this.mastery = mastery;
            this.prerequisites = prerequisites;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getWeakness() { return weakness; }
        public void setWeakness(double weakness) { this.weakness = weakness; }
        public double getImportance() { return importance; }
        public void setImportance(double importance) { this.importance = importance; }
        public double getDifficulty() { return difficulty; }
        public void setDifficulty(double difficulty) { this.difficulty = difficulty; }
        public double getMastery() { return mastery; }
        public void setMastery(double mastery) { this.mastery = mastery; }
        public List<String> getPrerequisites() { return prerequisites; }
        public void setPrerequisites(List<String> prerequisites) { this.prerequisites = prerequisites; }
    }

    public static class TimeConstraints {
        private int totalDays;
        private int hoursPerDay;

        public TimeConstraints() {}
        public TimeConstraints(int totalDays, int hoursPerDay) {
            this.totalDays = totalDays;
            this.hoursPerDay = hoursPerDay;
        }
        public int getTotalDays() { return totalDays; }
        public void setTotalDays(int totalDays) { this.totalDays = totalDays; }
        public int getHoursPerDay() { return hoursPerDay; }
        public void setHoursPerDay(int hoursPerDay) { this.hoursPerDay = hoursPerDay; }
    }
}
