package com.MBQ.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MBQ.dto.ReportDtos.CountRow;
import com.MBQ.dto.ReportDtos.DailyReportResponse;
import com.MBQ.dto.ReportDtos.DepartmentRow;
import com.MBQ.dto.ReportDtos.StockRow;
import com.MBQ.model.Alert;
import com.MBQ.model.BloodStock;
import com.MBQ.model.BloodStockMovement;
import com.MBQ.model.BloodStockMovementType;
import com.MBQ.model.PriorityLevel;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.QueueTicketStatusHistory;
import com.MBQ.model.TicketStatus;
import com.MBQ.repository.AlertRepository;
import com.MBQ.repository.BloodDonationRepository;
import com.MBQ.repository.LabRequestRepository;
import com.MBQ.model.DonationStatus;
import com.MBQ.model.LabRequestStatus;
import java.time.OffsetDateTime;
import com.MBQ.repository.BloodStockMovementRepository;
import com.MBQ.repository.BloodStockRepository;
import com.MBQ.repository.QueueTicketRepository;
import com.MBQ.repository.QueueTicketStatusHistoryRepository;
import com.MBQ.repository.TriageObservationRepository;

/** Builds the admin daily report (FR8) from the data already recorded by the other modules. */
@Service
public class ReportService {

    private final QueueTicketRepository tickets;
    private final QueueTicketStatusHistoryRepository history;
    private final TriageObservationRepository observations;
    private final BloodStockRepository stocks;
    private final BloodStockMovementRepository movements;
    private final AlertRepository alerts;
    private final LabRequestRepository labRequests;
    private final BloodDonationRepository donations;

    public ReportService(
            QueueTicketRepository tickets,
            QueueTicketStatusHistoryRepository history,
            TriageObservationRepository observations,
            BloodStockRepository stocks,
            BloodStockMovementRepository movements,
            AlertRepository alerts,
            LabRequestRepository labRequests,
            BloodDonationRepository donations) {
        this.tickets = tickets;
        this.history = history;
        this.observations = observations;
        this.stocks = stocks;
        this.movements = movements;
        this.alerts = alerts;
        this.labRequests = labRequests;
        this.donations = donations;
    }

    @Transactional(readOnly = true)
    public DailyReportResponse daily(LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? LocalDate.now() : requestedDate;
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        List<QueueTicket> dayTickets =
                tickets.findByTimeIssuedGreaterThanEqualAndTimeIssuedLessThanOrderByTimeIssuedAsc(from, to);

        // When was each ticket first called? (first move to IN_PROGRESS)
        Map<Long, LocalDateTime> firstCalled = new HashMap<>();
        if (!dayTickets.isEmpty()) {
            List<Long> ids = dayTickets.stream().map(QueueTicket::getId).toList();
            for (QueueTicketStatusHistory h : history.findByTicket_IdInOrderByChangedAtAscIdAsc(ids)) {
                if (h.getNewStatus() == TicketStatus.IN_PROGRESS) {
                    firstCalled.putIfAbsent(h.getTicket().getId(), h.getChangedAt());
                }
            }
        }

        Map<TicketStatus, Long> byStatus = new EnumMap<>(TicketStatus.class);
        for (TicketStatus s : TicketStatus.values()) byStatus.put(s, 0L);
        Map<PriorityLevel, Long> byPriority = new EnumMap<>(PriorityLevel.class);
        for (PriorityLevel p : PriorityLevel.values()) byPriority.put(p, 0L);

        Map<Long, DeptTally> byDepartment = new LinkedHashMap<>();
        List<Long> waits = new ArrayList<>();
        long bloodRequests = 0;

        for (QueueTicket t : dayTickets) {
            byStatus.merge(t.getStatus(), 1L, Long::sum);
            byPriority.merge(t.getPriorityLevel(), 1L, Long::sum);
            if (t.isRequiresBlood()) bloodRequests++;

            DeptTally tally = byDepartment.computeIfAbsent(
                    t.getDepartment().getId(), id -> new DeptTally(id, t.getDepartment().getName()));
            tally.tickets++;
            if (t.getStatus() == TicketStatus.COMPLETED) tally.completed++;
            if (t.getStatus() == TicketStatus.CANCELLED) tally.cancelled++;
            if (t.getStatus() == TicketStatus.WAITING) tally.stillWaiting++;

            LocalDateTime called = firstCalled.get(t.getId());
            if (called != null && t.getTimeIssued() != null && !called.isBefore(t.getTimeIssued())) {
                long minutes = Duration.between(t.getTimeIssued(), called).toMinutes();
                waits.add(minutes);
                tally.waits.add(minutes);
            }
        }

        long unitsReceived = 0;
        long unitsIssued = 0;
        long unitsWrittenOff = 0;
        for (BloodStockMovement m : movements.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)) {
            if (m.getMovementType() == BloodStockMovementType.RECEIVED) unitsReceived += m.getUnitsChanged();
            if (m.getMovementType() == BloodStockMovementType.ISSUED) unitsIssued += m.getUnitsChanged();
            if (m.getMovementType() == BloodStockMovementType.EXPIRED
                    || m.getMovementType() == BloodStockMovementType.WASTED) unitsWrittenOff += m.getUnitsChanged();
        }

        List<Alert> dayAlerts = alerts.findByDateSentGreaterThanEqualAndDateSentLessThan(from, to);
        long accepted = dayAlerts.stream().filter(a -> "ACCEPTED".equals(a.getResponseStatus())).count();
        long declined = dayAlerts.stream().filter(a -> "DECLINED".equals(a.getResponseStatus())).count();

        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime fromTz = from.atZone(zone).toOffsetDateTime();
        OffsetDateTime toTz = to.atZone(zone).toOffsetDateTime();
        long triage = observations.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(fromTz, toTz);
        long labRequested = labRequests.countByRequestedAtGreaterThanEqualAndRequestedAtLessThan(fromTz, toTz);
        long labCompleted = labRequests.countByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                LabRequestStatus.COMPLETED, fromTz, toTz);
        long donationsCollected = donations.countByCollectedAtGreaterThanEqualAndCollectedAtLessThan(fromTz, toTz);
        long donationsPassed = donations.countByStatusAndScreenedAtGreaterThanEqualAndScreenedAtLessThan(
                DonationStatus.PASSED, fromTz, toTz);
        long donationsFailed = donations.countByStatusAndScreenedAtGreaterThanEqualAndScreenedAtLessThan(
                DonationStatus.FAILED, fromTz, toTz);

        List<BloodStock> allStock = new ArrayList<>(stocks.findAll());
        allStock.sort(Comparator
                .comparing((BloodStock s) -> s.getFacility().getId())
                .thenComparing(s -> s.getBloodGroup().ordinal()));
        List<StockRow> stockRows = allStock.stream()
                .map(s -> new StockRow(s.getFacility().getId(), s.getBloodGroup(), s.getUnitsAvailable(),
                        s.getLowStockThreshold(), s.getUnitsAvailable() <= s.getLowStockThreshold()))
                .toList();

        return new DailyReportResponse(
                date,
                LocalDateTime.now(),
                dayTickets.size(),
                byStatus.get(TicketStatus.COMPLETED),
                byStatus.get(TicketStatus.CANCELLED),
                byStatus.get(TicketStatus.IN_PROGRESS),
                byStatus.get(TicketStatus.WAITING),
                waits.size(),
                average(waits),
                waits.stream().max(Long::compare).orElse(null),
                bloodRequests,
                triage,
                unitsReceived,
                unitsIssued,
                dayAlerts.size(),
                accepted,
                declined,
                byStatus.entrySet().stream().map(e -> new CountRow(e.getKey().name(), e.getValue())).toList(),
                byPriority.entrySet().stream().map(e -> new CountRow(e.getKey().name(), e.getValue())).toList(),
                byDepartment.values().stream()
                        .map(d -> new DepartmentRow(d.id, d.name, d.tickets, d.completed, d.cancelled,
                                d.stillWaiting, average(d.waits)))
                        .toList(),
                stockRows,
                unitsWrittenOff,
                labRequested,
                labCompleted,
                donationsCollected,
                donationsPassed,
                donationsFailed);
    }

    static Double average(List<Long> values) {
        if (values.isEmpty()) return null;
        double avg = values.stream().mapToLong(Long::longValue).average().orElse(0);
        return Math.round(avg * 10.0) / 10.0;
    }

    private static final class DeptTally {
        final Long id;
        final String name;
        long tickets;
        long completed;
        long cancelled;
        long stillWaiting;
        final List<Long> waits = new ArrayList<>();

        DeptTally(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
