package com.MBQ.service;

import com.MBQ.dto.QueueDtos.CreateTicketRequest;
import com.MBQ.dto.QueueDtos.PatientRegistrationRequest;
import com.MBQ.dto.QueueDtos.PatientResponse;
import com.MBQ.dto.QueueDtos.TicketHistoryResponse;
import com.MBQ.dto.QueueDtos.TicketResponse;
import com.MBQ.dto.QueueDtos.UpdateTicketStatusRequest;
import com.MBQ.model.Department;
import com.MBQ.model.Facility;
import com.MBQ.model.Patient;
import com.MBQ.model.PriorityLevel;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.QueueTicketStatusHistory;
import com.MBQ.model.TicketStatus;
import com.MBQ.repository.DepartmentRepository;
import com.MBQ.repository.FacilityRepository;
import com.MBQ.repository.PatientRepository;
import com.MBQ.repository.QueueTicketRepository;
import com.MBQ.repository.QueueTicketStatusHistoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class QueueService {

    private final PatientRepository patientRepository;
    private final FacilityRepository facilityRepository;
    private final DepartmentRepository departmentRepository;
    private final QueueTicketRepository queueTicketRepository;
    private final QueueTicketStatusHistoryRepository historyRepository;
    private final JdbcTemplate jdbcTemplate;

    public QueueService(
            PatientRepository patientRepository,
            FacilityRepository facilityRepository,
            DepartmentRepository departmentRepository,
            QueueTicketRepository queueTicketRepository,
            QueueTicketStatusHistoryRepository historyRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.patientRepository = patientRepository;
        this.facilityRepository = facilityRepository;
        this.departmentRepository = departmentRepository;
        this.queueTicketRepository = queueTicketRepository;
        this.historyRepository = historyRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------------------------------------------------------
    // STEP 1: REGISTER A PATIENT
    // ---------------------------------------------------------

    @Transactional
    public PatientResponse registerPatient(
            PatientRegistrationRequest request
    ) {
        Facility facility = facilityRepository.findById(request.facilityId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Facility not found"
                ));

        Patient patient = new Patient();
        patient.setName(request.name().trim());
        patient.setAge(request.age());
        patient.setSex(trimToNull(request.sex()));
        patient.setContact(trimToNull(request.contact()));
        patient.setBloodGroup(request.bloodGroup());
        patient.setFacility(facility);

        Patient saved = patientRepository.save(patient);

        return toPatientResponse(saved);
    }

    // ---------------------------------------------------------
    // STEP 2: CREATE A QUEUE TICKET
    // ---------------------------------------------------------

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Patient not found"
                ));

        Department department = departmentRepository
                .findById(request.departmentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Department not found"
                ));

        Long departmentFacilityId = department.getFacility().getId();

        if (patient.getFacility() == null
                || !Objects.equals(
                        patient.getFacility().getId(),
                        departmentFacilityId
                )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The patient and department must belong to the same facility"
            );
        }

        LocalDate issueDate = LocalDate.now();

        Integer sequenceNumber = jdbcTemplate.queryForObject(
                """
                INSERT INTO queue_ticket_sequence (
                    facility_id,
                    department_id,
                    issue_date,
                    last_number
                )
                VALUES (?, ?, ?, 1)
                ON CONFLICT (facility_id, department_id, issue_date)
                DO UPDATE SET
                    last_number = queue_ticket_sequence.last_number + 1
                RETURNING last_number
                """,
                Integer.class,
                departmentFacilityId,
                department.getId(),
                issueDate
        );

        if (sequenceNumber == null) {
            throw new IllegalStateException(
                    "Could not allocate a ticket number"
            );
        }

        String ticketNumber = generateTicketNumber(
                departmentFacilityId,
                department.getName(),
                issueDate,
                sequenceNumber
        );

        QueueTicket ticket = new QueueTicket();
        ticket.setTicketNumber(ticketNumber);
        ticket.setPatient(patient);
        ticket.setDepartment(department);
        ticket.setPriorityLevel(
                request.priorityLevel() == null
                        ? PriorityLevel.NORMAL
                        : request.priorityLevel()
        );
        ticket.setStatus(TicketStatus.WAITING);
        ticket.setTimeIssued(LocalDateTime.now());
        ticket.setTriageNotes(trimToNull(request.triageNotes()));
        ticket.setRequiresBlood(request.requiresBlood());

        QueueTicket saved = queueTicketRepository.saveAndFlush(ticket);

        recordStatusChange(
                saved,
                null,
                TicketStatus.WAITING,
                "Ticket created"
        );

        return toTicketResponse(saved);
    }

    // ---------------------------------------------------------
    // STEP 3: RETRIEVE THE WAITING QUEUE
    // ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TicketResponse> getWaitingQueue(Long departmentId) {
        requireDepartment(departmentId);

        return queueTicketRepository.findQueueByDepartmentAndStatus(
                        departmentId,
                        TicketStatus.WAITING
                )
                .stream()
                .map(this::toTicketResponse)
                .toList();
    }

    // ---------------------------------------------------------
    // STEP 4: CALL THE NEXT PATIENT
    // ---------------------------------------------------------

    @Transactional
    public TicketResponse callNextPatient(Long departmentId) {
        requireDepartment(departmentId);

        QueueTicket ticket = queueTicketRepository
                .findNextWaitingForUpdateSkipLocked(departmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No waiting patients are available in this department"
                ));

        TicketStatus previousStatus = ticket.getStatus();

        ticket.setStatus(TicketStatus.IN_PROGRESS);

        QueueTicket saved = queueTicketRepository.saveAndFlush(ticket);

        recordStatusChange(
                saved,
                previousStatus,
                TicketStatus.IN_PROGRESS,
                "Patient called"
        );

        return toTicketResponse(saved);
    }

    // ---------------------------------------------------------
    // STEP 5: UPDATE TICKET STATUS
    // ---------------------------------------------------------

    @Transactional
    public TicketResponse updateTicketStatus(
            Long ticketId,
            UpdateTicketStatusRequest request
    ) {
        QueueTicket ticket = queueTicketRepository
                .findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Queue ticket not found"
                ));

        TicketStatus previousStatus = ticket.getStatus();
        TicketStatus newStatus = request.status();

        validateTransition(previousStatus, newStatus);

        ticket.setStatus(newStatus);

        if (request.outcome() != null) {
            ticket.setOutcome(trimToNull(request.outcome()));
        }

        QueueTicket saved = queueTicketRepository.saveAndFlush(ticket);

        recordStatusChange(
                saved,
                previousStatus,
                newStatus,
                trimToNull(request.notes())
        );

        return toTicketResponse(saved);
    }

    // ---------------------------------------------------------
    // STEP 6: RETRIEVE TICKET HISTORY
    // ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TicketHistoryResponse> getTicketHistory(Long ticketId) {
        queueTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Queue ticket not found"
                ));

        return historyRepository
                .findByTicket_IdOrderByChangedAtAscIdAsc(ticketId)
                .stream()
                .map(history -> new TicketHistoryResponse(
                        history.getId(),
                        history.getTicket().getId(),
                        history.getPreviousStatus(),
                        history.getNewStatus(),
                        history.getChangedByStaffId(),
                        history.getChangedAt(),
                        history.getNotes()
                ))
                .toList();
    }

    // ---------------------------------------------------------
    // SHARED HELPERS
    // ---------------------------------------------------------

    private Department requireDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Department not found"
                ));
    }

    private void validateTransition(
            TicketStatus current,
            TicketStatus target
    ) {
        boolean allowed = switch (current) {
            case WAITING ->
                    target == TicketStatus.IN_PROGRESS
                    || target == TicketStatus.CANCELLED;

            case IN_PROGRESS ->
                    target == TicketStatus.COMPLETED
                    || target == TicketStatus.CANCELLED;

            case COMPLETED, CANCELLED -> false;
        };

        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid ticket status transition: "
                            + current + " -> " + target
            );
        }
    }

    private void recordStatusChange(
            QueueTicket ticket,
            TicketStatus previousStatus,
            TicketStatus newStatus,
            String notes
    ) {
        QueueTicketStatusHistory history =
                new QueueTicketStatusHistory();

        history.setTicket(ticket);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setChangedAt(LocalDateTime.now());
        history.setNotes(notes);

        // Staff attribution will be populated from the authenticated
        // staff identity when authentication is connected to this workflow.
        history.setChangedByStaffId(null);

        historyRepository.save(history);
    }

    private String generateTicketNumber(
            Long facilityId,
            String departmentName,
            LocalDate issueDate,
            int sequenceNumber
    ) {
        String departmentCode = departmentName
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");

        if (departmentCode.isEmpty()) {
            departmentCode = "DEP";
        } else if (departmentCode.length() > 3) {
            departmentCode = departmentCode.substring(0, 3);
        }

        return "F" + facilityId
                + "-" + departmentCode
                + "-" + issueDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format("%04d", sequenceNumber);
    }

    private PatientResponse toPatientResponse(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getName(),
                patient.getAge(),
                patient.getSex(),
                patient.getContact(),
                patient.getBloodGroup(),
                patient.getFacility() == null
                        ? null
                        : patient.getFacility().getId()
        );
    }

    private TicketResponse toTicketResponse(QueueTicket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getPatient().getId(),
                ticket.getPatient().getName(),
                ticket.getDepartment().getId(),
                ticket.getDepartment().getName(),
                ticket.getPriorityLevel(),
                ticket.getStatus(),
                ticket.getTimeIssued(),
                ticket.isRequiresBlood(),
                ticket.getTriageNotes(),
                ticket.getOutcome()
        );
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}