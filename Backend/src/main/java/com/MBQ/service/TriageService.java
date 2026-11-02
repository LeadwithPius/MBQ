package com.MBQ.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.TriageDtos;
import com.MBQ.model.ClinicalRiskAssessment;
import com.MBQ.model.Patient;
import com.MBQ.model.PriorityLevel;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.StaffUser;
import com.MBQ.model.TriageObservation;
import com.MBQ.repository.ClinicalRiskAssessmentRepository;
import com.MBQ.repository.PatientRepository;
import com.MBQ.repository.QueueTicketRepository;
import com.MBQ.repository.TriageObservationRepository;

@Service
public class TriageService {

    private final TriageObservationRepository observations;
    private final ClinicalRiskAssessmentRepository assessments;
    private final PatientRepository patients;
    private final QueueTicketRepository tickets;
    private final ClinicalRiskAssessmentService riskService;
    private final JdbcTemplate jdbc;
    private final StaffAccessService staffAccess;

    public TriageService(
            TriageObservationRepository observations,
            ClinicalRiskAssessmentRepository assessments,
            PatientRepository patients,
            QueueTicketRepository tickets,
            ClinicalRiskAssessmentService riskService,
            JdbcTemplate jdbc,
            StaffAccessService staffAccess) {
        this.observations = observations;
        this.assessments = assessments;
        this.patients = patients;
        this.tickets = tickets;
        this.riskService = riskService;
        this.jdbc = jdbc;
        this.staffAccess = staffAccess;
    }

    @Transactional
    public TriageDtos.AssessmentResponse create(
            TriageDtos.CreateObservationRequest request) {
        validate(request);

        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Patient not found"));
        staffAccess.requirePatientFacilityAccess(patient);

        StaffUser staff = staffAccess.currentStaff();
        QueueTicket ticket = null;

        if (request.queueTicketId() != null) {
            ticket = tickets.findById(request.queueTicketId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Queue ticket not found"));

            if (!Objects.equals(ticket.getPatient().getId(), patient.getId())
                    || ticket.getDepartment().getFacility() == null
                    || patient.getFacility() == null
                    || !Objects.equals(
                            ticket.getDepartment().getFacility().getId(),
                            patient.getFacility().getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The queue ticket must belong to this patient and facility");
            }
        }

        TriageObservation observation = new TriageObservation();
        observation.setPatient(patient);
        observation.setQueueTicket(ticket);
        observation.setAssessedBy(staff);
        observation.setMeasuredAt(
                request.measuredAt() == null
                        ? OffsetDateTime.now()
                        : request.measuredAt());
        observation.setSystolicBp(request.systolicBp());
        observation.setDiastolicBp(request.diastolicBp());
        observation.setHeartRate(request.heartRate());
        observation.setRespiratoryRate(request.respiratoryRate());
        observation.setTemperatureC(request.temperatureC());
        observation.setSpo2Percent(request.spo2Percent());
        observation.setSymptoms(
                request.symptoms() == null ? "" : request.symptoms().trim());
        observation.setNotes(trimToNull(request.notes()));

        observation = observations.save(observation);

        ClinicalRiskAssessmentService.RuleResult result =
                riskService.assess(observation);
        ClinicalRiskAssessment assessment =
                riskService.toAssessment(observation, result);
        assessment = assessments.save(assessment);

        if (ticket != null) {
            String sql = """
                UPDATE queue_ticket
                   SET priority_level = ?
                 WHERE id = ?
                   AND CASE priority_level
                         WHEN 'NORMAL' THEN 1
                         WHEN 'URGENT' THEN 2
                         WHEN 'EMERGENCY' THEN 3
                         ELSE 0
                       END
                       < CASE ?
                           WHEN 'NORMAL' THEN 1
                           WHEN 'URGENT' THEN 2
                           WHEN 'EMERGENCY' THEN 3
                           ELSE 0
                         END
                """;
            jdbc.update(sql, result.priority().name(), ticket.getId(),
                    result.priority().name());
        }

        return toAssessmentResponse(assessment);
    }

    @Transactional(readOnly = true)
    public TriageDtos.ObservationResponse getObservation(Long id) {
        TriageObservation observation = observations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Observation not found"));
        staffAccess.requirePatientFacilityAccess(observation.getPatient());
        return toObservationResponse(observation);
    }

    @Transactional(readOnly = true)
    public List<TriageDtos.ObservationResponse> getPatientObservations(
            Long patientId) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Patient not found"));
        staffAccess.requirePatientFacilityAccess(patient);

        return observations.findByPatient_IdOrderByMeasuredAtDesc(patientId)
                .stream()
                .map(this::toObservationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TriageDtos.AssessmentResponse> getAssessments(
            Long observationId) {
        TriageObservation observation = observations.findById(observationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Observation not found"));
        staffAccess.requirePatientFacilityAccess(observation.getPatient());

        return assessments.findByObservation_IdOrderByCreatedAtDesc(observationId)
                .stream()
                .map(this::toAssessmentResponse)
                .toList();
    }

    @Transactional
    public TriageDtos.AssessmentResponse review(
            Long assessmentId,
            TriageDtos.ReviewRequest request) {
        if (request == null
                || request.reviewStatus() == null
                || request.reviewStatus()
                        == ClinicalRiskAssessment.ReviewStatus.PENDING
                || request.clinicianNotes() == null
                || request.clinicianNotes().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A final review decision and clinician notes are required");
        }

        // Pessimistic row lock makes the pending -> final transition atomic.
        ClinicalRiskAssessment assessment = assessments.findByIdForUpdate(assessmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Assessment not found"));

        staffAccess.requirePatientFacilityAccess(
                assessment.getObservation().getPatient());

        if (assessment.getReviewStatus()
                != ClinicalRiskAssessment.ReviewStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This assessment has already been reviewed");
        }

        PriorityLevel confirmedPriority = request.confirmedPriority();
        if (request.reviewStatus() == ClinicalRiskAssessment.ReviewStatus.CONFIRMED) {
            if (confirmedPriority != null
                    && confirmedPriority != assessment.getRecommendedPriority()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "A changed priority must be submitted as OVERRIDDEN");
            }
            confirmedPriority = assessment.getRecommendedPriority();
        } else {
            if (confirmedPriority == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "confirmedPriority is required when overriding a recommendation");
            }
            if (confirmedPriority == assessment.getRecommendedPriority()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Use CONFIRMED when keeping the recommended priority");
            }
        }

        StaffUser reviewer = staffAccess.currentStaff();
        OffsetDateTime reviewedAt = OffsetDateTime.now();
        assessment.setReviewedBy(reviewer);
        assessment.setReviewStatus(request.reviewStatus());
        assessment.setConfirmedPriority(confirmedPriority);
        assessment.setClinicianNotes(request.clinicianNotes().trim());
        assessment.setReviewedAt(reviewedAt);

        // Persist the final decision and immutable audit snapshot in one transaction.
        ClinicalRiskAssessment saved = assessments.save(assessment);
        jdbc.update("""
            INSERT INTO clinical_risk_review_history (
                assessment_id, reviewed_by_id, review_status,
                recommended_priority_snapshot, confirmed_priority,
                clinician_notes, reviewed_at, is_legacy
            ) VALUES (?, ?, ?, ?, ?, ?, ?, FALSE)
            """,
            saved.getId(),
            reviewer.getId(),
            saved.getReviewStatus().name(),
            saved.getRecommendedPriority().name(),
            saved.getConfirmedPriority().name(),
            saved.getClinicianNotes(),
            reviewedAt);

        return toAssessmentResponse(saved);
    }

    private void validate(TriageDtos.CreateObservationRequest request) {
        if (request == null || request.patientId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "patientId is required");
        }

        if (request.systolicBp() != null
                && (request.systolicBp() < 30 || request.systolicBp() > 300)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "systolicBp must be between 30 and 300");
        }
        if (request.diastolicBp() != null
                && (request.diastolicBp() < 20 || request.diastolicBp() > 200)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "diastolicBp must be between 20 and 200");
        }
        if (request.systolicBp() != null
                && request.diastolicBp() != null
                && request.systolicBp() <= request.diastolicBp()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Systolic blood pressure must be greater than diastolic blood pressure");
        }
        if (request.temperatureC() != null
                && (request.temperatureC().doubleValue() < 25
                || request.temperatureC().doubleValue() > 45)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "temperatureC must be between 25 and 45");
        }
        if (request.heartRate() != null
                && (request.heartRate() < 1 || request.heartRate() > 300)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "heartRate must be between 1 and 300");
        }
        if (request.respiratoryRate() != null
                && (request.respiratoryRate() < 1
                || request.respiratoryRate() > 100)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "respiratoryRate must be between 1 and 100");
        }
        if (request.spo2Percent() != null
                && (request.spo2Percent() < 0
                || request.spo2Percent() > 100)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "spo2Percent must be between 0 and 100");
        }
    }

    private TriageDtos.ObservationResponse toObservationResponse(
            TriageObservation observation) {
        return new TriageDtos.ObservationResponse(
                observation.getId(),
                observation.getPatient().getId(),
                observation.getQueueTicket() == null
                        ? null
                        : observation.getQueueTicket().getId(),
                observation.getAssessedBy().getId(),
                observation.getMeasuredAt(),
                observation.getSystolicBp(),
                observation.getDiastolicBp(),
                observation.getHeartRate(),
                observation.getRespiratoryRate(),
                observation.getTemperatureC(),
                observation.getSpo2Percent(),
                observation.getSymptoms(),
                observation.getNotes(),
                observation.getCreatedAt());
    }

    private TriageDtos.AssessmentResponse toAssessmentResponse(
            ClinicalRiskAssessment assessment) {
        List<String> findings =
                assessment.getFindings() == null
                        || assessment.getFindings().isBlank()
                        ? List.of()
                        : assessment.getFindings().lines().toList();

        return new TriageDtos.AssessmentResponse(
                assessment.getId(),
                assessment.getObservation().getId(),
                assessment.getRecommendedPriority(),
                findings,
                assessment.getAssessmentSource(),
                assessment.getModelVersion(),
                assessment.isClinicianReviewRequired(),
                assessment.getReviewStatus(),
                assessment.getConfirmedPriority(),
                assessment.getClinicianNotes(),
                assessment.getCreatedAt(),
                assessment.getReviewedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
