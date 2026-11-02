package com.MBQ.controller;

import com.MBQ.dto.QueueDtos.PatientRegistrationRequest;
import com.MBQ.dto.QueueDtos.PatientResponse;
import com.MBQ.model.Patient;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.PatientRepository;
import com.MBQ.repository.StaffUserRepository;
import com.MBQ.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final QueueService queueService;
    private final PatientRepository patientRepository;
    private final StaffUserRepository staffUserRepository;

    public PatientController(
            QueueService queueService,
            PatientRepository patientRepository,
            StaffUserRepository staffUserRepository) {
        this.queueService = queueService;
        this.patientRepository = patientRepository;
        this.staffUserRepository = staffUserRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public List<PatientResponse> listPatients() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        StaffUser staff = staffUserRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated staff account was not found"));

        if (staff.getDepartment() == null || staff.getDepartment().getFacility() == null) {
            return List.of();
        }

        Long facilityId = staff.getDepartment().getFacility().getId();
        return patientRepository.findByFacility_IdOrderByIdDesc(facilityId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PatientResponse registerPatient(@Valid @RequestBody PatientRegistrationRequest request) {
        return queueService.registerPatient(request);
    }

    private PatientResponse toResponse(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getName(),
                patient.getAge(),
                patient.getSex(),
                patient.getContact(),
                patient.getBloodGroup(),
                patient.getFacility() == null ? null : patient.getFacility().getId());
    }
}
