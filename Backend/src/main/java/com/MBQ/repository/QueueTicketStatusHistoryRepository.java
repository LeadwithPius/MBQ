package com.MBQ.repository;

import com.MBQ.model.QueueTicketStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueueTicketStatusHistoryRepository
        extends JpaRepository<QueueTicketStatusHistory, Long> {

    List<QueueTicketStatusHistory>
    findByTicket_IdOrderByChangedAtAscIdAsc(Long ticketId);

    /** Status history of several tickets at once, oldest first (daily report wait times). */
    List<QueueTicketStatusHistory> findByTicket_IdInOrderByChangedAtAscIdAsc(
            java.util.Collection<Long> ticketIds);
}
