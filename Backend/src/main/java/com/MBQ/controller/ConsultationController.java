package com.MBQ.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.MBQ.dto.ConsultationDtos.ConsultationResponse;
import com.MBQ.dto.ConsultationDtos.SaveConsultationRequest;
import com.MBQ.service.ConsultationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/consultations")
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
public class ConsultationController {

    private final ConsultationService consultationService;

    public ConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultationResponse saveDraft(
            @Valid @RequestBody SaveConsultationRequest request) {
        return consultationService.saveDraft(request);
    }

    @GetMapping("/tickets/{ticketId}")
    public ConsultationResponse getByTicket(@PathVariable Long ticketId) {
        return consultationService.getByTicket(ticketId);
    }

    @GetMapping("/patients/{patientId}")
    public List<ConsultationResponse> getByPatient(@PathVariable Long patientId) {
        return consultationService.getByPatient(patientId);
    }

    @PatchMapping("/{consultationId}/complete")
    public ConsultationResponse complete(@PathVariable Long consultationId) {
        return consultationService.complete(consultationId);
    }
}
