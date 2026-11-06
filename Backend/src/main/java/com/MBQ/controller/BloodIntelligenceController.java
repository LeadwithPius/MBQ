package com.MBQ.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.MBQ.service.BloodIntelligenceService;
import com.MBQ.service.BloodIntelligenceService.Forecast;

@RestController
@RequestMapping("/api/blood-intelligence")
@PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF', 'DOCTOR', 'NURSE')")
public class BloodIntelligenceController {
    private final BloodIntelligenceService intelligence;

    public BloodIntelligenceController(BloodIntelligenceService intelligence) {
        this.intelligence = intelligence;
    }

    @GetMapping("/forecast")
    public List<Forecast> forecast() {
        return intelligence.forecast();
    }
}
