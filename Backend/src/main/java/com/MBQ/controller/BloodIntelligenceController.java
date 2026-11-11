package com.MBQ.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.MBQ.service.BloodIntelligenceService;
import com.MBQ.service.BloodIntelligenceService.Forecast;
import com.MBQ.service.BloodIntelligenceMlClient;
import com.MBQ.service.BloodIntelligenceMlClient.MlResponse;

@RestController
@RequestMapping("/api/blood-intelligence")
@PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF', 'DOCTOR', 'NURSE')")
public class BloodIntelligenceController {
    private final BloodIntelligenceService intelligence;
    private final BloodIntelligenceMlClient mlClient;

    public BloodIntelligenceController(BloodIntelligenceService intelligence,
            BloodIntelligenceMlClient mlClient) {
        this.intelligence = intelligence;
        this.mlClient = mlClient;
    }

    @GetMapping("/forecast")
    public List<Forecast> forecast() {
        return intelligence.forecast();
    }

    /** Synthetic-only demonstration endpoint; it never reads or writes the MBQ database. */
    @GetMapping("/demo-forecast")
    public MlResponse demoForecast() {
        MlResponse response = mlClient.demoForecast();
        if (response == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "The XGBoost demo service is not configured or is unavailable.");
        }
        return response;
    }
}
