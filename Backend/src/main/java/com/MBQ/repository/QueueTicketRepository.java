package com.MBQ.repository;

import com.MBQ.model.QueueTicket;
import com.MBQ.model.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QueueTicketRepository
        extends JpaRepository<QueueTicket, Long> {

    @Query("""
        SELECT q
        FROM QueueTicket q
        JOIN FETCH q.patient
        JOIN FETCH q.department
        WHERE q.department.id = :departmentId
          AND q.status = :status
        ORDER BY
          CASE q.priorityLevel
            WHEN com.MBQ.model.PriorityLevel.EMERGENCY THEN 0
            WHEN com.MBQ.model.PriorityLevel.URGENT THEN 1
            ELSE 2
          END,
          q.timeIssued ASC,
          q.id ASC
        """)
    List<QueueTicket> findQueueByDepartmentAndStatus(
            @Param("departmentId") Long departmentId,
            @Param("status") TicketStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT q
        FROM QueueTicket q
        WHERE q.id = :ticketId
        """)
    Optional<QueueTicket> findByIdForUpdate(
            @Param("ticketId") Long ticketId
    );

    @Query(value = """
        SELECT *
        FROM queue_ticket
        WHERE department_id = :departmentId
          AND status = 'WAITING'
        ORDER BY
          CASE priority_level
            WHEN 'EMERGENCY' THEN 0
            WHEN 'URGENT' THEN 1
            ELSE 2
          END,
          time_issued ASC,
          id ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<QueueTicket> findNextWaitingForUpdateSkipLocked(
            @Param("departmentId") Long departmentId
    );
}