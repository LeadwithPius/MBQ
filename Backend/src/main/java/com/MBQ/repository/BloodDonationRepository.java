package com.MBQ.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.MBQ.model.BloodDonation;

import jakarta.persistence.LockModeType;

public interface BloodDonationRepository extends JpaRepository<BloodDonation, Long> {

    /** Locks the row so a donation can't be screened twice at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM BloodDonation d WHERE d.id = :id")
    Optional<BloodDonation> findByIdForUpdate(@Param("id") Long id);

    /** Most recent donation by this donor (minimum-interval check). */
    Optional<BloodDonation> findFirstByDonor_IdOrderByCollectedAtDesc(Long donorId);

    List<BloodDonation> findTop100ByOrderByCollectedAtDescIdDesc();

    List<BloodDonation> findTop100ByFacility_IdOrderByCollectedAtDescIdDesc(Long facilityId);

    List<BloodDonation> findByDonor_IdOrderByCollectedAtDesc(Long donorId);

    long countByCollectedAtGreaterThanEqualAndCollectedAtLessThan(OffsetDateTime from, OffsetDateTime to);
}
