package com.aasa.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysisResponse {
    private List<TopicAnalysis> topics;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopicAnalysis {
        private String title;
        private String description;
        private SemanticSignals signals;
        private Double importance;
        private Double complexity;
        private List<QuizQuestion> quiz;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class SemanticSignals {
            private Double conceptDensity;
            private Double keywordDifficulty;
            private Integer formulaCount;
            private Integer length;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class QuizQuestion {
            private String question;
            private List<String> options;
            private String answer;
            private String difficulty;
            private String explanation;
        }
    }
}
