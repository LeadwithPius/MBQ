package com.MBQ.controller;

import com.MBQ.dto.BloodInventoryDtos.InventoryOperationResponse;
import com.MBQ.dto.BloodInventoryDtos.IssueRequest;
import com.MBQ.dto.BloodInventoryDtos.ReceiveBatchRequest;
import com.MBQ.dto.BloodInventoryDtos.WriteOffRequest;
import com.MBQ.service.BloodInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/blood-bank/inventory")
public class BloodInventoryController {

    private final BloodInventoryService inventoryService;

    public BloodInventoryController(BloodInventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** Receiving stock is a lab job. */
    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public InventoryOperationResponse receiveBatch(
            @Valid @RequestBody ReceiveBatchRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return inventoryService.receive(request, idempotencyKey);
    }

    /** Lab staff issue units; doctors may issue directly in an emergency. */
    @PostMapping("/issues")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF', 'DOCTOR')")
    public InventoryOperationResponse issue(
            @Valid @RequestBody IssueRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return inventoryService.issue(request, idempotencyKey);
    }
    /** Write off expired or wasted units (lab job). */
    @PostMapping("/write-offs")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public InventoryOperationResponse writeOff(
            @Valid @RequestBody WriteOffRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return inventoryService.writeOff(request, idempotencyKey);
    }

    /** Verify a newly received batch after the required physical and documentation checks. */
    @PatchMapping("/batches/{batchId}/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyBatch(@PathVariable Long batchId) {
        inventoryService.verifyBatch(batchId);
    }

}
