package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.MBQ.dto.ConsultationDtos.SaveConsultationRequest;
import com.MBQ.model.Consultation;
import com.MBQ.model.ConsultationStatus;
import com.MBQ.model.Department;
import com.MBQ.model.Facility;
import com.MBQ.model.Patient;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.StaffUser;
import com.MBQ.model.TicketStatus;
import com.MBQ.repository.ConsultationRepository;
import com.MBQ.repository.PatientRepository;
import com.MBQ.repository.QueueTicketRepository;

@ExtendWith(MockitoExtension.class)
class ConsultationServiceTest {

    @Mock private ConsultationRepository consultations;
    @Mock private QueueTicketRepository tickets;
    @Mock private PatientRepository patients;
    @Mock private StaffAccessService staffAccess;
    @Mock private QueueService queueService;

    private ConsultationService service;
    private StaffUser doctor;
    private QueueTicket ticket;

    @BeforeEach
    void setUp() {
        service = new ConsultationService(
                consultations, tickets, patients, staffAccess, queueService);

        Facility facility = new Facility();
        facility.setId(1L);
        Department department = new Department();
        department.setId(10L);
        department.setFacility(facility);

        doctor = new StaffUser();
        doctor.setId(7L);
        doctor.setName("Dr Test");

        Patient patient = new Patient();
        patient.setId(100L);
        patient.setName("Patient Test");
        patient.setFacility(facility);

        ticket = new QueueTicket();
        ticket.setId(20L);
        ticket.setTicketNumber("OPD-0001");
        ticket.setPatient(patient);
        ticket.setDepartment(department);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
    }

    @Test
    void saveDraft_linksConsultationToPatientTicketAndDoctor() {
        when(tickets.findByIdForUpdate(20L)).thenReturn(Optional.of(ticket));
        when(staffAccess.currentStaff()).thenReturn(doctor);
        when(consultations.findByQueueTicket_Id(20L)).thenReturn(Optional.empty());
        when(consultations.saveAndFlush(any(Consultation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.saveDraft(new SaveConsultationRequest(
                20L, "Fever", "Two days of fever", null, "Temp elevated",
                "Febrile illness", "Review and manage symptoms", null, null, "Review in 48 hours"));

        assertEquals(100L, response.patientId());
        assertEquals(20L, response.queueTicketId());
        assertEquals(7L, response.doctorId());
        assertEquals(ConsultationStatus.DRAFT, response.status());
        verify(staffAccess).requireTicketDepartmentAccess(ticket);
    }

    @Test
    void complete_finalizesConsultationAndCompletesQueueTicket() {
        Consultation consultation = new Consultation();
        consultation.setPatient(ticket.getPatient());
        consultation.setQueueTicket(ticket);
        consultation.setDoctor(doctor);
        consultation.setPresentingComplaint("Fever");
        consultation.setDiagnosis("Febrile illness");
        consultation.setTreatmentPlan("Review and manage symptoms");

        // An ID marks this as an existing draft owned by this doctor.
        consultation.setId(55L);

        when(consultations.findById(55L)).thenReturn(Optional.of(consultation));
        when(staffAccess.currentStaff()).thenReturn(doctor);
        when(staffAccess.isAdmin(doctor)).thenReturn(false);
        when(consultations.saveAndFlush(consultation)).thenReturn(consultation);

        var response = service.complete(55L);

        assertEquals(ConsultationStatus.COMPLETED, response.status());
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
        verify(queueService).updateTicketStatus(any(Long.class), any());
    }
}
