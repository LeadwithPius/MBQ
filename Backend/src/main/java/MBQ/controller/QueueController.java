package com.MBQ.controller;

import com.MBQ.dto.CreateTicketRequest;
import com.MBQ.dto.DepartmentQueueResponse;
import com.MBQ.dto.TicketResponse;
import com.MBQ.dto.UpdatePriorityRequest;
import com.MBQ.dto.UpdateTicketStatusRequest;
import com.MBQ.model.TicketStatus;
import com.MBQ.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * Queue endpoints: tickets, the live department queue, call-next, status and priority changes.
 * All rules live in {@link QueueService}.
 */
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    /** POST /api/queue/tickets -> 201 Created with the new ticket (number, position...). */
    @PostMapping("/tickets")
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        TicketResponse created = queueService.createTicket(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** GET /api/queue/tickets/{id} */
    @GetMapping("/tickets/{id}")
    public TicketResponse getTicket(@PathVariable("id") Long id) {
        return queueService.getTicket(id);
    }

    /** GET /api/queue/tickets?departmentId=1&status=WAITING&date=2026-10-22 (all optional; date defaults to today). */
    @GetMapping("/tickets")
    public List<TicketResponse> listTickets(
            @RequestParam(name = "departmentId", required = false) Long departmentId,
            @RequestParam(name = "status", required = false) TicketStatus status,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queueService.listTickets(departmentId, status, date);
    }

    /** GET /api/queue/departments/{departmentId} -> the live queue screen for that department. */
    @GetMapping("/departments/{departmentId}")
    public DepartmentQueueResponse getDepartmentQueue(@PathVariable("departmentId") Long departmentId) {
        return queueService.getDepartmentQueue(departmentId);
    }

    /** POST /api/queue/departments/{departmentId}/call-next -> 200 with the called ticket, or 204 if nobody is waiting. */
    @PostMapping("/departments/{departmentId}/call-next")
    public ResponseEntity<TicketResponse> callNext(@PathVariable("departmentId") Long departmentId) {
        return queueService.callNext(departmentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** PATCH /api/queue/tickets/{id}/status -> 200, 404, or 409 if the change isn't allowed. */
    @PatchMapping("/tickets/{id}/status")
    public TicketResponse updateStatus(@PathVariable("id") Long id,
                                       @Valid @RequestBody UpdateTicketStatusRequest request) {
        return queueService.updateStatus(id, request);
    }

    /** PATCH /api/queue/tickets/{id}/priority -> 200, 404, or 409 if the ticket is finished. */
    @PatchMapping("/tickets/{id}/priority")
    public TicketResponse updatePriority(@PathVariable("id") Long id,
                                         @Valid @RequestBody UpdatePriorityRequest request) {
        return queueService.updatePriority(id, request);
    }
}
