package com.aasa.service;

import com.aasa.dto.*;
import com.aasa.entity.*;
import com.aasa.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PlannerService {

    private static final Logger logger = Logger.getLogger(PlannerService.class.getName());

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private StudyProgressRepository studyProgressRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private PdfDocumentRepository pdfDocumentRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private WeaknessEngineService weaknessEngineService;

    @Autowired
    private ScoringEngineService scoringEngineService;

    public PlannerDto generatePlanner(User user) {
        logger.info("Generating planner for user ID: " + user.getId());

        List<Topic> topics = topicRepository.findByUserIdOrderByPriority(user.getId());
        List<PdfDocument> pdfs = pdfDocumentRepository.findByUserId(user.getId());

        if (topics.isEmpty()) {
            return createEmptyPlanner();
        }

        int daysUntilExam = calculateDaysUntilExam(pdfs);

        // 1. Build weak topic analysis for ALL topics
        List<WeakTopicAnalysis> allAnalyses = new ArrayList<>();
        Map<Long, StudyProgress> progressMap = new HashMap<>();

        for (Topic topic : topics) {
            StudyProgress progress = studyProgressRepository.findByUserIdAndTopicId(user.getId(), topic.getId())
                    .orElse(null);

            if (progress != null) {
                progressMap.put(topic.getId(), progress);
            }

            WeakTopicAnalysis analysis = buildWeakTopicAnalysis(topic, progress, daysUntilExam);
            allAnalyses.add(analysis);
        }

        // 2. Sort by priority descending
        allAnalyses.sort((a, b) -> Double.compare(b.getPriorityScore(), a.getPriorityScore()));

        // 3. Identify weak topics (mastery < 70 or weakness > 0.5)
        List<WeakTopicAnalysis> weakTopics = allAnalyses.stream()
                .filter(t -> t.getMasteryLevel() < 70.0 || t.getWeaknessScore() > 0.5)
                .collect(Collectors.toList());

        // 4. Generate today's todo tasks (max 5, focused)
        List<TodoTask> todayTasks = generateTodayTasks(allAnalyses, progressMap, user);

        // 5. Generate study roadmap (next 7-14 days)
        List<StudyRoadmapItem> roadmap = generateStudyRoadmap(allAnalyses, daysUntilExam);

        // 6. Generate revision schedule
        List<RevisionScheduleItem> revisionSchedule = generateRevisionSchedule(allAnalyses, progressMap);

        // 7. Generate data-driven recommendations
        List<String> recommendations = generateRecommendations(allAnalyses, weakTopics, daysUntilExam);

        // 8. Practice/test days
        List<Integer> practiceDays = identifyPracticeDays(roadmap);

        // 9. Summary stats
        double avgMastery = allAnalyses.stream()
                .mapToDouble(WeakTopicAnalysis::getMasteryLevel)
                .average()
                .orElse(0.0);

        int totalDurationToday = todayTasks.stream()
                .mapToInt(TodoTask::getEstimatedDurationMinutes)
                .sum();

        return PlannerDto.builder()
                .todayTasks(todayTasks)
                .weakTopics(weakTopics)
                .priorityTopics(allAnalyses)
                .studyRoadmap(roadmap)
                .revisionSchedule(revisionSchedule)
                .recommendations(recommendations)
                .practiceDays(practiceDays)
                .totalTopics(topics.size())
                .weakTopicsCount(weakTopics.size())
                .averageMastery(Math.round(avgMastery * 10.0) / 10.0)
                .daysUntilExam(daysUntilExam)
                .totalTasksToday(todayTasks.size())
                .totalDurationMinutesToday(totalDurationToday)
                .build();
    }

    private WeakTopicAnalysis buildWeakTopicAnalysis(Topic topic, StudyProgress progress, int daysUntilExam) {
        double weaknessScore;
        double masteryLevel;
        int totalAttempts = 0;
        int correctAttempts = 0;

        if (progress != null) {
            totalAttempts = progress.getTotalAttempts() != null ? progress.getTotalAttempts() : 0;
            correctAttempts = progress.getCorrectAttempts() != null ? progress.getCorrectAttempts() : 0;
            masteryLevel = (progress.getMasteryLevel() != null ? progress.getMasteryLevel() : 0.0) * 100.0;
            weaknessScore = topic.getWeaknessScore() != null
                    ? topic.getWeaknessScore()
                    : weaknessEngineService.getWeaknessScore(progress.getWeaknessLevel());
        } else {
            weaknessScore = 1.0; // NOT_ATTEMPTED
            masteryLevel = 0.0;
        }

        double importance = topic.getImportanceScore() != null ? topic.getImportanceScore() : 0.5;
        double complexity = topic.getComplexityScore() != null ? topic.getComplexityScore() : 0.5;
        double priorityScore = scoringEngineService.calculatePriorityScore(
                complexity, importance, weaknessScore, daysUntilExam);

        // Priority = (Weakness × Importance × Difficulty) / (Mastery + 0.1)

        String whyImportant = describeImportance(importance, complexity, weaknessScore);
        String recommendedDuration = estimateDuration(complexity, weaknessScore);

        return WeakTopicAnalysis.builder()
                .topicId(topic.getId())
                .topicTitle(topic.getTitle())
                .description(topic.getDescription() != null ? topic.getDescription() : "")
                .weaknessScore(Math.round(weaknessScore * 100.0) / 100.0)
                .importanceScore(Math.round(importance * 100.0) / 100.0)
                .complexityScore(Math.round(complexity * 100.0) / 100.0)
                .masteryLevel(Math.round(masteryLevel * 10.0) / 10.0)
                .priorityScore(Math.round(priorityScore * 100.0) / 100.0)
                .daysUntilExam(daysUntilExam)
                .whyImportant(whyImportant)
                .recommendedDuration(recommendedDuration)
                .totalAttempts(totalAttempts)
                .correctAttempts(correctAttempts)
                .build();
    }

    private String describeImportance(double importance, double complexity, double weakness) {
        StringBuilder sb = new StringBuilder();
        if (importance >= 0.8) {
            sb.append("Core concept with high exam weightage. ");
        } else if (importance >= 0.6) {
            sb.append("Important supporting topic. ");
        } else {
            sb.append("Supplementary topic. ");
        }

        if (weakness >= 0.7) {
            sb.append("Requires significant improvement.");
        } else if (weakness >= 0.4) {
            sb.append("Needs further practice.");
        } else {
            sb.append("Good understanding achieved.");
        }
        return sb.toString();
    }

    private String estimateDuration(double complexity, double weakness) {
        int baseMinutes = 30;
        int complexityExtra = (int) (complexity * 60);
        int weaknessExtra = (int) (weakness * 45);
        int total = baseMinutes + complexityExtra + weaknessExtra;

        // Capped at 120 min for hard, split into sessions
        if (total > 90) {
            return total + " mins (split into 2 sessions)";
        }
        return total + " mins";
    }

    private List<TodoTask> generateTodayTasks(List<WeakTopicAnalysis> analyses,
                                               Map<Long, StudyProgress> progressMap,
                                               User user) {
        List<TodoTask> tasks = new ArrayList<>();
        Set<Long> usedTopicIds = new HashSet<>();

        // First pass: weak topics that need learning (mastery < 50)
        for (WeakTopicAnalysis analysis : analyses) {
            if (tasks.size() >= 5) break;
            if (usedTopicIds.contains(analysis.getTopicId())) continue;

            if (analysis.getMasteryLevel() < 50.0 && analysis.getWeaknessScore() > 0.5) {
                // Hard topics split into smaller sessions
                boolean isHard = analysis.getComplexityScore() >= 0.7;
                int duration = isHard ? 45 : 60;

                tasks.add(TodoTask.builder()
                        .topicTitle(analysis.getTopicTitle())
                        .activityType("LEARN")
                        .estimatedDurationMinutes(duration)
                        .complexityLevel(complexityLabel(analysis.getComplexityScore()))
                        .priorityLevel("HIGH")
                        .completed(false)
                        .topicId(analysis.getTopicId())
                        .weaknessScore(analysis.getWeaknessScore())
                        .build());
                usedTopicIds.add(analysis.getTopicId());

                // If hard, add second session
                if (isHard && tasks.size() < 5) {
                    tasks.add(TodoTask.builder()
                            .topicTitle(analysis.getTopicTitle())
                            .activityType("LEARN")
                            .estimatedDurationMinutes(45)
                            .complexityLevel(complexityLabel(analysis.getComplexityScore()))
                            .priorityLevel("HIGH")
                            .completed(false)
                            .topicId(analysis.getTopicId())
                            .weaknessScore(analysis.getWeaknessScore())
                            .build());
                }
            }
        }

        // Second pass: topics needing revision (mastery 50-70 or medium weakness)
        for (WeakTopicAnalysis analysis : analyses) {
            if (tasks.size() >= 5) break;
            if (usedTopicIds.contains(analysis.getTopicId())) continue;

            if (analysis.getMasteryLevel() >= 50.0 && analysis.getMasteryLevel() < 75.0) {
                tasks.add(TodoTask.builder()
                        .topicTitle(analysis.getTopicTitle())
                        .activityType("REVISION")
                        .estimatedDurationMinutes(30)
                        .complexityLevel(complexityLabel(analysis.getComplexityScore()))
                        .priorityLevel("MEDIUM")
                        .completed(false)
                        .topicId(analysis.getTopicId())
                        .weaknessScore(analysis.getWeaknessScore())
                        .build());
                usedTopicIds.add(analysis.getTopicId());
            }
        }

        // Third pass: high importance topics always scheduled
        for (WeakTopicAnalysis analysis : analyses) {
            if (tasks.size() >= 5) break;
            if (usedTopicIds.contains(analysis.getTopicId())) continue;

            if (analysis.getImportanceScore() >= 0.8 && analysis.getMasteryLevel() < 90.0) {
                tasks.add(TodoTask.builder()
                        .topicTitle(analysis.getTopicTitle())
                        .activityType("PRACTICE")
                        .estimatedDurationMinutes(25)
                        .complexityLevel(complexityLabel(analysis.getComplexityScore()))
                        .priorityLevel("HIGH")
                        .completed(false)
                        .topicId(analysis.getTopicId())
                        .weaknessScore(analysis.getWeaknessScore())
                        .build());
                usedTopicIds.add(analysis.getTopicId());
            }
        }

        // Fourth pass: mastered topics become revision-only
        for (WeakTopicAnalysis analysis : analyses) {
            if (tasks.size() >= 5) break;
            if (usedTopicIds.contains(analysis.getTopicId())) continue;

            if (analysis.getMasteryLevel() >= 90.0) {
                tasks.add(TodoTask.builder()
                        .topicTitle(analysis.getTopicTitle())
                        .activityType("REVISION")
                        .estimatedDurationMinutes(15)
                        .complexityLevel(complexityLabel(analysis.getComplexityScore()))
                        .priorityLevel("LOW")
                        .completed(false)
                        .topicId(analysis.getTopicId())
                        .weaknessScore(analysis.getWeaknessScore())
                        .build());
                usedTopicIds.add(analysis.getTopicId());
            }
        }

        return tasks;
    }

    private List<StudyRoadmapItem> generateStudyRoadmap(List<WeakTopicAnalysis> analyses, int daysUntilExam) {
        List<StudyRoadmapItem> roadmap = new ArrayList<>();
        int planDays = Math.min(daysUntilExam, 14);
        if (planDays <= 0) planDays = 7;

        LocalDate today = LocalDate.now();
        Set<String> scheduledToday = new HashSet<>();

        // Distribute topics across days, weak topics appear more frequently
        for (int day = 0; day < planDays; day++) {
            int tasksToday = 0;
            Set<String> dayTopics = new HashSet<>();
            LocalDate dayDate = today.plusDays(day);

            for (WeakTopicAnalysis analysis : analyses) {
                if (tasksToday >= 4) break;

                String key = analysis.getTopicTitle() + "_" + day;
                if (scheduledToday.contains(key)) continue;

                boolean isWeak = analysis.getWeaknessScore() > 0.5;
                boolean isHighImportance = analysis.getImportanceScore() >= 0.7;

                // Weak topics appear more frequently (every 1-2 days)
                if (isWeak && day % 2 == 0) {
                    roadmap.add(StudyRoadmapItem.builder()
                            .topicTitle(analysis.getTopicTitle())
                            .activityType("LEARN")
                            .day(day + 1)
                            .scheduledDate(dayDate)
                            .estimatedDurationMinutes(estimateDurationMinutes(analysis))
                            .complexityLevel(complexityLabel(analysis.getComplexityScore()))
                            .priorityLevel("HIGH")
                            .completed(false)
                            .build());
                    scheduledToday.add(key);
                    dayTopics.add(analysis.getTopicTitle());
                    tasksToday++;
                }
                // High importance topics always scheduled
                else if (isHighImportance && !dayTopics.contains(analysis.getTopicTitle())) {
                    String activity = day % 3 == 0 ? "PRACTICE" : "REVISION";
                    roadmap.add(StudyRoadmapItem.builder()
                            .topicTitle(analysis.getTopicTitle())
                            .activityType(activity)
                            .day(day + 1)
                            .scheduledDate(dayDate)
                            .estimatedDurationMinutes(estimateDurationMinutes(analysis))
                            .complexityLevel(complexityLabel(analysis.getComplexityScore()))
                            .priorityLevel("HIGH")
                            .completed(false)
                            .build());
                    scheduledToday.add(key);
                    dayTopics.add(analysis.getTopicTitle());
                    tasksToday++;
                }
            }

            // Fill remaining slots with any topics not yet scheduled today
            for (WeakTopicAnalysis analysis : analyses) {
                if (tasksToday >= 5) break;
                if (dayTopics.contains(analysis.getTopicTitle())) continue;

                roadmap.add(StudyRoadmapItem.builder()
                        .topicTitle(analysis.getTopicTitle())
                        .activityType("REVISION")
                        .day(day + 1)
                        .scheduledDate(dayDate)
                        .estimatedDurationMinutes(20)
                        .complexityLevel(complexityLabel(analysis.getComplexityScore()))
                        .priorityLevel("MEDIUM")
                        .completed(false)
                        .build());
                dayTopics.add(analysis.getTopicTitle());
                tasksToday++;
            }
        }

        return roadmap;
    }

    private int estimateDurationMinutes(WeakTopicAnalysis analysis) {
        int base = 25;
        int complexityExtra = (int) (analysis.getComplexityScore() * 35);
        int weaknessExtra = (int) (analysis.getWeaknessScore() * 30);
        return Math.min(base + complexityExtra + weaknessExtra, 90);
    }

    private List<RevisionScheduleItem> generateRevisionSchedule(List<WeakTopicAnalysis> analyses,
                                                                  Map<Long, StudyProgress> progressMap) {
        List<RevisionScheduleItem> schedule = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (WeakTopicAnalysis analysis : analyses) {
            StudyProgress progress = progressMap.get(analysis.getTopicId());

            // Calculate days since last practice
            int daysSinceLastPractice = 0;
            if (progress != null && progress.getTotalAttempts() != null && progress.getTotalAttempts() > 0) {
                // Estimate based on weakness level
                if (analysis.getWeaknessScore() >= 0.7) {
                    daysSinceLastPractice = 0; // Needs immediate revision
                } else if (analysis.getWeaknessScore() >= 0.4) {
                    daysSinceLastPractice = 2;
                } else {
                    daysSinceLastPractice = 5;
                }
            } else {
                daysSinceLastPractice = 7; // Not attempted recently
            }

            // Determine revision frequency
            String frequency;
            LocalDate revisionDate;
            if (analysis.getWeaknessScore() >= 0.7) {
                frequency = "Every day";
                revisionDate = today;
            } else if (analysis.getWeaknessScore() >= 0.4) {
                frequency = "Every 2 days";
                revisionDate = today.plusDays(1);
            } else if (analysis.getMasteryLevel() >= 90.0) {
                frequency = "Every 7 days";
                revisionDate = today.plusDays(5);
            } else {
                frequency = "Every 3 days";
                revisionDate = today.plusDays(2);
            }

            schedule.add(RevisionScheduleItem.builder()
                    .topicTitle(analysis.getTopicTitle())
                    .revisionDate(revisionDate)
                    .frequency(frequency)
                    .daysSinceLastPractice(daysSinceLastPractice)
                    .weaknessScore(analysis.getWeaknessScore())
                    .build());
        }

        return schedule;
    }

    private List<String> generateRecommendations(List<WeakTopicAnalysis> analyses,
                                                  List<WeakTopicAnalysis> weakTopics,
                                                  int daysUntilExam) {
        List<String> recommendations = new ArrayList<>();

        if (analyses.isEmpty()) {
            recommendations.add("Upload a PDF and analyze it to get started.");
            return recommendations;
        }

        // First weak topic recommendation
        if (!weakTopics.isEmpty()) {
            WeakTopicAnalysis weakest = weakTopics.get(0);
            recommendations.add(String.format(
                    "Focus on \"%s\" first due to high weakness (%.0f%%) and importance (%.0f%%).",
                    weakest.getTopicTitle(),
                    weakest.getWeaknessScore() * 100,
                    weakest.getImportanceScore() * 100
            ));
        }

        // Multiple weak topics - revision scheduling
        if (weakTopics.size() >= 2) {
            WeakTopicAnalysis second = weakTopics.get(1);
            recommendations.add(String.format(
                    "Revise \"%s\" after completing \"%s\" to reinforce weak areas.",
                    second.getTopicTitle(),
                    weakTopics.get(0).getTopicTitle()
            ));
        }

        // Hard topic splitting
        for (WeakTopicAnalysis t : weakTopics) {
            if (t.getComplexityScore() >= 0.7 && t.getWeaknessScore() > 0.5) {
                recommendations.add(String.format(
                        "\"%s\" is complex (%.0f%%). Split study into 2-3 short sessions across days.",
                        t.getTopicTitle(),
                        t.getComplexityScore() * 100
                ));
                break;
            }
        }

        // Exam urgency
        if (daysUntilExam <= 7) {
            recommendations.add(String.format(
                    "Only %d days until exam. Prioritize weak topics and focus on practice tests.",
                    daysUntilExam
            ));
        } else if (daysUntilExam <= 30) {
            recommendations.add(String.format(
                    "%d days until exam. Cover weak topics this week, then enter revision mode.",
                    daysUntilExam
            ));
        }

        // High importance topics
        for (WeakTopicAnalysis t : analyses) {
            if (t.getImportanceScore() >= 0.9 && t.getMasteryLevel() < 80.0) {
                recommendations.add(String.format(
                        "\"%s\" has very high importance (%.0f%%). Keep it scheduled for regular practice.",
                        t.getTopicTitle(),
                        t.getImportanceScore() * 100
                ));
                break;
            }
        }

        // Deprioritize mastered topics
        for (WeakTopicAnalysis t : analyses) {
            if (t.getMasteryLevel() >= 90.0) {
                recommendations.add(String.format(
                        "\"%s\" is well mastered (%.0f%%). Can be deprioritized temporarily; revise weekly only.",
                        t.getTopicTitle(),
                        t.getMasteryLevel()
                ));
                break;
            }
        }

        return recommendations;
    }

    private List<Integer> identifyPracticeDays(List<StudyRoadmapItem> roadmap) {
        return roadmap.stream()
                .filter(i -> "PRACTICE".equals(i.getActivityType()) || "TEST".equals(i.getActivityType()))
                .map(StudyRoadmapItem::getDay)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private int calculateDaysUntilExam(List<PdfDocument> pdfs) {
        if (pdfs.isEmpty()) return 30;
        return pdfs.stream()
                .map(PdfDocument::getExamDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .map(examDate -> (int) ChronoUnit.DAYS.between(LocalDate.now(), examDate))
                .orElse(30);
    }

    private String complexityLabel(double score) {
        if (score >= 0.7) return "HARD";
        if (score >= 0.4) return "MEDIUM";
        return "EASY";
    }

    private PlannerDto createEmptyPlanner() {
        return PlannerDto.builder()
                .todayTasks(Collections.emptyList())
                .weakTopics(Collections.emptyList())
                .priorityTopics(Collections.emptyList())
                .studyRoadmap(Collections.emptyList())
                .revisionSchedule(Collections.emptyList())
                .recommendations(List.of("Upload a PDF and analyze it to start your study plan."))
                .practiceDays(Collections.emptyList())
                .totalTopics(0)
                .weakTopicsCount(0)
                .averageMastery(0.0)
                .daysUntilExam(0)
                .totalTasksToday(0)
                .totalDurationMinutesToday(0)
                .build();
    }
}