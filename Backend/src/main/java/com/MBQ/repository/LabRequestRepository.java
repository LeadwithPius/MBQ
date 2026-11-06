package com.MBQ.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.MBQ.model.LabRequest;
import com.MBQ.model.LabRequestStatus;

import jakarta.persistence.LockModeType;

public interface LabRequestRepository extends JpaRepository<LabRequest, Long> {

    /** Locks the row so two lab staff can't record results for the same request at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM LabRequest r WHERE r.id = :id")
    Optional<LabRequest> findByIdForUpdate(@Param("id") Long id);

    List<LabRequest> findByConsultation_IdOrderByRequestedAtAscIdAsc(Long consultationId);

    List<LabRequest> findTop100ByPatient_IdOrderByRequestedAtDescIdDesc(Long patientId);

    List<LabRequest> findTop200ByFacility_IdAndStatusInOrderByRequestedAtAscIdAsc(
            Long facilityId, Collection<LabRequestStatus> statuses);

    List<LabRequest> findTop200ByStatusInOrderByRequestedAtAscIdAsc(Collection<LabRequestStatus> statuses);

    /** Duplicate protection: is this test already open for the consultation? (case-insensitive) */
    boolean existsByConsultation_IdAndTestNameIgnoreCaseAndStatusIn(
            Long consultationId, String testName, Collection<LabRequestStatus> statuses);

    long countByRequestedAtGreaterThanEqualAndRequestedAtLessThan(
            java.time.OffsetDateTime from, java.time.OffsetDateTime to);

    long countByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            LabRequestStatus status, java.time.OffsetDateTime from, java.time.OffsetDateTime to);
}
