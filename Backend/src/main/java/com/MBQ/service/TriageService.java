package com.MBQ.service;

import com.MBQ.dto.TriageDtos;
import com.MBQ.model.*;
import com.MBQ.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class TriageService {

    private final TriageObservationRepository observations;
    private final ClinicalRiskAssessmentRepository assessments;
    private final PatientRepository patients;
    private final StaffUserRepository staffUsers;
    private final QueueTicketRepository tickets;
    private final ClinicalRiskAssessmentService riskService;
    private final JdbcTemplate jdbc;

    public TriageService(
            TriageObservationRepository observations,
            ClinicalRiskAssessmentRepository assessments,
            PatientRepository patients,
            StaffUserRepository staffUsers,
            QueueTicketRepository tickets,
            ClinicalRiskAssessmentService riskService,
            JdbcTemplate jdbc) {

        this.observations = observations;
        this.assessments = assessments;
        this.patients = patients;
        this.staffUsers = staffUsers;
        this.tickets = tickets;
        this.riskService = riskService;
        this.jdbc = jdbc;
    }

    @Transactional
    public TriageDtos.AssessmentResponse create(
            TriageDtos.CreateObservationRequest request) {

        validate(request);

        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Patient not found"));

        StaffUser staff = staffUsers.findById(request.assessedById())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Staff user not found"));

        QueueTicket ticket = null;

        if (request.queueTicketId() != null) {
            ticket = tickets.findById(request.queueTicketId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Queue ticket not found"));
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
                request.symptoms() == null
                        ? ""
                        : request.symptoms().trim());

        observation.setNotes(request.notes());

        observation = observations.save(observation);

        // Produce an explainable, preliminary recommendation.
        ClinicalRiskAssessmentService.RuleResult result =
                riskService.assess(observation);

        ClinicalRiskAssessment assessment =
                riskService.toAssessment(observation, result);

        assessment = assessments.save(assessment);

        /*
         * Escalate queue priority when the rules recommend a higher level.
         * Never automatically downgrade an existing priority.
         *
         * The recommendation remains pending clinician review.
         */
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

            jdbc.update(
                    sql,
                    result.priority().name(),
                    ticket.getId(),
                    result.priority().name());
        }

        return toAssessmentResponse(assessment);
    }

    @Transactional(readOnly = true)
    public TriageDtos.ObservationResponse getObservation(Long id) {

        TriageObservation observation = observations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Observation not found"));

        return toObservationResponse(observation);
    }

    @Transactional(readOnly = true)
    public List<TriageDtos.ObservationResponse> getPatientObservations(
            Long patientId) {

        if (!patients.existsById(patientId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Patient not found");
        }

        return observations
                .findByPatient_IdOrderByMeasuredAtDesc(patientId)
                .stream()
                .map(this::toObservationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TriageDtos.AssessmentResponse> getAssessments(
            Long observationId) {

        if (!observations.existsById(observationId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Observation not found");
        }

        return assessments
                .findByObservation_IdOrderByCreatedAtDesc(observationId)
                .stream()
                .map(this::toAssessmentResponse)
                .toList();
    }

    @Transactional
    public TriageDtos.AssessmentResponse review(
            Long assessmentId,
            TriageDtos.ReviewRequest request) {

        if (request == null
                || request.reviewedById() == null
                || request.reviewStatus() == null
                || request.clinicianNotes() == null
                || request.clinicianNotes().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reviewer, review status and clinician notes are required");
        }

        ClinicalRiskAssessment assessment =
                assessments.findById(assessmentId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Assessment not found"));

        StaffUser reviewer = staffUsers.findById(request.reviewedById())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Reviewing staff user not found"));

        if (request.reviewStatus()
                == ClinicalRiskAssessment.ReviewStatus.OVERRIDDEN
                && request.confirmedPriority() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "confirmedPriority is required when overriding a recommendation");
        }

        assessment.setReviewedBy(reviewer);
        assessment.setReviewStatus(request.reviewStatus());
        assessment.setClinicianNotes(request.clinicianNotes().trim());
        assessment.setReviewedAt(OffsetDateTime.now());

        if (request.reviewStatus()
                == ClinicalRiskAssessment.ReviewStatus.OVERRIDDEN) {

            assessment.setRecommendedPriority(
                    request.confirmedPriority());
        }

        return toAssessmentResponse(
                assessments.save(assessment));
    }

    private void validate(
            TriageDtos.CreateObservationRequest request) {

        if (request == null
                || request.patientId() == null
                || request.assessedById() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "patientId and assessedById are required");
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
                && (request.heartRate() < 1
                || request.heartRate() > 300)) {

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
                assessment.getClinicianNotes(),
                assessment.getCreatedAt(),
                assessment.getReviewedAt());
    }
}