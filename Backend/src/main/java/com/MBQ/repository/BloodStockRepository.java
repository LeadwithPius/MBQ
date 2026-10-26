package com.MBQ.repository;

import com.MBQ.model.BloodStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BloodStockRepository extends JpaRepository<BloodStock, Long> {

    List<BloodStock> findByFacilityId(Long facilityId);

    Optional<BloodStock> findByFacilityIdAndBloodGroup(
            Long facilityId,
            com.MBQ.model.BloodGroup bloodGroup
    );
}