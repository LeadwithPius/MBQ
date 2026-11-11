package com.MBQ.controller;

import java.util.List;

import com.MBQ.dto.BloodRequestDtos.*;
import com.MBQ.service.BloodRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/blood-bank/blood-requests")
public class BloodRequestController {
    private final BloodRequestService service;

    public BloodRequestController(BloodRequestService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF', 'DOCTOR', 'NURSE')")
    public List<BloodRequestResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE')")
    public BloodRequestResponse create(@Valid @RequestBody CreateBloodRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public BloodRequestResponse review(@PathVariable Long id, @Valid @RequestBody ReviewBloodRequest request) {
        return service.review(id, request);
    }

    @PostMapping("/{id}/fulfil")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public BloodRequestResponse fulfil(@PathVariable Long id, @Valid @RequestBody FulfilBloodRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.fulfil(id, request, idempotencyKey);
    }
}
