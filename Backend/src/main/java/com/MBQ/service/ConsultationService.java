package com.MBQ.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.ConsultationDtos.ConsultationResponse;
import com.MBQ.dto.ConsultationDtos.SaveConsultationRequest;
import com.MBQ.model.Consultation;
import com.MBQ.model.ConsultationStatus;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.StaffUser;
import com.MBQ.model.TicketStatus;
import com.MBQ.repository.ConsultationRepository;
import com.MBQ.repository.PatientRepository;
import com.MBQ.repository.QueueTicketRepository;

@Service
public class ConsultationService {

    private final ConsultationRepository consultations;
    private final QueueTicketRepository tickets;
    private final PatientRepository patients;
    private final StaffAccessService staffAccess;

    public ConsultationService(
            ConsultationRepository consultations,
            QueueTicketRepository tickets,
            PatientRepository patients,
            StaffAccessService staffAccess) {
        this.consultations = consultations;
        this.tickets = tickets;
        this.patients = patients;
        this.staffAccess = staffAccess;
    }

    @Transactional
    public ConsultationResponse saveDraft(SaveConsultationRequest request) {
        QueueTicket ticket = tickets.findByIdForUpdate(request.queueTicketId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Queue ticket not found"));
        staffAccess.requireTicketDepartmentAccess(ticket);

        if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A consultation can only be recorded for a patient currently being seen");
        }

        StaffUser doctor = staffAccess.currentStaff();
        Consultation consultation = consultations.findByQueueTicket_Id(ticket.getId())
                .orElseGet(() -> {
                    Consultation created = new Consultation();
                    created.setPatient(ticket.getPatient());
                    created.setQueueTicket(ticket);
                    created.setDoctor(doctor);
                    return created;
                });

        requireDraftOwner(consultation, doctor);
        if (consultation.getStatus() != ConsultationStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This consultation has been completed and is read-only");
        }

        applyRequest(consultation, request);
        consultation.setUpdatedAt(OffsetDateTime.now());
        return toResponse(consultations.saveAndFlush(consultation));
    }

    @Transactional(readOnly = true)
    public ConsultationResponse getByTicket(Long ticketId) {
        QueueTicket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Queue ticket not found"));
        staffAccess.requireTicketDepartmentAccess(ticket);
        return consultations.findByQueueTicket_Id(ticketId)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ConsultationResponse> getByPatient(Long patientId) {
        var patient = patients.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Patient not found"));
        staffAccess.requirePatientFacilityAccess(patient);
        return consultations.findByPatient_IdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ConsultationResponse complete(Long consultationId) {
        Consultation consultation = consultations.findById(consultationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Consultation not found"));
        staffAccess.requireTicketDepartmentAccess(consultation.getQueueTicket());

        StaffUser doctor = staffAccess.currentStaff();
        requireDraftOwner(consultation, doctor);

        if (consultation.getStatus() != ConsultationStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This consultation is already completed");
        }
        if (isBlank(consultation.getPresentingComplaint())
                || isBlank(consultation.getDiagnosis())
                || isBlank(consultation.getTreatmentPlan())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Presenting complaint, diagnosis and treatment plan are required before completing a consultation");
        }

        consultation.setStatus(ConsultationStatus.COMPLETED);
        consultation.setUpdatedAt(OffsetDateTime.now());
        consultation.setCompletedAt(OffsetDateTime.now());
        return toResponse(consultations.saveAndFlush(consultation));
    }

    private void requireDraftOwner(Consultation consultation, StaffUser currentStaff) {
        if (consultation.getId() != null
                && !staffAccess.isAdmin(currentStaff)
                && !consultation.getDoctor().getId().equals(currentStaff.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the doctor who created this draft can edit or complete it");
        }
    }

    private void applyRequest(Consultation consultation, SaveConsultationRequest request) {
        consultation.setPresentingComplaint(valueOrEmpty(request.presentingComplaint()));
        consultation.setHistoryOfPresentIllness(trimToNull(request.historyOfPresentIllness()));
        consultation.setMedicalHistory(trimToNull(request.medicalHistory()));
        consultation.setExaminationFindings(trimToNull(request.examinationFindings()));
        consultation.setDiagnosis(trimToNull(request.diagnosis()));
        consultation.setTreatmentPlan(trimToNull(request.treatmentPlan()));
        consultation.setPrescriptions(trimToNull(request.prescriptions()));
        consultation.setInvestigations(trimToNull(request.investigations()));
        consultation.setFollowUp(trimToNull(request.followUp()));
    }

    private ConsultationResponse toResponse(Consultation c) {
        return new ConsultationResponse(
                c.getId(),
                c.getPatient().getId(),
                c.getPatient().getName(),
                c.getQueueTicket().getId(),
                c.getQueueTicket().getTicketNumber(),
                c.getDoctor().getId(),
                c.getDoctor().getName(),
                c.getPresentingComplaint(),
                c.getHistoryOfPresentIllness(),
                c.getMedicalHistory(),
                c.getExaminationFindings(),
                c.getDiagnosis(),
                c.getTreatmentPlan(),
                c.getPrescriptions(),
                c.getInvestigations(),
                c.getFollowUp(),
                c.getStatus(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getCompletedAt());
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
