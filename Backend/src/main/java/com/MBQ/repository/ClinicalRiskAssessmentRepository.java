package com.MBQ.repository;

import com.MBQ.model.ClinicalRiskAssessment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ClinicalRiskAssessmentRepository extends JpaRepository<ClinicalRiskAssessment, Long> {
    List<ClinicalRiskAssessment> findByObservation_IdOrderByCreatedAtDesc(Long observationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT assessment
          FROM ClinicalRiskAssessment assessment
          JOIN FETCH assessment.observation observation
          JOIN FETCH observation.patient
         WHERE assessment.id = :assessmentId
        """)
    Optional<ClinicalRiskAssessment> findByIdForUpdate(
            @Param("assessmentId") Long assessmentId);
}
