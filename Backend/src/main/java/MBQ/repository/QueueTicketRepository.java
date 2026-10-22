package com.MBQ.repository;

import com.MBQ.model.QueueTicket;
import com.MBQ.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QueueTicketRepository extends JpaRepository<QueueTicket, Long> {

    /** One ticket with its patient and department loaded in the same query. */
    @Query("""
            select t from QueueTicket t
            join fetch t.patient
            join fetch t.department
            where t.id = :id""")
    Optional<QueueTicket> findDetailedById(@Param("id") Long id);

    /** Tickets in a department with any of the given statuses (used for the live queue). */
    @Query("""
            select t from QueueTicket t
            join fetch t.patient
            join fetch t.department d
            where d.id = :departmentId and t.status in :statuses""")
    List<QueueTicket> findByDepartmentAndStatuses(@Param("departmentId") Long departmentId,
                                                  @Param("statuses") Collection<TicketStatus> statuses);

    /** All tickets issued in [from, to), oldest first. */
    @Query("""
            select t from QueueTicket t
            join fetch t.patient
            join fetch t.department
            where t.timeIssued >= :from and t.timeIssued < :to
            order by t.timeIssued asc, t.id asc""")
    List<QueueTicket> findIssuedBetween(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    /** Tickets of one department issued in [from, to), oldest first. */
    @Query("""
            select t from QueueTicket t
            join fetch t.patient
            join fetch t.department d
            where d.id = :departmentId and t.timeIssued >= :from and t.timeIssued < :to
            order by t.timeIssued asc, t.id asc""")
    List<QueueTicket> findIssuedBetweenForDepartment(@Param("departmentId") Long departmentId,
                                                     @Param("from") LocalDateTime from,
                                                     @Param("to") LocalDateTime to);

    /** How many tickets a department has issued since a moment (used for daily ticket numbers). */
    long countByDepartmentIdAndTimeIssuedGreaterThanEqual(Long departmentId, LocalDateTime from);

    /** True if the patient already has a ticket with one of these statuses. */
    boolean existsByPatientIdAndStatusIn(Long patientId, Collection<TicketStatus> statuses);
}
