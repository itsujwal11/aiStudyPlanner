package com.aasa.service;

import com.aasa.dto.StudyProgressDto;
import com.aasa.entity.PdfDocument;
import com.aasa.entity.QuizAttempt;
import com.aasa.entity.StudyProgress;
import com.aasa.entity.Topic;
import com.aasa.entity.User;
import com.aasa.repository.PdfDocumentRepository;
import com.aasa.repository.QuizAttemptRepository;
import com.aasa.repository.StudyProgressRepository;
import com.aasa.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class StudyProgressService {

    private static final Logger logger = Logger.getLogger(StudyProgressService.class.getName());

    @Autowired
    private StudyProgressRepository studyProgressRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private PdfDocumentRepository pdfDocumentRepository;

    @Autowired
    private WeaknessEngineService weaknessEngineService;

    @Autowired
    private ScoringEngineService scoringEngineService;

    @Autowired
    private MasteryService masteryService;

    public StudyProgress getOrCreateProgress(User user, Topic topic) {
        logger.info("Getting or creating progress for user " + user.getId() + " and topic " + topic.getId());

        return studyProgressRepository.findByUserIdAndTopicId(user.getId(), topic.getId())
                .orElseGet(() -> {
                    logger.info("Creating new progress record");
                    StudyProgress progress = StudyProgress.builder()
                            .user(user)
                            .topic(topic)
                            .weaknessLevel(StudyProgress.WeaknessLevel.NOT_ATTEMPTED)
                            .completionPercentage(0.0)
                            .bestScore(0.0)
                            .totalAttempts(0)
                            .correctAttempts(0)
                            .build();
                    return studyProgressRepository.save(progress);
                });
    }

    public void updateProgressAfterQuizAttempt(User user, Topic topic, QuizAttempt attempt) {
        logger.info("Updating progress after quiz attempt for user " + user.getId() + " and topic " + topic.getId());

        StudyProgress progress = getOrCreateProgress(user, topic);

        progress.setTotalAttempts(progress.getTotalAttempts() + 1);
        if (attempt.getIsCorrect()) {
            progress.setCorrectAttempts(progress.getCorrectAttempts() + 1);
        }

        Double score = (progress.getCorrectAttempts() * 100.0) / progress.getTotalAttempts();
        if (score > progress.getBestScore()) {
            progress.setBestScore(score);
            logger.info("New best score: " + score);
        }

        Double completionPercentage = (progress.getCorrectAttempts() * 100.0) / progress.getTotalAttempts();
        progress.setCompletionPercentage(completionPercentage);

        StudyProgress.WeaknessLevel weaknessLevel = weaknessEngineService.calculateWeaknessLevel(score);
        progress.setWeaknessLevel(weaknessLevel);

        MasteryService.SpacedRepetitionResult sr = masteryService.updateAfterAttempt(
                user, topic, progress,
                attempt.getIsCorrect(),
                attempt.getTimeTakenSeconds() != null ? attempt.getTimeTakenSeconds().intValue() : 0
        );

        logger.info("Updated progress - Score: " + score + ", Weakness: " + weaknessLevel
                + ", Mastery: " + String.format("%.4f", sr.mastery)
                + ", Next review: " + progress.getNextReviewDate());

        studyProgressRepository.save(progress);

        updateTopicPriorities(user);
    }

    public void updateTopicPriorities(User user) {
        logger.info("Recalculating priorities for all topics of user " + user.getId());

        List<Topic> userTopics = topicRepository.findByUserIdOrderByPriority(user.getId());
        logger.info("Found " + userTopics.size() + " topics to recalculate");

        for (Topic topic : userTopics) {
            StudyProgress progress = studyProgressRepository.findByUserIdAndTopicId(user.getId(), topic.getId())
                    .orElse(null);

            if (progress != null) {
                PdfDocument pdf = topic.getPdfDocument();
                long daysUntilExam = ChronoUnit.DAYS.between(LocalDate.now(), pdf.getExamDate());

                Double weaknessScore = weaknessEngineService.getWeaknessScore(progress.getWeaknessLevel());

                Double priorityScore = scoringEngineService.calculatePriorityScore(
                        topic.getComplexityScore(),
                        topic.getImportanceScore(),
                        weaknessScore,
                        (int) daysUntilExam
                );

                logger.info("Topic: " + topic.getTitle() + " - Weakness: " + weaknessScore + ", Priority: " + priorityScore + ", Days: " + daysUntilExam);

                // Update both the specific weakness score and the priority on the topic for UI consistency
                topic.setWeaknessScore(weaknessScore);
                topic.setPriorityScore(priorityScore);
                topicRepository.save(topic);
            }
        }

        logger.info("Priority recalculation completed");
    }

    public List<StudyProgressDto> getUserProgressRankedByPriority(Long userId) {
        logger.info("Fetching ranked progress for user " + userId);
        return studyProgressRepository.findByUserIdOrderByPriority(userId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<StudyProgressDto> getWeakTopics(Long userId) {
        logger.info("Fetching weak topics for user " + userId);
        return studyProgressRepository.findByUserId(userId)
                .stream()
                .filter(sp -> sp.getWeaknessLevel() == StudyProgress.WeaknessLevel.HIGH ||
                             sp.getWeaknessLevel() == StudyProgress.WeaknessLevel.MEDIUM)
                .sorted((a, b) -> b.getTopic().getPriorityScore().compareTo(a.getTopic().getPriorityScore()))
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public Double getAverageCompletion(Long userId) {
        Double avg = studyProgressRepository.getAverageCompletion(userId);
        return avg != null ? avg : 0.0;
    }

    private StudyProgressDto convertToDto(StudyProgress progress) {
        return StudyProgressDto.builder()
                .topicId(progress.getTopic().getId())
                .topicTitle(progress.getTopic().getTitle())
                .weaknessLevel(progress.getWeaknessLevel().toString())
                .completionPercentage(progress.getCompletionPercentage())
                .bestScore(progress.getBestScore())
                .totalAttempts(progress.getTotalAttempts())
                .correctAttempts(progress.getCorrectAttempts())
                .priorityScore(progress.getTopic().getPriorityScore())
                .masteryLevel(progress.getMasteryLevel())
                .sm2Interval(progress.getSm2Interval())
                .nextReviewDate(progress.getNextReviewDate())
                .build();
    }
}
