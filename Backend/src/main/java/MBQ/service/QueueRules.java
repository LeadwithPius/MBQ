package com.MBQ.service;

import com.MBQ.model.PriorityLevel;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.TicketStatus;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The queue's rules in one place, as plain Java (no database), so they are easy to read and test.
 */
public final class QueueRules {

    /** Statuses that mean "this patient is still in the queue". */
    public static final Set<TicketStatus> ACTIVE_STATUSES =
            EnumSet.of(TicketStatus.WAITING, TicketStatus.IN_PROGRESS);

    /**
     * Call order for WAITING tickets:
     * 1. higher priority first (EMERGENCY, then URGENT, then NORMAL),
     * 2. then whoever arrived first,
     * 3. then lower id (tie-breaker so the order is always stable).
     */
    public static final Comparator<QueueTicket> CALL_ORDER =
            Comparator.comparingInt((QueueTicket t) -> rank(t.getPriorityLevel()))
                    .thenComparing(QueueTicket::getTimeIssued)
                    .thenComparing(QueueTicket::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * Allowed status changes. Anything not listed is rejected with 409 Conflict.
     *
     * WAITING     -> IN_PROGRESS (called), CANCELLED (left / registered by mistake)
     * IN_PROGRESS -> COMPLETED (seen), CANCELLED, WAITING (sent back to wait, e.g. for lab results)
     * COMPLETED and CANCELLED are final.
     */
    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(TicketStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(TicketStatus.WAITING,
                EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(TicketStatus.IN_PROGRESS,
                EnumSet.of(TicketStatus.COMPLETED, TicketStatus.CANCELLED, TicketStatus.WAITING));
        ALLOWED_TRANSITIONS.put(TicketStatus.COMPLETED, EnumSet.noneOf(TicketStatus.class));
        ALLOWED_TRANSITIONS.put(TicketStatus.CANCELLED, EnumSet.noneOf(TicketStatus.class));
    }

    private QueueRules() {
    }

    public static boolean canTransition(TicketStatus from, TicketStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** Lower number = called sooner. Written out explicitly so reordering the enum can't break it. */
    static int rank(PriorityLevel level) {
        if (level == null) {
            return 2;
        }
        return switch (level) {
            case EMERGENCY -> 0;
            case URGENT -> 1;
            case NORMAL -> 2;
        };
    }
}
