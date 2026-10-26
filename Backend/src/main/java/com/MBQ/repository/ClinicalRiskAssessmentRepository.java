package com.MBQ.repository;

import com.MBQ.model.ClinicalRiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClinicalRiskAssessmentRepository extends JpaRepository<ClinicalRiskAssessment, Long> {
    List<ClinicalRiskAssessment> findByObservation_IdOrderByCreatedAtDesc(Long observationId);
}
