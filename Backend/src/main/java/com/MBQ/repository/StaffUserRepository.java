package com.MBQ.repository;

import com.MBQ.model.StaffUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {

    Optional<StaffUser> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    java.util.List<StaffUser> findAllByOrderByNameAsc();
}