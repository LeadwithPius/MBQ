package com.MBQ.repository;

import com.MBQ.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    /** Reception search box: up to 50 patients whose name contains the text, A-Z. */
    List<Patient> findTop50ByNameContainingIgnoreCaseOrderByNameAsc(String name);

    /** Most recently registered patients first, capped at 50. */
    List<Patient> findTop50ByOrderByIdDesc();
}
