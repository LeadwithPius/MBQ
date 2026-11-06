package com.MBQ.dto;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.MBQ.model.ConsultationStatus;

public final class ConsultationDtos {

    private ConsultationDtos() {
    }

    public record SaveConsultationRequest(
            @NotNull Long queueTicketId,
            @Size(max = 10000) String presentingComplaint,
            @Size(max = 20000) String historyOfPresentIllness,
            @Size(max = 20000) String medicalHistory,
            @Size(max = 20000) String examinationFindings,
            @Size(max = 10000) String diagnosis,
            @Size(max = 20000) String treatmentPlan,
            @Size(max = 20000) String prescriptions,
            @Size(max = 20000) String investigations,
            @Size(max = 10000) String followUp) {
    }

    public record ConsultationResponse(
            Long id,
            Long patientId,
            String patientName,
            Long queueTicketId,
            String ticketNumber,
            Long doctorId,
            String doctorName,
            String presentingComplaint,
            String historyOfPresentIllness,
            String medicalHistory,
            String examinationFindings,
            String diagnosis,
            String treatmentPlan,
            String prescriptions,
            String investigations,
            String followUp,
            ConsultationStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime completedAt) {
    }
}
