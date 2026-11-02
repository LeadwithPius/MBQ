package com.MBQ.repository;

import java.util.Optional;
import java.util.UUID;

import com.MBQ.model.BloodInventoryOperation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BloodInventoryOperationRepository
        extends JpaRepository<BloodInventoryOperation, UUID> {

    Optional<BloodInventoryOperation> findByIdempotencyKey(String idempotencyKey);
}
