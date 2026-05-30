package com.aasa.controller;

import com.aasa.dto.DashboardDto;
import com.aasa.entity.User;
import com.aasa.service.AuthService;
import com.aasa.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.logging.Logger;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class DashboardController {

    private static final Logger logger = Logger.getLogger(DashboardController.class.getName());

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private AuthService authService;

    @GetMapping("/pdf/{pdfId}")
    public ResponseEntity<DashboardDto> getPdfDashboard(@PathVariable Long pdfId, Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            User user = authService.getUserByEmail(authentication.getName());
            DashboardDto dashboard = dashboardService.generatePdfDashboard(user, pdfId);
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            logger.severe("Error generating PDF dashboard: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<DashboardDto> getDashboard(Authentication authentication) {
        try {
            if (authentication == null) {
                logger.severe("Error generating dashboard: authentication is null");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            logger.info("Generating dashboard for user: " + authentication.getName());
            User user = authService.getUserByEmail(authentication.getName());
            DashboardDto dashboard = dashboardService.generateDashboard(user);
            logger.info("Dashboard generated successfully");
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            logger.severe("Error generating dashboard: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
