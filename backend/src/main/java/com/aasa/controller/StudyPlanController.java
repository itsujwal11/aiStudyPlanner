package com.aasa.controller;

import com.aasa.dto.StudyPlanRequest;
import com.aasa.dto.StudyPlanResult;
import com.aasa.service.StudyPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/study-plan")
public class StudyPlanController {

    @Autowired
    private StudyPlanService studyPlanService;

    @PostMapping("/generate")
    public ResponseEntity<StudyPlanResult> generatePlan(@RequestBody StudyPlanRequest request) {
        StudyPlanResult plan = studyPlanService.generatePlan(request);
        return ResponseEntity.ok(plan);
    }
}
