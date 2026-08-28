package com.aasa.service;

import com.aasa.dto.StudyRoadmapItem;
import com.aasa.dto.TodoTask;
import com.aasa.dto.WeakTopicAnalysis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks two planner properties the UI depends on:
 *
 *  1. a topic never appears twice in today's list — hard topics used to emit two
 *     identical LEARN rows, which rendered as the same task repeated;
 *  2. day 1 of the roadmap is exactly today's task list — these were two
 *     independent generators, so the roadmap's first day disagreed with Today
 *     about the same date.
 *
 * Both methods are pure functions of the analyses, so no repositories are needed.
 */
class PlannerTaskGenerationTest {

    private final PlannerService plannerService = new PlannerService();

    /** A weak, hard topic — the case that used to produce a duplicate row. */
    private WeakTopicAnalysis weakHardTopic(long id, String title) {
        return WeakTopicAnalysis.builder()
                .topicId(id).topicTitle(title).description("")
                .masteryLevel(10.0)        // < 50 -> first pass
                .weaknessScore(0.9)        // > 0.5 -> first pass
                .complexityScore(0.8)      // >= 0.7 -> "hard", previously duplicated
                .importanceScore(0.9)
                .priorityScore(0.9)
                .daysUntilExam(30)
                .whyImportant("").recommendedDuration("")
                .totalAttempts(4).correctAttempts(0)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<TodoTask> todayTasks(List<WeakTopicAnalysis> analyses) {
        return (List<TodoTask>) ReflectionTestUtils.invokeMethod(
                plannerService, "generateTodayTasks", analyses, new HashMap<>(), null);
    }

    @SuppressWarnings("unchecked")
    private List<StudyRoadmapItem> roadmap(List<WeakTopicAnalysis> analyses,
                                           int daysUntilExam, List<TodoTask> today) {
        return (List<StudyRoadmapItem>) ReflectionTestUtils.invokeMethod(
                plannerService, "generateStudyRoadmap", analyses, daysUntilExam, today);
    }

    @Test
    @DisplayName("a hard topic produces one task, not two identical rows")
    void hardTopicIsNotDuplicated() {
        List<TodoTask> tasks = todayTasks(List.of(weakHardTopic(1L, "Coca-Cola Case Study")));

        assertEquals(1, tasks.size(),
                "hard topics used to emit a second identical LEARN block");
        assertEquals("Coca-Cola Case Study", tasks.get(0).getTopicTitle());
    }

    @Test
    @DisplayName("no topic appears twice across the whole day plan")
    void noDuplicateTopicsInPlan() {
        List<WeakTopicAnalysis> analyses = new ArrayList<>();
        for (long i = 1; i <= 6; i++) {
            analyses.add(weakHardTopic(i, "Topic " + i));
        }

        List<TodoTask> tasks = todayTasks(analyses);

        List<Long> topicIds = tasks.stream().map(TodoTask::getTopicId).toList();
        assertEquals(topicIds.size(), Set.copyOf(topicIds).size(),
                "duplicate topic in today's plan: " + topicIds);
    }

    @Test
    @DisplayName("every task carries a stable key that is unique within the day")
    void tasksCarryUniqueStableKeys() {
        List<WeakTopicAnalysis> analyses = List.of(
                weakHardTopic(1L, "Alpha"), weakHardTopic(2L, "Beta"));

        List<TodoTask> tasks = todayTasks(analyses);

        List<String> keys = tasks.stream().map(TodoTask::getTaskKey).toList();
        assertTrue(keys.stream().noneMatch(k -> k == null || k.isBlank()), "missing task key");
        assertEquals(keys.size(), Set.copyOf(keys).size(), "duplicate task keys: " + keys);
        // Key format must match PlannerTaskCompletionService.taskKey so a saved
        // tick can be matched back to the task it belongs to.
        assertEquals("1:LEARN:0", PlannerService.taskKeyFor(1L, "LEARN", 0));
    }

    @Test
    @DisplayName("day 1 of the roadmap is exactly today's task list")
    void roadmapDayOneMatchesToday() {
        List<WeakTopicAnalysis> analyses = List.of(
                weakHardTopic(1L, "Coca-Cola Case Study"),
                weakHardTopic(2L, "SkyHigh Airlines Case Study"));

        List<TodoTask> today = todayTasks(analyses);
        List<StudyRoadmapItem> plan = roadmap(analyses, 10, today);

        List<StudyRoadmapItem> dayOne = plan.stream()
                .filter(r -> r.getDay() == 1)
                .collect(Collectors.toList());

        assertEquals(today.size(), dayOne.size(), "day 1 should mirror today's tasks");
        for (int i = 0; i < today.size(); i++) {
            assertEquals(today.get(i).getTopicTitle(), dayOne.get(i).getTopicTitle());
            assertEquals(today.get(i).getActivityType(), dayOne.get(i).getActivityType());
            assertEquals(today.get(i).getEstimatedDurationMinutes(),
                    dayOne.get(i).getEstimatedDurationMinutes(),
                    "duration must agree between the two views");
        }
        assertEquals(LocalDate.now(), dayOne.get(0).getScheduledDate());
    }

    @Test
    @DisplayName("day 1 does not repeat a topic either")
    void roadmapDayOneHasNoDuplicates() {
        List<WeakTopicAnalysis> analyses = List.of(weakHardTopic(1L, "Coca-Cola Case Study"));

        List<StudyRoadmapItem> plan = roadmap(analyses, 10, todayTasks(analyses));
        List<String> dayOneTitles = plan.stream()
                .filter(r -> r.getDay() == 1)
                .map(StudyRoadmapItem::getTopicTitle)
                .toList();

        assertEquals(dayOneTitles.size(), Set.copyOf(dayOneTitles).size(),
                "duplicate topic on roadmap day 1: " + dayOneTitles);
        assertFalse(dayOneTitles.isEmpty());
    }
}
