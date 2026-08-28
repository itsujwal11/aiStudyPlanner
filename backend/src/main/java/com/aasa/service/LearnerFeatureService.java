package com.aasa.service;

import com.aasa.entity.QuizAttempt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Builds the feature vector the offline weakness model expects, from a
 * learner's attempt history on one topic.
 *
 * The model ({@code ml/train_model.py}) was trained on the ASSISTments
 * skill-builder dataset, where each row is one practice opportunity on one
 * (student, skill) pair and the target is whether that attempt was answered
 * correctly. AASA's (user, topic) attempt history is the direct analogue, so
 * the same five features are reconstructed here:
 *
 * <pre>
 *   previous_attempts      attempts recorded on this topic so far
 *   previous_accuracy      correct / attempts so far (0.5 with no history)
 *   average_response_time  mean seconds per attempt (null when unknown)
 *   recent_accuracy        correct ratio over the last 3 attempts (0.5 with none)
 *   opportunity            1-based index of the attempt being predicted
 * </pre>
 *
 * Training built these features with a {@code shift(1)} so a row never saw its
 * own outcome. Here the equivalent guarantee is structural: we summarise every
 * attempt made <em>so far</em> to predict the <em>next</em> one, which has not
 * happened yet. Weakness is then {@code 1 - P(correct on next attempt)}.
 *
 * <p>{@code averageResponseTime} is deliberately nullable rather than defaulted:
 * the trained pipeline starts with {@code SimpleImputer(strategy="median")}, so
 * a null is filled from the training distribution, which is a better estimate
 * than any constant this class could invent.</p>
 */
@Service
public class LearnerFeatureService {

    /** Neutral prior used for accuracy features when there is no history yet. */
    public static final double NO_HISTORY_ACCURACY = 0.5;
    /** Window size for the recent-accuracy feature, matching training. */
    public static final int RECENT_WINDOW = 3;

    /**
     * @param attempts attempts on one topic, oldest first
     *                 ({@code QuizAttemptRepository.findByUserIdAndTopicId} returns this order)
     */
    public LearnerFeatures extract(List<QuizAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return new LearnerFeatures(0.0, NO_HISTORY_ACCURACY, null, NO_HISTORY_ACCURACY, 1.0);
        }

        int total = attempts.size();
        int correct = 0;
        double responseSecondsTotal = 0.0;
        int responseSecondsCount = 0;

        for (QuizAttempt attempt : attempts) {
            if (Boolean.TRUE.equals(attempt.getIsCorrect())) {
                correct++;
            }
            Long seconds = attempt.getTimeTakenSeconds();
            if (seconds != null && seconds >= 0) {
                responseSecondsTotal += seconds;
                responseSecondsCount++;
            }
        }

        double previousAccuracy = (double) correct / total;

        int recentFrom = Math.max(0, total - RECENT_WINDOW);
        int recentCorrect = 0;
        for (int i = recentFrom; i < total; i++) {
            if (Boolean.TRUE.equals(attempts.get(i).getIsCorrect())) {
                recentCorrect++;
            }
        }
        double recentAccuracy = (double) recentCorrect / (total - recentFrom);

        Double averageResponseTime = responseSecondsCount > 0
                ? responseSecondsTotal / responseSecondsCount
                : null;

        return new LearnerFeatures(
                total,
                previousAccuracy,
                averageResponseTime,
                recentAccuracy,
                total + 1.0
        );
    }

    /** The five model inputs, named as the service's JSON contract expects. */
    public record LearnerFeatures(
            double previousAttempts,
            double previousAccuracy,
            Double averageResponseTime,
            double recentAccuracy,
            double opportunity
    ) {}
}
