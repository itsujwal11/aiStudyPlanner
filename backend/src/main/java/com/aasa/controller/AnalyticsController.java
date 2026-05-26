package com.aasa.controller;

import com.aasa.entity.User;
import com.aasa.service.AuthService;
import com.aasa.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private AuthService authService;

    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceAnalytics(Authentication authentication) {
        User user = authService.getUserByEmail(authentication.getName());
        Map<String, Object> analytics = analyticsService.getPerformanceAnalytics(user);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/topic/{topicId}")
    public ResponseEntity<Map<String, Object>> getTopicAnalytics(
            @PathVariable Long topicId,
            Authentication authentication) {
        User user = authService.getUserByEmail(authentication.getName());
        Map<String, Object> analytics = analyticsService.getTopicAnalytics(user, topicId);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/comparison")
    public ResponseEntity<List<Map<String, Object>>> getComparisonAnalytics(Authentication authentication) {
        User user = authService.getUserByEmail(authentication.getName());
        List<Map<String, Object>> analytics = analyticsService.getComparisonAnalytics(user);
        return ResponseEntity.ok(analytics);
    }
}
