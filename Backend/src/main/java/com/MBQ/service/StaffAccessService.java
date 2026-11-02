package com.MBQ.service;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.model.Department;
import com.MBQ.model.Patient;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.Role;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.StaffUserRepository;

/**
 * Centralizes staff identity and facility/department authorization.
 *
 * ADMIN accounts have system-wide access and may have no department.
 * Every non-admin account must have an assigned department and facility.
 */
@Service
public class StaffAccessService {

    private final StaffUserRepository staffUsers;

    public StaffAccessService(StaffUserRepository staffUsers) {
        this.staffUsers = staffUsers;
    }

    public StaffUser currentStaff() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated staff identity is required");
        }

        StaffUser staff = staffUsers.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated staff account was not found"));

        if (!staff.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This staff account is inactive");
        }

        if (staff.getRole() != Role.ADMIN
                && (staff.getDepartment() == null
                || staff.getDepartment().getFacility() == null)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your staff account must be assigned to a department and facility");
        }

        return staff;
    }

    public boolean isAdmin(StaffUser staff) {
        return staff != null && staff.getRole() == Role.ADMIN;
    }

    public Long facilityIdOf(StaffUser staff) {
        if (isAdmin(staff)) {
            return null;
        }
        if (staff.getDepartment() == null
                || staff.getDepartment().getFacility() == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your staff account has no assigned facility");
        }
        return staff.getDepartment().getFacility().getId();
    }

    public void requireFacilityAccess(Long resourceFacilityId) {
        StaffUser staff = currentStaff();
        if (isAdmin(staff)) {
            return;
        }
        if (resourceFacilityId == null
                || !Objects.equals(facilityIdOf(staff), resourceFacilityId)) {
            throw forbidden();
        }
    }

    /**
     * Queue operations are department-scoped for non-admin staff.
     */
    public void requireDepartmentAccess(Department department) {
        if (department == null || department.getFacility() == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Department not found");
        }

        StaffUser staff = currentStaff();
        if (isAdmin(staff)) {
            return;
        }

        Long assignedDepartmentId = staff.getDepartment().getId();
        if (!Objects.equals(assignedDepartmentId, department.getId())) {
            throw forbidden();
        }

        requireFacilityAccess(department.getFacility().getId());
    }

    public void requirePatientFacilityAccess(Patient patient) {
        if (patient == null || patient.getFacility() == null) {
            throw forbidden();
        }
        requireFacilityAccess(patient.getFacility().getId());
    }

    public void requireTicketDepartmentAccess(QueueTicket ticket) {
        if (ticket == null || ticket.getDepartment() == null) {
            throw forbidden();
        }
        requireDepartmentAccess(ticket.getDepartment());
        requirePatientFacilityAccess(ticket.getPatient());
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You are not authorized to access this facility or department");
    }
}
