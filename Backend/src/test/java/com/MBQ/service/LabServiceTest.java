package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.LabDtos.CompleteLabRequest;
import com.MBQ.dto.LabDtos.CreateLabRequest;
import com.MBQ.dto.LabDtos.LabRequestResponse;
import com.MBQ.model.Consultation;
import com.MBQ.model.ConsultationStatus;
import com.MBQ.model.Department;
import com.MBQ.model.Facility;
import com.MBQ.model.LabPriority;
import com.MBQ.model.LabRequest;
import com.MBQ.model.LabRequestStatus;
import com.MBQ.model.Patient;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.ConsultationRepository;
import com.MBQ.repository.LabRequestRepository;
import com.MBQ.repository.PatientRepository;

/** Laboratory workflow rules. Repositories are Mockito mocks; no database. */
@ExtendWith(MockitoExtension.class)
class LabServiceTest {

    @Mock private LabRequestRepository labRequests;
    @Mock private ConsultationRepository consultations;
    @Mock private PatientRepository patients;
    @Mock private StaffAccessService staffAccess;

    private LabService service;
    private StaffUser doctor;
    private StaffUser labTech;
    private Consultation consultation;

    @BeforeEach
    void setUp() {
        service = new LabService(labRequests, consultations, patients, staffAccess);

        Facility facility = new Facility();
        facility.setId(1L);
        Department opd = new Department();
        opd.setId(1L);
        opd.setName("OPD");
        opd.setFacility(facility);
        Patient patient = new Patient();
        patient.setId(7L);
        patient.setName("Mary Banda");
        patient.setFacility(facility);
        QueueTicket ticket = new QueueTicket();
        ticket.setId(3L);
        ticket.setTicketNumber("F1-D1-0003");
        ticket.setDepartment(opd);
        ticket.setPatient(patient);

        doctor = new StaffUser();
        doctor.setId(10L);
        doctor.setName("Dr Phiri");
        labTech = new StaffUser();
        labTech.setId(20L);
        labTech.setName("Lab Tech");

        consultation = new Consultation();
        consultation.setId(5L);
        consultation.setPatient(patient);
        consultation.setQueueTicket(ticket);
        consultation.setDoctor(doctor);
        consultation.setStatus(ConsultationStatus.DRAFT);
    }

    private LabRequest openRequest(LabRequestStatus status) {
        LabRequest lab = new LabRequest();
        lab.setId(40L);
        lab.setConsultation(consultation);
        lab.setPatient(consultation.getPatient());
        lab.setFacility(consultation.getQueueTicket().getDepartment().getFacility());
        lab.setTestName("Full blood count");
        lab.setPriority(LabPriority.ROUTINE);
        lab.setStatus(status);
        lab.setRequestedBy(doctor);
        lab.setRequestedAt(OffsetDateTime.now());
        return lab;
    }

    @Test
    void request_fromOpenConsultation_isSavedAsRequestedForThePatientsFacility() {
        when(consultations.findById(5L)).thenReturn(Optional.of(consultation));
        when(staffAccess.currentStaff()).thenReturn(doctor);
        when(labRequests.existsByConsultation_IdAndTestNameIgnoreCaseAndStatusIn(eq(5L), eq("Malaria RDT"), anyCollection()))
                .thenReturn(false);
        when(labRequests.saveAndFlush(any(LabRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LabRequestResponse response = service.request(
                new CreateLabRequest(5L, "  Malaria RDT ", "Fever 3 days", LabPriority.URGENT));

        assertEquals("Malaria RDT", response.testName());
        assertEquals(LabRequestStatus.REQUESTED, response.status());
        assertEquals(LabPriority.URGENT, response.priority());
        assertEquals("Mary Banda", response.patientName());
        assertEquals("Dr Phiri", response.requestedByName());
    }

    @Test
    void request_duplicateOpenTest_isRejectedWith409() {
        when(consultations.findById(5L)).thenReturn(Optional.of(consultation));
        when(staffAccess.currentStaff()).thenReturn(doctor);
        when(labRequests.existsByConsultation_IdAndTestNameIgnoreCaseAndStatusIn(eq(5L), eq("Malaria RDT"), anyCollection()))
                .thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.request(new CreateLabRequest(5L, "Malaria RDT", null, null)));

        assertEquals(409, error.getStatusCode().value());
        verify(labRequests, never()).saveAndFlush(any());
    }

    @Test
    void request_onCompletedConsultation_isRejected() {
        consultation.setStatus(ConsultationStatus.COMPLETED);
        when(consultations.findById(5L)).thenReturn(Optional.of(consultation));
        when(staffAccess.currentStaff()).thenReturn(doctor);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.request(new CreateLabRequest(5L, "Malaria RDT", null, null)));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void request_byADifferentDoctor_isForbidden() {
        StaffUser otherDoctor = new StaffUser();
        otherDoctor.setId(11L);
        when(consultations.findById(5L)).thenReturn(Optional.of(consultation));
        when(staffAccess.currentStaff()).thenReturn(otherDoctor);
        when(staffAccess.isAdmin(otherDoctor)).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.request(new CreateLabRequest(5L, "Malaria RDT", null, null)));

        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void complete_recordsResultAndWhoEnteredIt() {
        LabRequest lab = openRequest(LabRequestStatus.IN_PROGRESS);
        when(labRequests.findByIdForUpdate(40L)).thenReturn(Optional.of(lab));
        when(staffAccess.currentStaff()).thenReturn(labTech);
        when(labRequests.saveAndFlush(any(LabRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LabRequestResponse response = service.complete(40L,
                new CompleteLabRequest("Hb 7.2 g/dL", "Low", true));

        assertEquals(LabRequestStatus.COMPLETED, response.status());
        assertEquals("Hb 7.2 g/dL", response.resultText());
        assertEquals(Boolean.TRUE, response.abnormal());
        assertEquals("Lab Tech", response.completedByName());
    }

    @Test
    void complete_alreadyCompletedResult_cannotBeOverwritten() {
        LabRequest lab = openRequest(LabRequestStatus.COMPLETED);
        lab.setResultText("Hb 12 g/dL");
        when(labRequests.findByIdForUpdate(40L)).thenReturn(Optional.of(lab));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.complete(40L, new CompleteLabRequest("Hb 7 g/dL", null, true)));

        assertEquals(409, error.getStatusCode().value());
        assertEquals("Hb 12 g/dL", lab.getResultText());
        verify(labRequests, never()).saveAndFlush(any());
    }
}
