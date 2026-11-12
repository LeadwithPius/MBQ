package com.MBQ.repository;

import com.MBQ.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    java.util.List<Patient> findByFacility_IdOrderByIdDesc(Long facilityId);
    boolean existsByNrcNumberIgnoreCase(String nrcNumber);
}
