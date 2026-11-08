package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.QueueDtos.CreateTicketRequest;
import com.MBQ.dto.QueueDtos.TicketResponse;
import com.MBQ.dto.QueueDtos.UpdatePriorityRequest;
import com.MBQ.model.Department;
import com.MBQ.model.Facility;
import com.MBQ.model.Patient;
import com.MBQ.model.PriorityLevel;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.QueueTicketStatusHistory;
import com.MBQ.model.StaffUser;
import com.MBQ.model.TicketStatus;
import com.MBQ.repository.DepartmentRepository;
import com.MBQ.repository.FacilityRepository;
import com.MBQ.repository.PatientRepository;
import com.MBQ.repository.QueueTicketRepository;
import com.MBQ.repository.QueueTicketStatusHistoryRepository;

/**
 * Unit tests for the queue rules added in fix/demo-blockers.
 * No database: the repositories are replaced by Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock private PatientRepository patientRepository;
    @Mock private FacilityRepository facilityRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private QueueTicketRepository queueTicketRepository;
    @Mock private QueueTicketStatusHistoryRepository historyRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private StaffAccessService staffAccess;

    private QueueService queueService;
    private Department opd;
    private Patient patient;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(
                patientRepository, facilityRepository, departmentRepository,
                queueTicketRepository, historyRepository, jdbcTemplate, staffAccess);

        Facility facility = new Facility();
        facility.setId(1L);

        opd = new Department();
        opd.setId(10L);
        opd.setName("OPD");
        opd.setFacility(facility);

        patient = new Patient();
        patient.setId(100L);
        patient.setName("Mwaba Banda");
        patient.setFacility(facility);
    }

    private QueueTicket ticket(TicketStatus status, PriorityLevel priority) {
        QueueTicket ticket = new QueueTicket();
        ticket.setId(1L);
        ticket.setTicketNumber("F1-OPD-20261104-0001");
        ticket.setPatient(patient);
        ticket.setDepartment(opd);
        ticket.setStatus(status);
        ticket.setPriorityLevel(priority);
        ticket.setTimeIssued(LocalDateTime.of(2026, 11, 4, 9, 0));
        return ticket;
    }

    @Test
    void updatePriority_waitingTicket_changesPriorityAndRecordsHistory() {
        QueueTicket waiting = ticket(TicketStatus.WAITING, PriorityLevel.NORMAL);
        StaffUser nurse = new StaffUser();
        nurse.setId(5L);

        when(queueTicketRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(waiting));
        when(queueTicketRepository.saveAndFlush(waiting)).thenReturn(waiting);
        when(staffAccess.currentStaff()).thenReturn(nurse);

        TicketResponse response = queueService.updateTicketPriority(
                1L, new UpdatePriorityRequest(PriorityLevel.EMERGENCY, "Severe bleeding", true));

        assertEquals(PriorityLevel.EMERGENCY, response.priorityLevel());
        assertEquals(TicketStatus.WAITING, response.status());
        assertEquals(true, response.requiresBlood());
        verify(historyRepository).save(any(QueueTicketStatusHistory.class));
    }

    @Test
    void updatePriority_completedTicket_isRejectedWith409() {
        QueueTicket completed = ticket(TicketStatus.COMPLETED, PriorityLevel.NORMAL);
        when(queueTicketRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(completed));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> queueService.updateTicketPriority(
                        1L, new UpdatePriorityRequest(PriorityLevel.URGENT, null, null)));

        assertEquals(409, error.getStatusCode().value());
        assertEquals(PriorityLevel.NORMAL, completed.getPriorityLevel());
        verify(queueTicketRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTicket_patientWithActiveTicket_isRejectedWith409() {
        when(patientRepository.findById(100L)).thenReturn(Optional.of(patient));
        when(departmentRepository.findById(10L)).thenReturn(Optional.of(opd));
        when(queueTicketRepository.existsByPatient_IdAndStatusIn(eq(100L), anyCollection()))
                .thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> queueService.createTicket(
                        new CreateTicketRequest(100L, 10L, PriorityLevel.NORMAL, null, false)));

        assertEquals(409, error.getStatusCode().value());
        verify(staffAccess).requireDepartmentAssignmentAccess(opd);
        // No ticket number may be used up for a rejected request.
        verifyNoInteractions(jdbcTemplate);
    }
}
