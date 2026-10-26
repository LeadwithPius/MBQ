package com.MBQ.repository;

import com.MBQ.model.TriageObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TriageObservationRepository extends JpaRepository<TriageObservation, Long> {
    List<TriageObservation> findByPatient_IdOrderByMeasuredAtDesc(Long patientId);
}
