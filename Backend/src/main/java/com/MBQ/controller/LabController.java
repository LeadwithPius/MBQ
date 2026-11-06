package com.MBQ.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.MBQ.dto.LabDtos.CancelLabRequest;
import com.MBQ.dto.LabDtos.CompleteLabRequest;
import com.MBQ.dto.LabDtos.CreateLabRequest;
import com.MBQ.dto.LabDtos.LabRequestResponse;
import com.MBQ.model.LabRequestStatus;
import com.MBQ.service.LabService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/lab/requests")
public class LabController {

    private final LabService labService;

    public LabController(LabService labService) {
        this.labService = labService;
    }

    /** Doctors order tests from an open consultation. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public LabRequestResponse request(@Valid @RequestBody CreateLabRequest request) {
        return labService.request(request);
    }

    /** Lab worklist. No status = open tests (requested + in progress). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public List<LabRequestResponse> worklist(@RequestParam(required = false) LabRequestStatus status) {
        return labService.worklist(status);
    }

    @GetMapping("/consultations/{consultationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_STAFF')")
    public List<LabRequestResponse> byConsultation(@PathVariable Long consultationId) {
        return labService.byConsultation(consultationId);
    }

    @GetMapping("/patients/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_STAFF')")
    public List<LabRequestResponse> byPatient(@PathVariable Long patientId) {
        return labService.byPatient(patientId);
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public LabRequestResponse start(@PathVariable Long id) {
        return labService.start(id);
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public LabRequestResponse complete(@PathVariable Long id, @Valid @RequestBody CompleteLabRequest request) {
        return labService.complete(id, request);
    }

    /** The requesting doctor or the lab can cancel an open test. */
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_STAFF')")
    public LabRequestResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelLabRequest request) {
        return labService.cancel(id, request);
    }
}
