package com.MBQ.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.MBQ.model.BloodGroup;
import com.MBQ.model.Donor;

public interface DonorRepository extends JpaRepository<Donor, Long> {

    List<Donor> findAllByOrderByNameAsc();

    List<Donor> findByBloodGroupOrderByNameAsc(BloodGroup bloodGroup);

    List<Donor> findAllByFacilityIdOrderByNameAsc(Long facilityId);

    List<Donor> findByFacilityIdAndBloodGroupOrderByNameAsc(Long facilityId, BloodGroup bloodGroup);

    List<Donor> findByFacilityIdAndBloodGroupAndEligibleTrueOrderByNameAsc(Long facilityId, BloodGroup bloodGroup);

    /** Donors who can be alerted for a shortage of this blood group. */
    List<Donor> findByBloodGroupAndEligibleTrueOrderByNameAsc(BloodGroup bloodGroup);
}
