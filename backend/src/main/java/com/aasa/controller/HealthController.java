package com.aasa.controller;

import com.aasa.service.MlWeaknessClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Autowired
    private MlWeaknessClient mlWeaknessClient;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        // weaknessModel reports whether the hybrid path is actually live, so a
        // demo never silently runs on evidence-only scoring while claiming
        // otherwise. "disabled" = switched off by config; "unavailable" =
        // enabled but the service or its artifact is missing.
        String weaknessModel;
        if (!mlWeaknessClient.isEnabled()) {
            weaknessModel = "disabled";
        } else {
            weaknessModel = mlWeaknessClient.isModelAvailable() ? "live" : "unavailable";
        }

        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "aasa-backend",
                "weaknessModel", weaknessModel,
                "weaknessScoring", "live".equals(weaknessModel)
                        ? "hybrid (0.70 evidence + 0.30 model)"
                        : "evidence only",
                "timestamp", Instant.now().toString()
        ));
    }
}
