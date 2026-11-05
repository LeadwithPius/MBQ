package com.MBQ.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.MBQ.model.BloodGroup;

/** Daily report (FR8) and the department list used by the admin department picker. */
public final class ReportDtos {

    private ReportDtos() {}

    /** A label with a count, e.g. ("EMERGENCY", 3). */
    public record CountRow(String label, long count) {}

    /** Queue activity of one department on the report day. */
    public record DepartmentRow(
            Long departmentId,
            String departmentName,
            long tickets,
            long completed,
            long cancelled,
            long stillWaiting,
            Double averageWaitMinutes) {}

    /** Current stock of one blood group (a snapshot, not historical). */
    public record StockRow(
            Long facilityId,
            BloodGroup bloodGroup,
            int unitsAvailable,
            int lowStockThreshold,
            boolean low) {}

    /**
     * One day's activity across all facilities.
     * Wait time = minutes from ticket issued to the patient first being called (IN_PROGRESS).
     */
    public record DailyReportResponse(
            LocalDate date,
            LocalDateTime generatedAt,
            long totalTickets,
            long completed,
            long cancelled,
            long inProgress,
            long stillWaiting,
            long patientsCalled,
            Double averageWaitMinutes,
            Long longestWaitMinutes,
            long bloodRequests,
            long triageObservations,
            long unitsReceived,
            long unitsIssued,
            long donorAlertsSent,
            long donorAlertsAccepted,
            long donorAlertsDeclined,
            List<CountRow> byStatus,
            List<CountRow> byPriority,
            List<DepartmentRow> byDepartment,
            List<StockRow> stock) {}

    public record DepartmentResponse(
            Long id,
            String name,
            Long facilityId,
            String facilityName) {}
}
