package com.MBQ.service;

import com.MBQ.dto.PatientResponse;
import com.MBQ.dto.RegisterPatientRequest;
import com.MBQ.exception.ResourceNotFoundException;
import com.MBQ.model.Facility;
import com.MBQ.model.Patient;
import com.MBQ.repository.FacilityRepository;
import com.MBQ.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Patient registration and lookup (FR1). */
@Service
public class PatientService {

    private final PatientRepository patients;
    private final FacilityRepository facilities;

    public PatientService(PatientRepository patients, FacilityRepository facilities) {
        this.patients = patients;
        this.facilities = facilities;
    }

    @Transactional
    public PatientResponse register(RegisterPatientRequest request) {
        Patient patient = new Patient();
        patient.setName(request.name().trim());
        patient.setAge(request.age());
        patient.setSex(request.sex().toUpperCase(Locale.ROOT));   // store "MALE"/"FEMALE" consistently
        patient.setContact(blankToNull(request.contact()));
        patient.setBloodGroup(request.bloodGroup());

        if (request.facilityId() != null) {
            Facility facility = facilities.findById(request.facilityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility", request.facilityId()));
            patient.setFacility(facility);
        }

        return PatientResponse.from(patients.save(patient));
    }

    @Transactional(readOnly = true)
    public PatientResponse getById(Long id) {
        return patients.findById(id)
                .map(PatientResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    /** With a name: up to 50 matches A-Z. Without: the 50 most recently registered. */
    @Transactional(readOnly = true)
    public List<PatientResponse> search(String name) {
        List<Patient> found = (name == null || name.isBlank())
                ? patients.findTop50ByOrderByIdDesc()
                : patients.findTop50ByNameContainingIgnoreCaseOrderByNameAsc(name.trim());
        return found.stream().map(PatientResponse::from).toList();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
