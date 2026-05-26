package com.aasa.dto;

import java.util.List;
import java.util.Map;

public class StudyPlanResult {
    private List<RankedTopic> rankedTopics;
    private Map<String, StudyStrategy> studyStrategy;
    private List<ScheduleDay> schedule;
    private Map<String, List<String>> revisionPlan;
    private List<DroppedTopic> droppedTopics;

    public StudyPlanResult() {}

    public StudyPlanResult(List<RankedTopic> rankedTopics, Map<String, StudyStrategy> studyStrategy,
                           List<ScheduleDay> schedule, Map<String, List<String>> revisionPlan,
                           List<DroppedTopic> droppedTopics) {
        this.rankedTopics = rankedTopics;
        this.studyStrategy = studyStrategy;
        this.schedule = schedule;
        this.revisionPlan = revisionPlan;
        this.droppedTopics = droppedTopics;
    }

    public List<RankedTopic> getRankedTopics() { return rankedTopics; }
    public void setRankedTopics(List<RankedTopic> rankedTopics) { this.rankedTopics = rankedTopics; }
    public Map<String, StudyStrategy> getStudyStrategy() { return studyStrategy; }
    public void setStudyStrategy(Map<String, StudyStrategy> studyStrategy) { this.studyStrategy = studyStrategy; }
    public List<ScheduleDay> getSchedule() { return schedule; }
    public void setSchedule(List<ScheduleDay> schedule) { this.schedule = schedule; }
    public Map<String, List<String>> getRevisionPlan() { return revisionPlan; }
    public void setRevisionPlan(Map<String, List<String>> revisionPlan) { this.revisionPlan = revisionPlan; }
    public List<DroppedTopic> getDroppedTopics() { return droppedTopics; }
    public void setDroppedTopics(List<DroppedTopic> droppedTopics) { this.droppedTopics = droppedTopics; }
}
