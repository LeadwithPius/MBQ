package com.MBQ.dto;

import java.util.List;

/**
 * The live queue for one department (FR3), shaped for the dashboard screen.
 *
 * @param nowServing tickets currently IN_PROGRESS
 * @param waiting    WAITING tickets in call order (emergency first, then urgent, then normal;
 *                   oldest first within the same priority), each with its position
 */
public record DepartmentQueueResponse(
        Long departmentId,
        String departmentName,
        int waitingCount,
        List<TicketResponse> nowServing,
        List<TicketResponse> waiting) {
}
