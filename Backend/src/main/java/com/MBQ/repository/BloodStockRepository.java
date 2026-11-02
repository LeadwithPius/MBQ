package com.MBQ.repository;

import com.MBQ.model.BloodStock;
import com.MBQ.model.BloodGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BloodStockRepository extends JpaRepository<BloodStock, Long> {

    List<BloodStock> findByFacilityId(Long facilityId);

    Optional<BloodStock> findByFacilityIdAndBloodGroup(
            Long facilityId,
            BloodGroup bloodGroup
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT stock FROM BloodStock stock WHERE stock.id = :stockId")
    Optional<BloodStock> findByIdForUpdate(@Param("stockId") Long stockId);
}