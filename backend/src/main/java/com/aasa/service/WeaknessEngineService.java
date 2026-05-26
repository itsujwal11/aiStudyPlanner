package com.aasa.service;

import com.aasa.entity.StudyProgress;
import org.springframework.stereotype.Service;

@Service
public class WeaknessEngineService {

    public StudyProgress.WeaknessLevel calculateWeaknessLevel(Double score) {
        if (score == null) {
            return StudyProgress.WeaknessLevel.NOT_ATTEMPTED;
        }

        if (score >= 75) {
            return StudyProgress.WeaknessLevel.LOW;
        } else if (score >= 50) {
            return StudyProgress.WeaknessLevel.MEDIUM;
        } else {
            return StudyProgress.WeaknessLevel.HIGH;
        }
    }

    public Double getWeaknessScore(StudyProgress.WeaknessLevel level) {
        return switch (level) {
            case LOW -> 0.2;
            case MEDIUM -> 0.5;
            case HIGH -> 0.9;
            case NOT_ATTEMPTED -> 1.0;
        };
    }
}
