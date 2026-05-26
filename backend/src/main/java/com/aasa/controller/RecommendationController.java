package com.aasa.controller;

import com.aasa.entity.User;
import com.aasa.service.AuthService;
import com.aasa.service.RecommendationEngineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/recommendations")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RecommendationController {

    @Autowired
    private RecommendationEngineService recommendationEngineService;

    @Autowired
    private AuthService authService;

    @GetMapping("/next-topics")
    public ResponseEntity<List<Map<String, Object>>> getNextTopics(
            @RequestParam(defaultValue = "5") int limit,
            Authentication authentication) {
        User user = authService.getUserByEmail(authentication.getName());
        List<com.aasa.entity.Topic> topics = recommendationEngineService.getRecommendedTopics(user, limit);

        List<Map<String, Object>> response = topics.stream()

                .map(topic -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", topic.getId());
                    map.put("title", topic.getTitle());
                    map.put("priority", topic.getPriorityScore());
                    map.put("complexity", topic.getComplexityScore());
                    return map;
                })
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/insights")
    public ResponseEntity<Map<String, Object>> getStudyInsights(Authentication authentication) {
        User user = authService.getUserByEmail(authentication.getName());
        Map<String, Object> insights = recommendationEngineService.getStudyInsights(user);
        return ResponseEntity.ok(insights);
    }

    @GetMapping("/schedule")
    public ResponseEntity<List<Map<String, Object>>> getStudySchedule(
            @RequestParam(defaultValue = "7") int daysAhead,
            Authentication authentication) {
        User user = authService.getUserByEmail(authentication.getName());
        List<Map<String, Object>> schedule = recommendationEngineService.getStudySchedule(user, daysAhead);
        return ResponseEntity.ok(schedule);
    }
}
