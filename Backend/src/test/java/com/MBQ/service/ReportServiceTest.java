package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.MBQ.dto.ReportDtos.DailyReportResponse;
import com.MBQ.model.BloodStockMovement;
import com.MBQ.model.BloodStockMovementType;
import com.MBQ.model.Department;
import com.MBQ.model.PriorityLevel;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.QueueTicketStatusHistory;
import com.MBQ.model.TicketStatus;
import com.MBQ.repository.AlertRepository;
import com.MBQ.repository.BloodStockMovementRepository;
import com.MBQ.repository.BloodStockRepository;
import com.MBQ.repository.QueueTicketRepository;
import com.MBQ.repository.QueueTicketStatusHistoryRepository;
import com.MBQ.repository.TriageObservationRepository;

/** Unit tests for the daily report numbers. No database: repositories are Mockito mocks. */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private QueueTicketRepository tickets;
    @Mock private QueueTicketStatusHistoryRepository history;
    @Mock private TriageObservationRepository observations;
    @Mock private BloodStockRepository stocks;
    @Mock private BloodStockMovementRepository movements;
    @Mock private AlertRepository alerts;

    private static final LocalDate DAY = LocalDate.of(2026, 11, 11);

    private static QueueTicket ticket(long id, Department dept, TicketStatus status,
                                      PriorityLevel priority, LocalDateTime issued) {
        QueueTicket t = new QueueTicket();
        t.setId(id);
        t.setDepartment(dept);
        t.setStatus(status);
        t.setPriorityLevel(priority);
        t.setTimeIssued(issued);
        return t;
    }

    private static QueueTicketStatusHistory called(QueueTicket t, LocalDateTime at) {
        QueueTicketStatusHistory h = new QueueTicketStatusHistory();
        h.setTicket(t);
        h.setPreviousStatus(TicketStatus.WAITING);
        h.setNewStatus(TicketStatus.IN_PROGRESS);
        h.setChangedAt(at);
        return h;
    }

    @Test
    void daily_countsTicketsAndAveragesWaitFromIssuedToFirstCalled() {
        Department opd = new Department();
        opd.setId(1L);
        opd.setName("OPD");
        LocalDateTime nine = DAY.atTime(9, 0);

        QueueTicket seen = ticket(1, opd, TicketStatus.COMPLETED, PriorityLevel.EMERGENCY, nine);
        QueueTicket alsoSeen = ticket(2, opd, TicketStatus.COMPLETED, PriorityLevel.NORMAL, nine);
        QueueTicket waiting = ticket(3, opd, TicketStatus.WAITING, PriorityLevel.NORMAL, nine);

        when(tickets.findByTimeIssuedGreaterThanEqualAndTimeIssuedLessThanOrderByTimeIssuedAsc(any(), any()))
                .thenReturn(List.of(seen, alsoSeen, waiting));
        when(history.findByTicket_IdInOrderByChangedAtAscIdAsc(anyCollection())).thenReturn(List.of(
                called(seen, nine.plusMinutes(10)),
                called(alsoSeen, nine.plusMinutes(30))));

        BloodStockMovement issued = new BloodStockMovement();
        issued.setMovementType(BloodStockMovementType.ISSUED);
        issued.setUnitsChanged(2);
        when(movements.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(List.of(issued));

        DailyReportResponse report = new ReportService(tickets, history, observations, stocks, movements, alerts)
                .daily(DAY);

        assertEquals(3, report.totalTickets());
        assertEquals(2, report.completed());
        assertEquals(1, report.stillWaiting());
        assertEquals(2, report.patientsCalled());
        assertEquals(20.0, report.averageWaitMinutes());   // (10 + 30) / 2
        assertEquals(30L, report.longestWaitMinutes());
        assertEquals(2, report.unitsIssued());
        assertEquals(1, report.byDepartment().size());
        assertEquals(3, report.byDepartment().get(0).tickets());
    }

    @Test
    void daily_emptyDay_hasNoAverageWait() {
        DailyReportResponse report = new ReportService(tickets, history, observations, stocks, movements, alerts)
                .daily(DAY);

        assertEquals(0, report.totalTickets());
        assertNull(report.averageWaitMinutes());
        assertNull(report.longestWaitMinutes());
    }
}
