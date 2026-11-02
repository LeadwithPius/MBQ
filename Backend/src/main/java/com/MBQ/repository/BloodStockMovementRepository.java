package com.MBQ.repository;

import java.util.Optional;

import com.MBQ.model.BloodStockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BloodStockMovementRepository
        extends JpaRepository<BloodStockMovement, Long> {

    Optional<BloodStockMovement> findByIdempotencyKey(String idempotencyKey);
}
