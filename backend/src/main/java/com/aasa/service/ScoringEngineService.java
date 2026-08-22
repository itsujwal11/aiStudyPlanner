package com.aasa.service;

import com.aasa.entity.Topic;
import org.springframework.stereotype.Service;

@Service
public class ScoringEngineService {

    public Double calculateComplexityScore(Double conceptDensity, Double keywordDifficulty,
                                          Integer formulaCount, Integer contentLength) {
        if (conceptDensity == null || keywordDifficulty == null) {
            return 0.0;
        }

        double normalizedLength = contentLength != null ? Math.min(contentLength / 10000.0, 1.0) : 0.0;
        double formulaScore = formulaCount != null ? Math.min(formulaCount / 10.0, 1.0) : 0.0;

        return (0.4 * (conceptDensity != null ? conceptDensity / 10.0 : 0.5)) +
               (0.3 * (keywordDifficulty != null ? keywordDifficulty / 10.0 : 0.5)) +
               (0.2 * formulaScore) + 
               (0.1 * normalizedLength);
    }

    public Double calculateImportanceScore(Topic topic) {
        Double topicFrequency = 0.5;
        Double semanticWeight = 0.5;
        Double chapterCoverage = 0.3;

        return (0.5 * topicFrequency) +
               (0.3 * semanticWeight) +
               (0.2 * chapterCoverage);
    }

    /**
     * LEGACY fixed-weight scoring kept only as a helper for backward compatibility.
     * The main study-priority algorithm is now AdaptivePriorityService, which derives
     * priorities from Bayesian Knowledge Tracing mastery, forgetting-curve risk,
     * exam urgency, and topic importance (learner-data-driven instead of static).
     */
    /**
     * Legacy fixed-weight priority formula, kept only as a scoring helper for
     * backward compatibility. The main algorithm now derives priorities from
     * learner evidence — see {@link AdaptivePriorityService#calculatePriority}.
     */
    @Deprecated
    public Double calculatePriorityScore(Double complexity, Double importance,
     Double weaknessLevel, Integer daysUntilExam) {
        if (complexity == null || importance == null || weaknessLevel == null) {
            return 0.0;
        }
        
        double urgency = 1.0 / (Math.max(daysUntilExam, 0) + 1.0);

        return (0.35 * complexity) +
               (0.25 * importance) +
               (0.25 * weaknessLevel) +
               (0.15 * urgency);
    }
}
