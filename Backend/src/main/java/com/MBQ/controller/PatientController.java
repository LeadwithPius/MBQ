package com.MBQ.controller;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.MBQ.dto.QueueDtos.PatientRegistrationRequest;
import com.MBQ.dto.QueueDtos.PatientResponse;
import com.MBQ.model.Patient;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.PatientRepository;
import com.MBQ.service.QueueService;
import com.MBQ.service.StaffAccessService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final QueueService queueService;
    private final PatientRepository patientRepository;
    private final StaffAccessService staffAccess;

    public PatientController(
            QueueService queueService,
            PatientRepository patientRepository,
            StaffAccessService staffAccess) {
        this.queueService = queueService;
        this.patientRepository = patientRepository;
        this.staffAccess = staffAccess;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public List<PatientResponse> listPatients() {
        StaffUser staff = staffAccess.currentStaff();

        List<Patient> patients = staffAccess.isAdmin(staff)
                ? patientRepository.findAll(Sort.by(Sort.Direction.DESC, "id"))
                : patientRepository.findByFacility_IdOrderByIdDesc(
                        staffAccess.facilityIdOf(staff));

        return patients.stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PatientResponse registerPatient(
            @Valid @RequestBody PatientRegistrationRequest request) {
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
                patient.getFacility() == null
                        ? null
                        : patient.getFacility().getId());
    }
}
