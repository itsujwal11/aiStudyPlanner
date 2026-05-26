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

    public Double calculatePriorityScore(Double complexity, Double importance,
                                        Double weaknessLevel, Integer daysUntilExam) {
        if (complexity == null || importance == null || weaknessLevel == null) {
            return 0.0;
        }

        double urgency = 1.0 / (daysUntilExam + 1.0);

        return (0.35 * complexity) +
               (0.25 * importance) +
               (0.25 * weaknessLevel) +
               (0.15 * urgency);
    }
}
