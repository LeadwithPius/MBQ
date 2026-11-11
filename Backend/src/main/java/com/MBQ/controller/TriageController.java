package com.MBQ.controller;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleTriageError(
            ResponseStatusException exception) {
        String detail = exception.getReason() == null
                ? "The triage request could not be completed."
                : exception.getReason();
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        String formError = null;
        if (detail.equals("patientId is required") || detail.equals("Patient not found")) {
            fieldErrors.put("patientId", detail);
        } else if (detail.equals("Queue ticket not found")
                || detail.equals("queueTicketId is required")) {
            fieldErrors.put("queueTicketId", detail);
        } else if (detail.equals("systolicBp is required")
                || detail.startsWith("systolicBp must be")) {
            fieldErrors.put("systolicBp", detail);
        } else if (detail.equals("diastolicBp is required")
                || detail.startsWith("diastolicBp must be")) {
            fieldErrors.put("diastolicBp", detail);
        } else if (detail.equals("heartRate is required")
                || detail.startsWith("heartRate must be")) {
            fieldErrors.put("heartRate", detail);
        } else if (detail.equals("temperatureC is required")
                || detail.startsWith("temperatureC must be")) {
            fieldErrors.put("temperatureC", detail);
        } else if (detail.equals("respiratoryRate is required")
                || detail.startsWith("respiratoryRate must be")) {
            fieldErrors.put("respiratoryRate", detail);
        } else if (detail.equals("spo2Percent is required")
                || detail.startsWith("spo2Percent must be")) {
            fieldErrors.put("spo2Percent", detail);
        } else if (detail.equals("symptoms is required")) {
            fieldErrors.put("symptoms", detail);
        } else if (detail.equals("notes is required")) {
            fieldErrors.put("notes", detail);
        } else if (detail.startsWith("Systolic blood pressure")) {
            fieldErrors.put("systolicBp", detail);
            fieldErrors.put("diastolicBp", detail);
        } else {
            formError = detail;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", exception.getStatusCode().value());
        body.put("error", "Triage request failed");
        body.put("fieldErrors", fieldErrors);
        body.put("formError", formError);
        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }
}
