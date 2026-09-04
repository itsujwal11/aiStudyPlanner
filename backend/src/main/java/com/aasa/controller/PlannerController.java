package com.aasa.controller;

import com.aasa.dto.PlannerDto;
import com.aasa.dto.TaskCompletionRequest;
import com.aasa.entity.User;
import com.aasa.service.AuthService;
import com.aasa.service.PlannerService;
import com.aasa.service.PlannerTaskCompletionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/planner")
public class PlannerController {

    private static final Logger logger = Logger.getLogger(PlannerController.class.getName());

    @Autowired
    private PlannerService plannerService;

    @Autowired
    private AuthService authService;

    @Autowired
    private PlannerTaskCompletionService completionService;

    @GetMapping
    public ResponseEntity<?> getPlanner(Authentication authentication) {
        // Without this an unauthenticated call NPEs on authentication.getName()
        // and is reported as a 500, which reads as a server fault rather than a
        // missing session. Matches the toggle endpoint below.
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not signed in"));
        }
        try {
            User user = authService.getUserByEmail(authentication.getName());
            PlannerDto planner = plannerService.generatePlanner(user);
            return ResponseEntity.ok(planner);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to build planner", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/tasks/toggle")
    public ResponseEntity<?> toggleTaskCompletion(
            Authentication authentication,
            @RequestBody TaskCompletionRequest request
    ) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not signed in"));
        }
        // A missing topicId used to surface as an opaque 500; the caller needs to
        // know the request was wrong, not that the server broke.
        if (request == null || request.getTopicId() == null
                || request.getActivityType() == null || request.getActivityType().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "topicId and activityType are required"));
        }

        try {
            User user = authService.getUserByEmail(authentication.getName());
            completionService.setCompletion(
                    user, LocalDate.now(),
                    request.getTopicId(),
                    request.getActivityType(),
                    request.getSessionIndex(),
                    request.isCompleted()
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "completed", request.isCompleted()
            ));
        } catch (Exception e) {
            // Logged rather than printed so the cause is visible in the server
            // log alongside everything else (a missing table, for instance).
            logger.log(Level.SEVERE, "Failed to toggle planner task for "
                    + authentication.getName() + ": " + e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Could not save the task"));
        }
    }
}