package com.aasa.service;

import com.aasa.dto.StudyPlanRequest;
import com.aasa.dto.StudyPlanRequest.TopicMetric;
import com.aasa.dto.StudyPlanResult;
import com.aasa.dto.RankedTopic;
import com.aasa.dto.StudyStrategy;
import com.aasa.dto.ScheduleDay;
import com.aasa.dto.ScheduleBlock;
import com.aasa.dto.DroppedTopic;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudyPlanService {

    private static final double EPSILON = 0.1;

    public StudyPlanResult generatePlan(StudyPlanRequest request) {
        List<TopicMetric> topics = request.getTopics();
        if (topics == null || topics.isEmpty()) {
            return new StudyPlanResult();
        }

        List<PriorityTopic> priorityList = topics.stream()
                .map(t -> new PriorityTopic(t, computePriority(t)))
                .sorted(Comparator.comparingDouble(PriorityTopic::getPriority).reversed())
                .collect(Collectors.toList());

        List<RankedTopic> rankedTopics = priorityList.stream()
                .map(pt -> new RankedTopic(pt.getName(), pt.getPriority(),
                        String.format("Weakness %.2f, Importance %.2f, Difficulty %.2f, Mastery %.2f",
                                pt.getMetric().getWeakness(), pt.getMetric().getImportance(),
                                pt.getMetric().getDifficulty(), pt.getMetric().getMastery())))
                .collect(Collectors.toList());

        int topN = Math.min(8, priorityList.size());
        int totalMinutes = request.getTimeConstraints().getTotalDays() * request.getTimeConstraints().getHoursPerDay() * 60;
        int minutesPerTopic = totalMinutes / (topN == 0 ? 1 : topN);
        Map<String, StudyStrategy> studyStrategyMap = new HashMap<>();
        for (int i = 0; i < topN; i++) {
            PriorityTopic pt = priorityList.get(i);
            TopicMetric tm = pt.getMetric();
            int practiceQs = (int) Math.ceil(tm.getDifficulty() * 10);
            int revisionCount = (int) Math.ceil(tm.getDifficulty() * 2);
            StudyStrategy ss = new StudyStrategy(minutesPerTopic, minutesPerTopic / 45,
                    Arrays.asList("concept", "activeRecall", "practice"), practiceQs, revisionCount);
            studyStrategyMap.put(tm.getName(), ss);
        }

        List<ScheduleDay> schedule = new ArrayList<>();
        int day = 1;
        int topicsIdx = 0;
        Map<String, Integer> revSchedule = new HashMap<>();
        int maxDays = request.getTimeConstraints().getTotalDays();
        while (day <= maxDays && topicsIdx < priorityList.size()) {
            List<ScheduleBlock> blocks = new ArrayList<>();
            int newBlocks = 0;
            while (newBlocks < 2 && topicsIdx < priorityList.size()) {
                PriorityTopic pt = priorityList.get(topicsIdx);
                blocks.add(new ScheduleBlock("09:00-10:30", pt.getName(), "learning"));
                revSchedule.put(pt.getName(), day + 1);
                topicsIdx++;
                newBlocks++;
            }
            int revAdded = 0;
            final int currentDay = day;
            List<String> due = revSchedule.entrySet().stream()
                    .filter(e -> e.getValue() == currentDay)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            for (String revTopic : due) {
                if (revAdded >= 2) break;
                blocks.add(new ScheduleBlock("14:00-14:30", revTopic, "revision"));
                int next = revSchedule.get(revTopic) + (revAdded == 0 ? 1 : 2);
                if (next <= maxDays) {
                    revSchedule.put(revTopic, next);
                } else {
                    revSchedule.remove(revTopic);
                }
                revAdded++;
            }
            schedule.add(new ScheduleDay(day, blocks));
            day++;
        }

        Map<String, List<String>> revisionPlan = new HashMap<>();
        for (RankedTopic rt : rankedTopics) {
            String tName = rt.getName();
            List<String> revDays = new ArrayList<>();
            revDays.add("Day " + (revSchedule.containsKey(tName) ? revSchedule.get(tName) - 1 : ""));
            revDays.add("Day " + (revSchedule.containsKey(tName) ? revSchedule.get(tName) : ""));
            revisionPlan.put(tName, revDays);
        }

        List<DroppedTopic> dropped = new ArrayList<>();
        if (priorityList.size() > topN) {
            for (int i = topN; i < priorityList.size(); i++) {
                PriorityTopic pt = priorityList.get(i);
                dropped.add(new DroppedTopic(pt.getName(), "Low priority due to time constraints"));
            }
        }

        StudyPlanResult result = new StudyPlanResult();
        result.setRankedTopics(rankedTopics);
        result.setStudyStrategy(studyStrategyMap);
        result.setSchedule(schedule);
        result.setRevisionPlan(revisionPlan);
        result.setDroppedTopics(dropped);
        return result;
    }

    private double computePriority(TopicMetric t) {
        return (t.getWeakness() * t.getImportance() * t.getDifficulty()) / (t.getMastery() + EPSILON);
    }

    private static class PriorityTopic {
        private final TopicMetric metric;
        private final double priority;
        private final String name;

        PriorityTopic(TopicMetric metric, double priority) {
            this.metric = metric;
            this.priority = priority;
            this.name = metric.getName();
        }

        public TopicMetric getMetric() { return metric; }
        public double getPriority() { return priority; }
        public String getName() { return name; }
    }
}