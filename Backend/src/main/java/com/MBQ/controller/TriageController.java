package com.MBQ.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.MBQ.dto.TriageDtos.AssessmentResponse;
import com.MBQ.dto.TriageDtos.CreateObservationRequest;
import com.MBQ.dto.TriageDtos.ObservationResponse;
import com.MBQ.dto.TriageDtos.ReviewRequest;
import com.MBQ.service.TriageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/triage")
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    /**
     * Record a patient's triage observations.
     * Only administrators and nurses may create observations.
     */
    @PostMapping("/observations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'NURSE')")
    public AssessmentResponse createObservation(
            @Valid @RequestBody CreateObservationRequest request) {

        return triageService.create(request);
    }

    /**
     * Retrieve a triage observation by ID.
     * Administrators, doctors, and nurses may view observations.
     */
    @GetMapping("/observations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ObservationResponse getObservation(@PathVariable Long id) {
        return triageService.getObservation(id);
    }

    /**
     * Retrieve a patient's triage observations.
     */
    @GetMapping("/patients/{patientId}/observations")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public List<ObservationResponse> getPatientObservations(
            @PathVariable Long patientId) {

        return triageService.getPatientObservations(patientId);
    }

    /**
     * Retrieve risk assessments associated with a triage observation.
     */
    @GetMapping("/observations/{observationId}/assessments")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public List<AssessmentResponse> getAssessments(
            @PathVariable Long observationId) {

        return triageService.getAssessments(observationId);
    }

    /**
     * Review a clinical risk assessment.
     * Only administrators and doctors may review assessments.
     */
    @PatchMapping("/assessments/{assessmentId}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public AssessmentResponse reviewAssessment(
            @PathVariable Long assessmentId,
            @Valid @RequestBody ReviewRequest request) {

        return triageService.review(assessmentId, request);
    }
}