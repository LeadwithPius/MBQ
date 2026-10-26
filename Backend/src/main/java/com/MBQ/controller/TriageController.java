package com.MBQ.controller;

import com.MBQ.dto.TriageDtos;
import com.MBQ.service.TriageService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/triage")
public class TriageController {

    private final TriageService service;

    public TriageController(TriageService service) {
        this.service = service;
    }

    @PostMapping("/observations")
    @ResponseStatus(HttpStatus.CREATED)
    public TriageDtos.AssessmentResponse create(
            @RequestBody TriageDtos.CreateObservationRequest request) {

        return service.create(request);
    }

    @GetMapping("/observations/{id}")
    public TriageDtos.ObservationResponse getObservation(
            @PathVariable Long id) {

        return service.getObservation(id);
    }

    @GetMapping("/patients/{patientId}/observations")
    public List<TriageDtos.ObservationResponse> getPatientObservations(
            @PathVariable Long patientId) {

        return service.getPatientObservations(patientId);
    }

    @GetMapping("/observations/{observationId}/assessments")
    public List<TriageDtos.AssessmentResponse> getAssessments(
            @PathVariable Long observationId) {

        return service.getAssessments(observationId);
    }

    @PatchMapping("/assessments/{assessmentId}/review")
    public TriageDtos.AssessmentResponse review(
            @PathVariable Long assessmentId,
            @RequestBody TriageDtos.ReviewRequest request) {

        return service.review(assessmentId, request);
    }
}