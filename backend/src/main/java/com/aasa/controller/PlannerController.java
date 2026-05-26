package com.aasa.controller;

import com.aasa.dto.PlannerDto;
import com.aasa.entity.User;
import com.aasa.service.AuthService;
import com.aasa.service.PlannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.logging.Logger;

@RestController
@RequestMapping("/api/planner")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class PlannerController {

    private static final Logger logger = Logger.getLogger(PlannerController.class.getName());

    @Autowired
    private PlannerService plannerService;

    @Autowired
    private AuthService authService;

    @GetMapping
    public ResponseEntity<PlannerDto> getPlanner(Authentication authentication) {
        try {
            logger.info("Fetching planner for user");
            User user = authService.getUserByEmail(authentication.getName());
            PlannerDto planner = plannerService.generatePlanner(user);
            return ResponseEntity.ok(planner);
        } catch (Exception e) {
            logger.severe("Error generating planner: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}