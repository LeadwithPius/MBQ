package com.MBQ.controller;

import com.MBQ.dto.BloodInventoryDtos.InventoryOperationResponse;
import com.MBQ.dto.BloodInventoryDtos.IssueRequest;
import com.MBQ.dto.BloodInventoryDtos.ReceiveBatchRequest;
import com.MBQ.service.BloodInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/blood-bank/inventory")
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
public class BloodInventoryController {

    private final BloodInventoryService inventoryService;

    public BloodInventoryController(BloodInventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryOperationResponse receiveBatch(
            @Valid @RequestBody ReceiveBatchRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return inventoryService.receive(request, idempotencyKey);
    }

    @PostMapping("/issues")
    public InventoryOperationResponse issue(
            @Valid @RequestBody IssueRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return inventoryService.issue(request, idempotencyKey);
    }
}
