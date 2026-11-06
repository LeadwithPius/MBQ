package com.MBQ.repository;

import java.util.List;
import java.util.Optional;

import com.MBQ.model.BloodStockBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BloodStockBatchRepository extends JpaRepository<BloodStockBatch, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT batch
        FROM BloodStockBatch batch
        WHERE batch.stock.id = :stockId
          AND batch.verificationStatus = com.MBQ.model.BloodBatchVerificationStatus.VERIFIED
          AND batch.remainingQuantity > 0
          AND batch.expiryDate >= CURRENT_DATE
        ORDER BY batch.expiryDate ASC, batch.id ASC
        """)
    List<BloodStockBatch> findEligibleBatchesForUpdate(@Param("stockId") Long stockId);

    /** All batches of a stock, soonest expiry first (legacy batches without expiry last). */
    @Query("""
        SELECT batch
        FROM BloodStockBatch batch
        WHERE batch.stock.id = :stockId
        ORDER BY CASE WHEN batch.expiryDate IS NULL THEN 1 ELSE 0 END,
                 batch.expiryDate ASC, batch.id ASC
        """)
    List<BloodStockBatch> findAllForStock(@Param("stockId") Long stockId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT batch FROM BloodStockBatch batch WHERE batch.id = :id")
    Optional<BloodStockBatch> findByIdForUpdate(@Param("id") Long id);

    Optional<BloodStockBatch> findByStockIdAndBatchReference(
        Long stockId,
        String batchReference
    );
}
