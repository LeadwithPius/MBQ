package com.MBQ.service;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.LabDtos.CancelLabRequest;
import com.MBQ.dto.LabDtos.CompleteLabRequest;
import com.MBQ.dto.LabDtos.CreateLabRequest;
import com.MBQ.dto.LabDtos.LabRequestResponse;
import com.MBQ.model.Consultation;
import com.MBQ.model.ConsultationStatus;
import com.MBQ.model.LabPriority;
import com.MBQ.model.LabRequest;
import com.MBQ.model.LabRequestStatus;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.ConsultationRepository;
import com.MBQ.repository.LabRequestRepository;
import com.MBQ.repository.PatientRepository;

/**
 * Laboratory workflow (brief Workflow B): a doctor requests a test from a draft
 * consultation, lab staff start and complete it, and the doctor sees the result.
 * Completed results are immutable.
 */
@Service
public class LabService {

    static final Set<LabRequestStatus> OPEN =
            EnumSet.of(LabRequestStatus.REQUESTED, LabRequestStatus.IN_PROGRESS);

    private final LabRequestRepository labRequests;
    private final ConsultationRepository consultations;
    private final PatientRepository patients;
    private final StaffAccessService staffAccess;

    public LabService(
            LabRequestRepository labRequests,
            ConsultationRepository consultations,
            PatientRepository patients,
            StaffAccessService staffAccess) {
        this.labRequests = labRequests;
        this.consultations = consultations;
        this.patients = patients;
        this.staffAccess = staffAccess;
    }

    @Transactional
    public LabRequestResponse request(CreateLabRequest request) {
        Consultation consultation = consultations.findById(request.consultationId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Consultation not found"));
        staffAccess.requireTicketDepartmentAccess(consultation.getQueueTicket());

        StaffUser doctor = staffAccess.currentStaff();
        if (!staffAccess.isAdmin(doctor)
                && !consultation.getDoctor().getId().equals(doctor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the doctor running this consultation can request tests");
        }
        if (consultation.getStatus() != ConsultationStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tests can only be requested while the consultation is open");
        }

        String testName = request.testName().trim();
        if (labRequests.existsByConsultation_IdAndTestNameIgnoreCaseAndStatusIn(
                consultation.getId(), testName, OPEN)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "\"" + testName + "\" is already requested for this consultation");
        }

        LabRequest lab = new LabRequest();
        lab.setConsultation(consultation);
        lab.setPatient(consultation.getPatient());
        lab.setFacility(consultation.getQueueTicket().getDepartment().getFacility());
        lab.setTestName(testName);
        lab.setClinicalNotes(trimToNull(request.clinicalNotes()));
        lab.setPriority(request.priority() == null ? LabPriority.ROUTINE : request.priority());
        lab.setStatus(LabRequestStatus.REQUESTED);
        lab.setRequestedBy(doctor);
        lab.setRequestedAt(OffsetDateTime.now());
        return toResponse(labRequests.saveAndFlush(lab));
    }

    @Transactional
    public LabRequestResponse start(Long id) {
        LabRequest lab = lockAndAuthorize(id);
        if (lab.getStatus() != LabRequestStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a newly requested test can be started (current status: " + lab.getStatus() + ")");
        }
        lab.setStatus(LabRequestStatus.IN_PROGRESS);
        lab.setStartedBy(staffAccess.currentStaff());
        lab.setStartedAt(OffsetDateTime.now());
        return toResponse(labRequests.saveAndFlush(lab));
    }

    @Transactional
    public LabRequestResponse complete(Long id, CompleteLabRequest request) {
        LabRequest lab = lockAndAuthorize(id);
        if (!OPEN.contains(lab.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This test is already " + lab.getStatus().name().toLowerCase(Locale.ROOT)
                    + "; results cannot be changed");
        }
        if (request.resultText() == null || request.resultText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A result is required");
        }
        StaffUser actor = staffAccess.currentStaff();
        OffsetDateTime now = OffsetDateTime.now();
        if (lab.getStartedAt() == null) {
            lab.setStartedBy(actor);
            lab.setStartedAt(now);
        }
        lab.setStatus(LabRequestStatus.COMPLETED);
        lab.setResultText(request.resultText().trim());
        lab.setResultNotes(trimToNull(request.resultNotes()));
        lab.setAbnormal(Boolean.TRUE.equals(request.abnormal()));
        lab.setCompletedBy(actor);
        lab.setCompletedAt(now);
        return toResponse(labRequests.saveAndFlush(lab));
    }

    @Transactional
    public LabRequestResponse cancel(Long id, CancelLabRequest request) {
        LabRequest lab = lockAndAuthorize(id);
        if (!OPEN.contains(lab.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only open tests can be cancelled");
        }
        lab.setStatus(LabRequestStatus.CANCELLED);
        lab.setCancelledReason(request.reason().trim());
        return toResponse(labRequests.saveAndFlush(lab));
    }

    @Transactional(readOnly = true)
    public List<LabRequestResponse> byConsultation(Long consultationId) {
        Consultation consultation = consultations.findById(consultationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Consultation not found"));
        staffAccess.requirePatientFacilityAccess(consultation.getPatient());
        return labRequests.findByConsultation_IdOrderByRequestedAtAscIdAsc(consultationId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LabRequestResponse> byPatient(Long patientId) {
        var patient = patients.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        staffAccess.requirePatientFacilityAccess(patient);
        return labRequests.findTop100ByPatient_IdOrderByRequestedAtDescIdDesc(patientId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * The lab worklist: open tests by default (urgent first, then oldest first),
     * or completed/cancelled ones when asked. Facility-scoped for non-admins.
     */
    @Transactional(readOnly = true)
    public List<LabRequestResponse> worklist(LabRequestStatus status) {
        Set<LabRequestStatus> statuses = status == null ? OPEN : EnumSet.of(status);
        StaffUser staff = staffAccess.currentStaff();
        Long facilityId = staffAccess.facilityIdOf(staff);
        List<LabRequest> rows = facilityId == null
                ? labRequests.findTop200ByStatusInOrderByRequestedAtAscIdAsc(statuses)
                : labRequests.findTop200ByFacility_IdAndStatusInOrderByRequestedAtAscIdAsc(facilityId, statuses);
        return rows.stream()
                .sorted((a, b) -> {
                    if (status != null && !OPEN.contains(status)) {
                        // History views: newest first.
                        return b.getRequestedAt().compareTo(a.getRequestedAt());
                    }
                    int urgent = Boolean.compare(b.getPriority() == LabPriority.URGENT,
                            a.getPriority() == LabPriority.URGENT);
                    return urgent != 0 ? urgent : a.getRequestedAt().compareTo(b.getRequestedAt());
                })
                .map(this::toResponse)
                .toList();
    }

    private LabRequest lockAndAuthorize(Long id) {
        LabRequest lab = labRequests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab request not found"));
        staffAccess.requireFacilityAccess(lab.getFacility().getId());
        return lab;
    }

    LabRequestResponse toResponse(LabRequest r) {
        var ticket = r.getConsultation().getQueueTicket();
        return new LabRequestResponse(
                r.getId(),
                r.getConsultation().getId(),
                r.getPatient().getId(),
                r.getPatient().getName(),
                ticket == null ? null : ticket.getTicketNumber(),
                ticket == null || ticket.getDepartment() == null ? null : ticket.getDepartment().getName(),
                r.getTestName(),
                r.getClinicalNotes(),
                r.getPriority(),
                r.getStatus(),
                nameOf(r.getRequestedBy()),
                r.getRequestedAt(),
                nameOf(r.getStartedBy()),
                r.getStartedAt(),
                r.getResultText(),
                r.getResultNotes(),
                r.getAbnormal(),
                nameOf(r.getCompletedBy()),
                r.getCompletedAt(),
                r.getCancelledReason());
    }

    private static String nameOf(StaffUser staff) {
        return staff == null ? null : staff.getName();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
