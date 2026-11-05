package com.MBQ.repository;

import java.util.List;
import java.util.Optional;

import com.MBQ.model.BloodStockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BloodStockMovementRepository
        extends JpaRepository<BloodStockMovement, Long> {

    Optional<BloodStockMovement> findByIdempotencyKey(String idempotencyKey);

    /** Ledger entries recorded in [from, to) (daily report). */
    List<BloodStockMovement> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            java.time.LocalDateTime from, java.time.LocalDateTime to);

    /** The 20 most recent ledger entries for one stock. */
    List<BloodStockMovement> findTop20ByStock_IdOrderByCreatedAtDescIdDesc(Long stockId);
}
