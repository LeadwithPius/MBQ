package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import com.MBQ.dto.BloodRequestDtos.CreateBloodRequest;
import com.MBQ.model.*;
import com.MBQ.repository.BloodRequestRepository;
import com.MBQ.repository.BloodStockRepository;
import com.MBQ.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BloodRequestServiceTest {
    @Mock private BloodRequestRepository requests;
    @Mock private PatientRepository patients;
    @Mock private BloodStockRepository stocks;
    @Mock private StaffAccessService staffAccess;
    @Mock private BloodInventoryService inventory;

    private BloodRequestService service;
    private StaffUser doctor;
    private Facility facility;
    private Patient patient;

    @BeforeEach
    void setUp() {
        service = new BloodRequestService(requests, patients, stocks, staffAccess, inventory);
        facility = new Facility();
        facility.setId(7L);
        doctor = new StaffUser();
        doctor.setId(12L);
        doctor.setName("Dr Test");
        doctor.setRole(Role.DOCTOR);
        patient = new Patient();
        patient.setId(33L);
        patient.setName("Patient Test");
        patient.setBloodGroup(BloodGroup.O_POS);
        patient.setFacility(facility);
    }

    @Test
    void doctorCanCreateFacilityScopedBloodRequest() {
        when(staffAccess.currentStaff()).thenReturn(doctor);
        when(staffAccess.facilityIdOf(doctor)).thenReturn(7L);
        when(patients.findById(33L)).thenReturn(Optional.of(patient));
        when(requests.save(any(BloodRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CreateBloodRequest(33L, 2, PriorityLevel.URGENT, "Urgent transfusion"));

        assertEquals(33L, response.patientId());
        assertEquals(BloodGroup.O_POS, response.bloodGroup());
        assertEquals(2, response.units());
        assertEquals(BloodRequestStatus.PENDING, response.status());
        assertEquals("Dr Test", response.requestedBy());
        verify(staffAccess).requirePatientFacilityAccess(patient);
    }

    @Test
    void requestIsPersistedAsPendingAndLinkedToFacility() {
        when(staffAccess.currentStaff()).thenReturn(doctor);
        when(staffAccess.facilityIdOf(doctor)).thenReturn(7L);
        when(patients.findById(33L)).thenReturn(Optional.of(patient));
        when(requests.save(any(BloodRequest.class))).thenAnswer(invocation -> {
            BloodRequest saved = invocation.getArgument(0);
            assertNotNull(saved.getFacility());
            assertEquals(7L, saved.getFacility().getId());
            assertEquals(BloodRequestStatus.PENDING, saved.getStatus());
            return saved;
        });

        service.create(new CreateBloodRequest(33L, 1, PriorityLevel.NORMAL, "Cross-match and transfusion"));

        verify(requests).save(any(BloodRequest.class));
    }
}
