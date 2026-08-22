package com.aasa.service;

import org.springframework.stereotype.Service;

/**
 * Bayesian Knowledge Tracing (BKT) — the mastery-estimation core of the
 * Adaptive Knowledge-Tracing and RAG Recommendation Algorithm.
 *
 * Given the student's answer history, each attempt updates the probability
 * P(K) that the student has mastered the underlying skill:
 *
 *   correct answer:   P(obs) = (1 - slip) * P          / ((1 - slip) * P + guess * (1 - P))
 *   incorrect answer: P(obs) = slip * P                / (slip * P + (1 - guess) * (1 - P))
 *   learning step:    P(new) = P(obs) + (1 - P(obs)) * learn
 *
 * The learning transition is asymmetric: answering correctly implies a stronger
 * chance the skill was just consolidated than answering incorrectly.
 *
 * The same service models memory decay with an exponential forgetting curve:
 *
 *   forgettingRisk = 1 - e^(-lambda * daysSinceLastReview)
 *
 * where lambda grows as mastery falls, so weak topics decay faster than strong ones.
 */
@Service
public class BayesianKnowledgeTracingService {

    /** Chance a knowledgeable student still answers wrong (slip). */
    public static final double DEFAULT_SLIP_PROBABILITY = 0.10;
    /** Chance an unknowing student still answers right (guess). */
    public static final double DEFAULT_GUESS_PROBABILITY = 0.20;
    /** Probability the skill is learned after a correct practice opportunity. */
    public static final double LEARN_PROBABILITY_ON_CORRECT = 0.40;
    /** Probability the skill is partially learned after seeing feedback on a mistake. */
    public static final double LEARN_PROBABILITY_ON_INCORRECT = 0.15;
    /** Base daily decay rate; scaled by (1.6 - mastery) so weak topics forget faster. */
    public static final double BASE_FORGETTING_LAMBDA = 0.15;

    /**
     * Convenience BKT update using the default model parameters.
     */
    public double updateMastery(double previousMastery, boolean answeredCorrectly) {
        return updateMastery(previousMastery, answeredCorrectly,
                DEFAULT_GUESS_PROBABILITY,
                DEFAULT_SLIP_PROBABILITY,
                answeredCorrectly ? LEARN_PROBABILITY_ON_CORRECT : LEARN_PROBABILITY_ON_INCORRECT);
    }

    /**
     * Full Bayesian Knowledge Tracing update.
     *
     * @param previousMastery      prior P(K) in [0,1]
     * @param answeredCorrectly    outcome of the attempt
     * @param guessProbability     P(correct | not mastered)
     * @param slipProbability      P(wrong | mastered)
     * @param learningProbability  P(learn during this opportunity)
     * @return posterior mastery P(K new) in [0,1]
     */
    public double updateMastery(double previousMastery, boolean answeredCorrectly,
                                double guessProbability, double slipProbability,
                                double learningProbability) {
        double p = clamp01(previousMastery);
        double g = clamp01(guessProbability);
        double s = clamp01(slipProbability);
        double l = clamp01(learningProbability);

        double pObserved;
        if (answeredCorrectly) {
            double numerator = (1.0 - s) * p;
            double denominator = numerator + g * (1.0 - p);
            pObserved = denominator <= 0.0 ? p : numerator / denominator;
        } else {
            double numerator = s * p;
            double denominator = numerator + (1.0 - g) * (1.0 - p);
            pObserved = denominator <= 0.0 ? p : numerator / denominator;
        }

        // Learning transition: every practice opportunity may produce learning.
        double afterLearning = pObserved + (1.0 - pObserved) * l;
        return clamp01(afterLearning);
    }

    /**
     * Exponential forgetting curve: risk = 1 - e^(-lambda * daysSinceLastReview).
     * Lambda scales inversely with mastery, so fragile knowledge decays fastest.
     *
     * @param mastery              current mastery probability (0..1)
     * @param daysSinceLastReview  whole days since the topic was last studied (>= 0)
     * @return forgetting risk in [0,1); 0 when reviewed today
     */
    public double forgettingRisk(double mastery, long daysSinceLastReview) {
        long days = Math.max(0L, daysSinceLastReview);
        if (days == 0L) {
            return 0.0;
        }
        double lambda = BASE_FORGETTING_LAMBDA * (1.6 - clamp01(mastery));
        return 1.0 - Math.exp(-lambda * days);
    }

    private double clamp01(double value) {
        return value < 0.0 ? 0.0 : (Math.min(value, 1.0));
    }
}
