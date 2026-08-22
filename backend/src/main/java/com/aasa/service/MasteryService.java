package com.aasa.service;

import com.aasa.entity.ReviewLog;
import com.aasa.entity.StudyProgress;
import com.aasa.entity.Topic;
import com.aasa.entity.User;
import com.aasa.repository.ReviewLogRepository;
import com.aasa.repository.StudyProgressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.logging.Logger;

@Service
public class MasteryService {

    private static final Logger logger = Logger.getLogger(MasteryService.class.getName());

    private static final double ALPHA_PRIOR = 2.0;
    private static final double BETA_PRIOR = 8.0;
    private static final double MASTERY_THRESHOLD = 0.85;
    private static final double GUESS_PENALTY_SECONDS = 3.0;

    @Autowired
    private StudyProgressRepository studyProgressRepository;

    @Autowired
    private ReviewLogRepository reviewLogRepository;

    private final BayesianKnowledgeTracingService knowledgeTracingService =
            new BayesianKnowledgeTracingService();

    @Transactional
    public SpacedRepetitionResult updateAfterAttempt(
            User user, Topic topic, StudyProgress progress,
            boolean isCorrect, int timeTakenSeconds) {

        int sm2Quality = mapToSm2Quality(isCorrect, timeTakenSeconds);
        double masteryBefore = progress.getMasteryLevel() != null ? progress.getMasteryLevel() : 0.0;

        // Two estimators are combined on every attempt:
        // 1) Beta-Binomial posterior — accumulates raw success/failure evidence (alpha/beta)
        // 2) Bayesian Knowledge Tracing — models guess/slip noise explicitly
        double betaBinomialMastery = updateBayesianMastery(progress, isCorrect, timeTakenSeconds);
        double bktMastery = knowledgeTracingService.updateMastery(masteryBefore, isCorrect);
        double newMastery = clamp01(0.5 * betaBinomialMastery + 0.5 * bktMastery);
        SM2Result sm2 = applySm2(progress, sm2Quality);
        int rating = mapQualityToRating(sm2Quality);

        progress.setMasteryLevel(newMastery);
        progress.setSm2Interval(sm2.interval);
        progress.setSm2Efactor(sm2.efactor);
        progress.setSm2Repetitions(sm2.repetitions);
        progress.setNextReviewDate(LocalDate.now().plusDays(sm2.interval));
        progress.setLastStudyDate(LocalDate.now());

        studyProgressRepository.save(progress);

        ReviewLog log = ReviewLog.builder()
                .user(user)
                .topic(topic)
                .reviewType("QUIZ")
                .rating(rating)
                .responseTimeMs(timeTakenSeconds * 1000)
                .scheduledDays(sm2.interval)
                .actualInterval(progress.getSm2Interval() != null ? progress.getSm2Interval() : 0)
                .masteryBefore(masteryBefore)
                .masteryAfter(newMastery)
                .createdAt(LocalDateTime.now())
                .build();
        reviewLogRepository.save(log);

        return new SpacedRepetitionResult(newMastery, sm2.interval, sm2.efactor, sm2.repetitions);
    }

    private double updateBayesianMastery(StudyProgress progress, boolean isCorrect, int timeTakenSec) {
        double alpha = progress.getAlpha() != null ? progress.getAlpha() : ALPHA_PRIOR;
        double beta = progress.getBeta() != null ? progress.getBeta() : BETA_PRIOR;

        if (isCorrect) {
            alpha += 1.0;
        } else {
            beta += 1.0;
        }

        if (timeTakenSec < GUESS_PENALTY_SECONDS && isCorrect) {
            beta += 0.3;
        }

        progress.setAlpha(alpha);
        progress.setBeta(beta);

        return alpha / (alpha + beta);
    }

    private int mapToSm2Quality(boolean isCorrect, int timeTakenSeconds) {
        if (isCorrect && timeTakenSeconds < 5) return 4;
        if (isCorrect && timeTakenSeconds < 15) return 3;
        if (isCorrect) return 2;
        return 1;
    }

    private int mapQualityToRating(int sm2Quality) {
        if (sm2Quality >= 4) return 4;
        if (sm2Quality >= 3) return 3;
        if (sm2Quality >= 2) return 2;
        return 1;
    }

    private SM2Result applySm2(StudyProgress progress, int quality) {
        int n = progress.getSm2Repetitions() != null ? progress.getSm2Repetitions() : 0;
        double ef = progress.getSm2Efactor() != null ? progress.getSm2Efactor() : 2.5;
        int interval;

        if (quality < 3) {
            n = 0;
            interval = 1;
        } else {
            if (n == 0) {
                interval = 1;
            } else if (n == 1) {
                interval = 6;
            } else {
                interval = (int) Math.ceil(progress.getSm2Interval() * ef);
            }
            n++;
        }

        ef = ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        if (ef < 1.3) ef = 1.3;

        return new SM2Result(interval, ef, n);
    }

    public long getDueTodayCount(Long userId) {
        return studyProgressRepository.countDueForReview(userId, LocalDate.now());
    }

    public double predictedRetention(double mastery, double daysSinceReview) {
        double decayRate = 0.5 * (1 - mastery * 0.7);
        return mastery * Math.exp(-decayRate * daysSinceReview);
    }

    /** Exponential forgetting-curve risk from the BKT model (1 - e^(-lambda*days)). */
    public double forgettingRisk(double mastery, long daysSinceReview) {
        return knowledgeTracingService.forgettingRisk(mastery, daysSinceReview);
    }

    private double clamp01(double v) {
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }

    private static class SM2Result {
        final int interval;
        final double efactor;
        final int repetitions;

        SM2Result(int interval, double efactor, int repetitions) {
            this.interval = interval;
            this.efactor = efactor;
            this.repetitions = repetitions;
        }
    }

    public static class SpacedRepetitionResult {
        public final double mastery;
        public final int intervalDays;
        public final double efactor;
        public final int repetitions;

        public SpacedRepetitionResult(double mastery, int intervalDays, double efactor, int repetitions) {
            this.mastery = mastery;
            this.intervalDays = intervalDays;
            this.efactor = efactor;
            this.repetitions = repetitions;
        }
    }
}
