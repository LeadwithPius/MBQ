package com.MBQ.repository;

import java.util.List;
import java.util.Optional;

import com.MBQ.model.BloodRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BloodRequestRepository extends JpaRepository<BloodRequest, Long> {
    List<BloodRequest> findAllByFacility_IdOrderByRequestedAtDesc(Long facilityId);
    List<BloodRequest> findTop100ByOrderByRequestedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM BloodRequest request WHERE request.id = :id")
    Optional<BloodRequest> findByIdForUpdate(@Param("id") Long id);
}
