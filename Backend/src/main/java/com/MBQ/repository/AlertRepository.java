package com.MBQ.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.MBQ.model.Alert;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    /** True if this donor already has an unanswered alert for this stock. */
    boolean existsByStock_IdAndDonor_IdAndResponseStatus(Long stockId, Long donorId, String responseStatus);

    List<Alert> findTop50ByOrderByDateSentDescIdDesc();

    /** Donor alerts sent in [from, to) (daily report). */
    List<Alert> findByDateSentGreaterThanEqualAndDateSentLessThan(
            java.time.LocalDateTime from, java.time.LocalDateTime to);

    List<Alert> findTop50ByStock_Facility_IdOrderByDateSentDescIdDesc(Long facilityId);
}
