package com.MBQ.dto;

import com.MBQ.model.ClinicalRiskAssessment;
import com.MBQ.model.PriorityLevel;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class TriageDtos {
    private TriageDtos() {}

    public record CreateObservationRequest(
        Long patientId, Long queueTicketId, Long assessedById, OffsetDateTime measuredAt,
        Integer systolicBp, Integer diastolicBp, Integer heartRate, Integer respiratoryRate,
        BigDecimal temperatureC, Integer spo2Percent, String symptoms, String notes) {}

    public record ObservationResponse(
        Long id, Long patientId, Long queueTicketId, Long assessedById,
        OffsetDateTime measuredAt, Integer systolicBp, Integer diastolicBp,
        Integer heartRate, Integer respiratoryRate, BigDecimal temperatureC,
        Integer spo2Percent, String symptoms, String notes, OffsetDateTime createdAt) {}

    public record AssessmentResponse(
        Long id, Long observationId, PriorityLevel recommendedPriority, List<String> findings,
        String assessmentSource, String modelVersion, boolean clinicianReviewRequired,
        ClinicalRiskAssessment.ReviewStatus reviewStatus, String clinicianNotes,
        OffsetDateTime createdAt, OffsetDateTime reviewedAt) {}

    public record ReviewRequest(
        Long reviewedById, ClinicalRiskAssessment.ReviewStatus reviewStatus,
        PriorityLevel confirmedPriority, String clinicianNotes) {}
}
