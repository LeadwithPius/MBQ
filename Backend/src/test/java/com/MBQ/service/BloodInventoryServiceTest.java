package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.BloodInventoryDtos.IssueRequest;
import com.MBQ.model.BloodGroup;
import com.MBQ.model.BloodStock;
import com.MBQ.model.Facility;
import com.MBQ.model.Patient;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.BloodInventoryOperationRepository;
import com.MBQ.repository.BloodStockBatchRepository;
import com.MBQ.repository.BloodStockMovementRepository;
import com.MBQ.repository.BloodStockRepository;
import com.MBQ.repository.PatientRepository;

/** Workflow D: issuing blood for a named patient must be ABO/RhD compatible. */
@ExtendWith(MockitoExtension.class)
class BloodInventoryServiceTest {

    @Mock private BloodStockRepository stocks;
    @Mock private BloodStockBatchRepository batches;
    @Mock private BloodStockMovementRepository movements;
    @Mock private BloodInventoryOperationRepository operations;
    @Mock private StaffAccessService staffAccess;
    @Mock private JdbcTemplate jdbc;
    @Mock private PatientRepository patients;

    private BloodInventoryService service;
    private BloodStock aPos;
    private Patient patient;

    @BeforeEach
    void setUp() {
        service = new BloodInventoryService(stocks, batches, movements, operations, staffAccess, jdbc, null, patients);
        Facility facility = new Facility();
        facility.setId(1L);
        aPos = new BloodStock();
        aPos.setId(1L);
        aPos.setFacility(facility);
        aPos.setBloodGroup(BloodGroup.A_POS);
        aPos.setUnitsAvailable(10);
        patient = new Patient();
        patient.setId(7L);
        patient.setName("Mary Banda");
        patient.setFacility(facility);
        when(staffAccess.currentStaff()).thenReturn(new StaffUser());
        when(stocks.findByIdForUpdate(1L)).thenReturn(Optional.of(aPos));
        when(patients.findById(7L)).thenReturn(Optional.of(patient));
    }

    @Test
    void issue_incompatibleGroupForPatient_isRejectedBeforeAnyStockChanges() {
        patient.setBloodGroup(BloodGroup.O_NEG);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.issue(new IssueRequest(1L, 1, "Theatre", 7L), "key-1"));

        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("Incompatible blood"));
        verifyNoInteractions(jdbc);
        verify(stocks, never()).save(any());
    }

    @Test
    void issue_unknownPatientGroup_onlyAllowsONegative() {
        patient.setBloodGroup(null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.issue(new IssueRequest(1L, 1, "Emergency", 7L), "key-2"));

        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("O-"));
    }
}
