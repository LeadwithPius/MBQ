package com.MBQ.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.MBQ.model.BloodGroup;
import com.MBQ.model.DonationStatus;

public final class DonationDtos {

    private DonationDtos() {}

    public record RecordDonationRequest(
            @NotNull Long donorId,
            @Min(1) @Max(2) int units) {}

    /** passed=true sends the units into stock as a batch awaiting verification. */
    public record ScreenDonationRequest(
            @NotNull Boolean passed,
            @Size(max = 1000) String notes) {}

    public record DonationResponse(
            Long id,
            Long donorId,
            String donorName,
            BloodGroup bloodGroup,
            int units,
            OffsetDateTime collectedAt,
            String collectedByName,
            DonationStatus status,
            String screenedByName,
            OffsetDateTime screenedAt,
            String screeningNotes,
            Long batchId,
            String batchReference,
            LocalDate batchExpiryDate,
            String batchVerificationStatus) {}
}
