package com.MBQ.service;

import com.MBQ.model.PriorityLevel;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.TicketStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit tests: no Spring, no database, run in milliseconds. */
class QueueRulesTest {

    private static final LocalDateTime NINE_AM = LocalDateTime.of(2026, 10, 22, 9, 0);

    private static QueueTicket ticket(long id, PriorityLevel priority, LocalDateTime issued) {
        QueueTicket t = new QueueTicket();
        t.setId(id);
        t.setPriorityLevel(priority);
        t.setTimeIssued(issued);
        return t;
    }

    @Test
    void callOrder_priorityFirst_thenArrivalTime_thenId() {
        QueueTicket normalEarly = ticket(1, PriorityLevel.NORMAL, NINE_AM);
        QueueTicket urgentLate = ticket(2, PriorityLevel.URGENT, NINE_AM.plusMinutes(30));
        QueueTicket emergencyLatest = ticket(3, PriorityLevel.EMERGENCY, NINE_AM.plusHours(1));
        QueueTicket normalSameTimeHigherId = ticket(4, PriorityLevel.NORMAL, NINE_AM);

        List<QueueTicket> queue = new ArrayList<>(List.of(normalSameTimeHigherId, normalEarly, urgentLate, emergencyLatest));
        queue.sort(QueueRules.CALL_ORDER);

        assertThat(queue).containsExactly(emergencyLatest, urgentLate, normalEarly, normalSameTimeHigherId);
    }

    @ParameterizedTest(name = "{0} -> {1} allowed = {2}")
    @CsvSource({
            "WAITING,     IN_PROGRESS, true",
            "WAITING,     CANCELLED,   true",
            "WAITING,     COMPLETED,   false",
            "WAITING,     WAITING,     false",
            "IN_PROGRESS, COMPLETED,   true",
            "IN_PROGRESS, CANCELLED,   true",
            "IN_PROGRESS, WAITING,     true",
            "COMPLETED,   WAITING,     false",
            "COMPLETED,   IN_PROGRESS, false",
            "CANCELLED,   WAITING,     false",
            "CANCELLED,   COMPLETED,   false"
    })
    void statusTransitions(TicketStatus from, TicketStatus to, boolean allowed) {
        assertThat(QueueRules.canTransition(from, to)).isEqualTo(allowed);
    }

    @Test
    void activeStatuses_areWaitingAndInProgressOnly() {
        assertThat(QueueRules.ACTIVE_STATUSES)
                .containsExactlyInAnyOrder(TicketStatus.WAITING, TicketStatus.IN_PROGRESS);
    }
}
