package com.MBQ.dto;

import com.MBQ.model.BloodGroup;
import com.MBQ.model.Patient;

/** JSON returned for a patient. We never return the JPA entity directly. */
public record PatientResponse(
        Long id,
        String name,
        Integer age,
        String sex,
        String contact,
        BloodGroup bloodGroup,
        Long facilityId) {

    public static PatientResponse from(Patient p) {
        return new PatientResponse(
                p.getId(),
                p.getName(),
                p.getAge(),
                p.getSex(),
                p.getContact(),
                p.getBloodGroup(),
                p.getFacility() != null ? p.getFacility().getId() : null);
    }
}
