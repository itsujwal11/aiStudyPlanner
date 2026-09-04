package com.aasa.service;

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

}
