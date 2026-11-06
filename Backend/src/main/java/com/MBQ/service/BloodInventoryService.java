package com.MBQ.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.MBQ.dto.BloodInventoryDtos.BatchAllocation;
import com.MBQ.dto.BloodInventoryDtos.InventoryOperationResponse;
import com.MBQ.dto.BloodInventoryDtos.IssueRequest;
import com.MBQ.dto.BloodInventoryDtos.ReceiveBatchRequest;
import com.MBQ.model.BloodBatchVerificationStatus;
import com.MBQ.model.BloodInventoryOperation;
import com.MBQ.model.BloodInventoryOperationType;
import com.MBQ.model.BloodStock;
import com.MBQ.model.BloodStockBatch;
import com.MBQ.model.BloodStockMovement;
import com.MBQ.model.BloodStockMovementType;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.BloodInventoryOperationRepository;
import com.MBQ.repository.BloodStockBatchRepository;
import com.MBQ.repository.BloodStockMovementRepository;
import com.MBQ.repository.BloodStockRepository;

@Service
public class BloodInventoryService {

    private final BloodStockRepository stocks;
    private final BloodStockBatchRepository batches;
    private final BloodStockMovementRepository movements;
    private final BloodInventoryOperationRepository operations;
    private final StaffAccessService staffAccess;
    private final JdbcTemplate jdbc;
    private final JsonMapper objectMapper;

    public BloodInventoryService(
            BloodStockRepository stocks,
            BloodStockBatchRepository batches,
            BloodStockMovementRepository movements,
            BloodInventoryOperationRepository operations,
            StaffAccessService staffAccess,
            JdbcTemplate jdbc,
            JsonMapper objectMapper) {
        this.stocks = stocks;
        this.batches = batches;
        this.movements = movements;
        this.operations = operations;
        this.staffAccess = staffAccess;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InventoryOperationResponse receive(
            ReceiveBatchRequest request, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        validateReceive(request);

        StaffUser actor = staffAccess.currentStaff();
        BloodStock stock = lockStock(request.stockId());
        staffAccess.requireFacilityAccess(stock.getFacility().getId());

        String fingerprint = fingerprint(
                "RECEIVE|" + request.stockId() + "|" + request.batchReference().trim()
                + "|" + request.expiryDate() + "|" + request.units() + "|"
                + normalize(request.sourceReference()) + "|" + request.reason().trim());

        OperationClaim claim = claimOperation(
                idempotencyKey, fingerprint, BloodInventoryOperationType.RECEIVE, actor);

        if (!claim.created()) {
            return replay(claim, fingerprint, BloodInventoryOperationType.RECEIVE);
        }

        if (batches.findByStockIdAndBatchReference(
                stock.getId(), request.batchReference().trim()).isPresent()) {
            throw conflict("This batch reference already exists for the selected stock.");
        }

        int before = stock.getUnitsAvailable();
        int after = Math.addExact(before, request.units());

        BloodStockBatch batch = new BloodStockBatch();
        batch.setStock(stock);
        batch.setBatchReference(request.batchReference().trim());
        batch.setExpiryDate(request.expiryDate());
        batch.setOpeningQuantity(request.units());
        batch.setRemainingQuantity(request.units());
        batch.setVerificationStatus(BloodBatchVerificationStatus.PENDING_VERIFICATION);
        batch.setSourceReference(normalize(request.sourceReference()));
        batch.setRecordedBy(actor);
        batches.saveAndFlush(batch);

        stock.setUnitsAvailable(after);
        stocks.save(stock);

        BloodInventoryOperation operation = operations.findById(claim.operationId())
                .orElseThrow(() -> new IllegalStateException("Inventory operation disappeared"));

        BloodStockMovement movement = new BloodStockMovement();
        movement.setStock(stock);
        movement.setBatch(batch);
        movement.setMovementType(BloodStockMovementType.RECEIVED);
        movement.setUnitsChanged(request.units());
        movement.setQuantityBefore(before);
        movement.setQuantityAfter(after);
        movement.setReason(request.reason().trim());
        movement.setRecordedBy(actor);
        movement.setCreatedAt(LocalDateTime.now());
        movement.setIdempotencyKey(idempotencyKey);
        movement.setRequestFingerprint(fingerprint);
        movement.setOperation(operation);
        movements.save(movement);

        InventoryOperationResponse response = new InventoryOperationResponse(
                claim.operationId(), "RECEIVE", stock.getId(), request.units(),
                before, after,
                List.of(new BatchAllocation(batch.getId(), batch.getBatchReference(),
                        batch.getExpiryDate(), request.units())),
                false);
        completeOperation(claim.operationId(), response);
        return response;
    }

    @Transactional
    public InventoryOperationResponse issue(
            IssueRequest request, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        validateIssue(request);

        StaffUser actor = staffAccess.currentStaff();
        BloodStock stock = lockStock(request.stockId());
        staffAccess.requireFacilityAccess(stock.getFacility().getId());

        String fingerprint = fingerprint(
                "ISSUE|" + request.stockId() + "|" + request.units() + "|"
                + request.reason().trim());

        OperationClaim claim = claimOperation(
                idempotencyKey, fingerprint, BloodInventoryOperationType.ISSUE, actor);

        if (!claim.created()) {
            return replay(claim, fingerprint, BloodInventoryOperationType.ISSUE);
        }

        List<BloodStockBatch> eligible = batches.findEligibleBatchesForUpdate(stock.getId());
        int eligibleUnits = eligible.stream().mapToInt(BloodStockBatch::getRemainingQuantity).sum();
        if (eligibleUnits < request.units()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient verified, unexpired blood units. Eligible units: " + eligibleUnits);
        }

        int before = stock.getUnitsAvailable();
        if (before < request.units()) {
            throw new IllegalStateException(
                    "Batch balances exceed aggregate stock. Reconciliation is required.");
        }
        int after = before - request.units();

        BloodInventoryOperation operation = operations.findById(claim.operationId())
                .orElseThrow(() -> new IllegalStateException("Inventory operation disappeared"));

        int remainingToIssue = request.units();
        int movementBalance = before;
        List<BatchAllocation> allocations = new ArrayList<>();

        for (BloodStockBatch batch : eligible) {
            if (remainingToIssue == 0) break;

            int allocated = Math.min(batch.getRemainingQuantity(), remainingToIssue);
            batch.setRemainingQuantity(batch.getRemainingQuantity() - allocated);
            remainingToIssue -= allocated;
            allocations.add(new BatchAllocation(
                    batch.getId(), batch.getBatchReference(), batch.getExpiryDate(), allocated));

            BloodStockMovement movement = new BloodStockMovement();
            movement.setStock(stock);
            movement.setBatch(batch);
            movement.setMovementType(BloodStockMovementType.ISSUED);
            movement.setUnitsChanged(allocated);
            movement.setQuantityBefore(movementBalance);
            movement.setQuantityAfter(movementBalance - allocated);
            movement.setReason(request.reason().trim());
            movement.setRecordedBy(actor);
            movement.setCreatedAt(LocalDateTime.now());
            movement.setRequestFingerprint(fingerprint);
            movement.setOperation(operation);
            movements.save(movement);
            movementBalance -= allocated;
        }

        batches.saveAll(eligible);
        stock.setUnitsAvailable(after);
        stocks.save(stock);

        InventoryOperationResponse response = new InventoryOperationResponse(
                claim.operationId(), "ISSUE", stock.getId(), request.units(),
                before, after, List.copyOf(allocations), false);
        completeOperation(claim.operationId(), response);
        return response;
    }

    @Transactional
    public void verifyBatch(Long batchId) {
        BloodStockBatch batch = batches.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));
        staffAccess.requireFacilityAccess(batch.getStock().getFacility().getId());
        if (batch.getVerificationStatus() != BloodBatchVerificationStatus.PENDING_VERIFICATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only batches awaiting verification can be verified.");
        }
        batch.setVerificationStatus(BloodBatchVerificationStatus.VERIFIED);
        batches.save(batch);
    }

    private BloodStock lockStock(Long stockId) {
        return stocks.findByIdForUpdate(stockId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Blood stock record not found"));
    }

    private OperationClaim claimOperation(
            String key, String fingerprint, BloodInventoryOperationType type, StaffUser actor) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO blood_inventory_operation
                    (id, idempotency_key, operation_type, request_fingerprint, recorded_by)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, id, key, type.name(), fingerprint, actor.getId());

        if (inserted == 1) {
            return new OperationClaim(id, true, null, null);
        }

        OperationRow existing = jdbc.queryForObject("""
                SELECT id, operation_type, request_fingerprint, result_payload::text
                FROM blood_inventory_operation
                WHERE idempotency_key = ?
                FOR UPDATE
                """, (rs, rowNum) -> new OperationRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("operation_type"),
                    rs.getString("request_fingerprint"),
                    rs.getString("result_payload")), key);

        if (existing == null) throw new IllegalStateException("Idempotency record unavailable");
        return new OperationClaim(existing.id(), false,
                existing.operationType(), existing.fingerprint(), existing.resultPayload());
    }

    private InventoryOperationResponse replay(
            OperationClaim claim, String fingerprint, BloodInventoryOperationType expectedType) {
        if (!expectedType.name().equals(claim.existingType())
                || !fingerprint.equals(claim.existingFingerprint())) {
            throw conflict("This idempotency key has already been used for a different request.");
        }
        if (claim.resultPayload() == null) {
            throw conflict("The original operation has no completed result; contact an administrator.");
        }
        try {
            InventoryOperationResponse saved = objectMapper.readValue(
                    claim.resultPayload(), InventoryOperationResponse.class);
            return new InventoryOperationResponse(
                    saved.operationId(), saved.operationType(), saved.stockId(),
                    saved.unitsChanged(), saved.quantityBefore(), saved.quantityAfter(),
                    saved.allocations(), true);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Stored inventory operation result is invalid", ex);
        }
    }

    private void completeOperation(UUID id, InventoryOperationResponse response) {
        try {
            String payload = objectMapper.writeValueAsString(response);
            int updated = jdbc.update("""
                    UPDATE blood_inventory_operation
                    SET result_payload = CAST(? AS jsonb)
                    WHERE id = ?
                    """, payload, id);
            if (updated != 1) throw new IllegalStateException("Could not complete inventory operation");
        } catch (JacksonException ex) {
            throw new IllegalStateException("Could not serialize inventory operation result", ex);
        }
    }

    private static void validateReceive(ReceiveBatchRequest request) {
        if (request == null || request.stockId() == null || request.units() < 1
                || request.expiryDate() == null || request.expiryDate().isBefore(LocalDate.now())
                || blank(request.batchReference()) || blank(request.reason())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A stock, batch reference, future or current expiry date, positive quantity and reason are required.");
        }
    }

    private static void validateIssue(IssueRequest request) {
        if (request == null || request.stockId() == null || request.units() < 1
                || blank(request.reason())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A stock, positive quantity and reason are required.");
        }
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A valid Idempotency-Key header is required (1-120 characters).");
        }
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record OperationRow(
            UUID id, String operationType, String fingerprint, String resultPayload) {}

    private record OperationClaim(
            UUID operationId,
            boolean created,
            String existingType,
            String existingFingerprint,
            String resultPayload) {
        private OperationClaim(UUID operationId, boolean created, String existingType,
                               String existingFingerprint) {
            this(operationId, created, existingType, existingFingerprint, null);
        }
    }
}
