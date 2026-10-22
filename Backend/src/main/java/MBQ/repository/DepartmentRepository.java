package com.MBQ.repository;

import com.MBQ.model.Department;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /**
     * Loads a department and locks its row (SELECT ... FOR UPDATE) until the transaction ends.
     * Two receptionists issuing tickets at the same moment are processed one after the other,
     * so they can never get the same ticket number, and two nurses pressing "call next"
     * can never call the same patient.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Department d where d.id = :id")
    Optional<Department> findByIdForUpdate(@Param("id") Long id);
}
