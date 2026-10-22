package com.MBQ.service;

import com.MBQ.dto.CreateTicketRequest;
import com.MBQ.dto.DepartmentQueueResponse;
import com.MBQ.dto.TicketResponse;
import com.MBQ.dto.UpdatePriorityRequest;
import com.MBQ.dto.UpdateTicketStatusRequest;
import com.MBQ.exception.ConflictException;
import com.MBQ.exception.ResourceNotFoundException;
import com.MBQ.model.Department;
import com.MBQ.model.Patient;
import com.MBQ.model.PriorityLevel;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.TicketStatus;
import com.MBQ.repository.DepartmentRepository;
import com.MBQ.repository.PatientRepository;
import com.MBQ.repository.QueueTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Queue tickets: creation (FR2), live queue (FR3), priority flagging (FR4),
 * calling the next patient and status updates.
 *
 * Every method maps entities to DTOs inside its transaction, because
 * spring.jpa.open-in-view is false (lazy relations can't be loaded after the transaction ends).
 */
@Service
public class QueueService {

    private final QueueTicketRepository tickets;
    private final PatientRepository patients;
    private final DepartmentRepository departments;

    public QueueService(QueueTicketRepository tickets, PatientRepository patients,
                        DepartmentRepository departments) {
        this.tickets = tickets;
        this.patients = patients;
        this.departments = departments;
    }

    /** Issues a new WAITING ticket with the next daily number for the department. */
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", request.patientId()));

        // Lock the department row first so numbering is safe when two receptionists click at once.
        Department department = departments.findByIdForUpdate(request.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", request.departmentId()));

        if (tickets.existsByPatientIdAndStatusIn(patient.getId(), QueueRules.ACTIVE_STATUSES)) {
            throw new ConflictException("Patient " + patient.getId()
                    + " already has an active ticket; complete or cancel it first");
        }

        LocalDateTime now = LocalDateTime.now();
        long issuedToday = tickets.countByDepartmentIdAndTimeIssuedGreaterThanEqual(
                department.getId(), now.toLocalDate().atStartOfDay());

        QueueTicket ticket = new QueueTicket();
        ticket.setPatient(patient);
        ticket.setDepartment(department);
        ticket.setTicketNumber(TicketNumberGenerator.format(
                TicketNumberGenerator.prefixFor(department.getName()), issuedToday + 1));
        ticket.setPriorityLevel(request.priorityLevel() != null ? request.priorityLevel() : PriorityLevel.NORMAL);
        ticket.setStatus(TicketStatus.WAITING);
        ticket.setTimeIssued(now);
        ticket.setRequiresBlood(Boolean.TRUE.equals(request.requiresBlood()));

        QueueTicket saved = tickets.save(ticket);
        return TicketResponse.from(saved, positionOf(saved));
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicket(Long id) {
        QueueTicket ticket = findTicket(id);
        return TicketResponse.from(ticket, positionOf(ticket));
    }

    /**
     * Tickets issued on a day (default today), optionally filtered by department and status.
     * Ordered by time issued. Positions are filled in for WAITING tickets.
     */
    @Transactional(readOnly = true)
    public List<TicketResponse> listTickets(Long departmentId, TicketStatus status, LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now();
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();

        List<QueueTicket> found;
        if (departmentId != null) {
            requireDepartment(departmentId);
            found = tickets.findIssuedBetweenForDepartment(departmentId, from, to);
        } else {
            found = tickets.findIssuedBetween(from, to);
        }

        return found.stream()
                .filter(t -> status == null || t.getStatus() == status)
                .map(t -> TicketResponse.from(t, positionOf(t)))
                .toList();
    }

    /** The live queue screen for one department (FR3). */
    @Transactional(readOnly = true)
    public DepartmentQueueResponse getDepartmentQueue(Long departmentId) {
        Department department = requireDepartment(departmentId);

        List<QueueTicket> active = tickets.findByDepartmentAndStatuses(departmentId, QueueRules.ACTIVE_STATUSES);

        List<TicketResponse> nowServing = active.stream()
                .filter(t -> t.getStatus() == TicketStatus.IN_PROGRESS)
                .sorted(QueueRules.CALL_ORDER)
                .map(t -> TicketResponse.from(t, null))
                .toList();

        List<QueueTicket> waitingSorted = active.stream()
                .filter(t -> t.getStatus() == TicketStatus.WAITING)
                .sorted(QueueRules.CALL_ORDER)
                .toList();

        List<TicketResponse> waiting = new ArrayList<>();
        for (int i = 0; i < waitingSorted.size(); i++) {
            waiting.add(TicketResponse.from(waitingSorted.get(i), i + 1));
        }

        return new DepartmentQueueResponse(department.getId(), department.getName(),
                waiting.size(), nowServing, waiting);
    }

    /**
     * "Call next": moves the first WAITING ticket (by call order) to IN_PROGRESS.
     * Returns empty when nobody is waiting.
     */
    @Transactional
    public Optional<TicketResponse> callNext(Long departmentId) {
        // Lock so two nurses pressing the button together can't call the same patient.
        departments.findByIdForUpdate(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));

        Optional<QueueTicket> next = tickets
                .findByDepartmentAndStatuses(departmentId, EnumSet.of(TicketStatus.WAITING))
                .stream()
                .min(QueueRules.CALL_ORDER);

        next.ifPresent(t -> t.setStatus(TicketStatus.IN_PROGRESS));
        return next.map(t -> TicketResponse.from(t, null));
    }

    /** Changes a ticket's status, enforcing {@link QueueRules#canTransition}. */
    @Transactional
    public TicketResponse updateStatus(Long ticketId, UpdateTicketStatusRequest request) {
        QueueTicket ticket = findTicket(ticketId);
        TicketStatus from = ticket.getStatus();
        TicketStatus to = request.status();

        if (!QueueRules.canTransition(from, to)) {
            throw new ConflictException("Cannot change ticket " + ticket.getTicketNumber()
                    + " from " + from + " to " + to);
        }

        ticket.setStatus(to);
        if (request.outcome() != null) {
            ticket.setOutcome(request.outcome().trim());
        }
        if (request.triageNotes() != null) {
            ticket.setTriageNotes(request.triageNotes().trim());
        }
        return TicketResponse.from(ticket, positionOf(ticket));
    }

    /** Triage flags a ticket's priority (FR4). Only allowed while the patient is still in the queue. */
    @Transactional
    public TicketResponse updatePriority(Long ticketId, UpdatePriorityRequest request) {
        QueueTicket ticket = findTicket(ticketId);

        if (!QueueRules.ACTIVE_STATUSES.contains(ticket.getStatus())) {
            throw new ConflictException("Cannot change priority of ticket " + ticket.getTicketNumber()
                    + " because it is " + ticket.getStatus());
        }

        ticket.setPriorityLevel(request.priorityLevel());
        if (request.triageNotes() != null) {
            ticket.setTriageNotes(request.triageNotes().trim());
        }
        if (request.requiresBlood() != null) {
            ticket.setRequiresBlood(request.requiresBlood());
        }
        return TicketResponse.from(ticket, positionOf(ticket));
    }

    // ---------- helpers ----------

    private QueueTicket findTicket(Long id) {
        return tickets.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
    }

    private Department requireDepartment(Long id) {
        return departments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    /** 1-based place in the department's waiting line, or null if the ticket isn't WAITING. */
    private Integer positionOf(QueueTicket ticket) {
        if (ticket.getStatus() != TicketStatus.WAITING) {
            return null;
        }
        List<QueueTicket> waiting = tickets
                .findByDepartmentAndStatuses(ticket.getDepartment().getId(), EnumSet.of(TicketStatus.WAITING))
                .stream()
                .sorted(QueueRules.CALL_ORDER)
                .toList();
        for (int i = 0; i < waiting.size(); i++) {
            if (waiting.get(i).getId().equals(ticket.getId())) {
                return i + 1;
            }
        }
        return null;
    }
}
