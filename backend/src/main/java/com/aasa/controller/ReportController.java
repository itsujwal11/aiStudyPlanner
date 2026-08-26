package com.aasa.controller;

import com.aasa.dto.ReportSummaryDto;
import com.aasa.entity.User;
import com.aasa.service.AuthService;
import com.aasa.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final Logger logger = Logger.getLogger(ReportController.class.getName());

    @Autowired
    private ReportService reportService;

    @Autowired
    private AuthService authService;

    @GetMapping("/study-report")
    public ResponseEntity<?> getStudyReport(Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            User user = authService.getUserByEmail(authentication.getName());
            ReportSummaryDto report = reportService.generateStudyReport(user);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            logger.severe("Error generating study report: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate study report"));
        }
    }
}