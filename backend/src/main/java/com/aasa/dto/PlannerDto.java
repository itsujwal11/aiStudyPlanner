package com.aasa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlannerDto {
    // Today's executable tasks
    private List<TodoTask> todayTasks;

    // Weak topics analysis
    private List<WeakTopicAnalysis> weakTopics;

    // All topics ranked by priority
    private List<WeakTopicAnalysis> priorityTopics;

    // Study roadmap (days from now)
    private List<StudyRoadmapItem> studyRoadmap;

    // Revision schedule
    private List<RevisionScheduleItem> revisionSchedule;

    // Short data-driven recommendations
    private List<String> recommendations;

    // Upcoming test/practice days
    private List<Integer> practiceDays;

    // Summary stats
    private int totalTopics;
    private int weakTopicsCount;
    private double averageMastery;
    private int daysUntilExam;
    private int totalTasksToday;
    private int totalDurationMinutesToday;
}