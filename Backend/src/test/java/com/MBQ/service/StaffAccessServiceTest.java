package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.model.Department;
import com.MBQ.model.Facility;
import com.MBQ.model.Role;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.StaffUserRepository;

@ExtendWith(MockitoExtension.class)
class StaffAccessServiceTest {

    @Mock
    private StaffUserRepository staffUsers;

    private StaffAccessService access;

    @BeforeEach
    void setUp() {
        access = new StaffAccessService(staffUsers);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test-user", "n/a", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void departmentlessAdminCanAccessAnyFacility() {
        StaffUser admin = staff("admin", Role.ADMIN, null);
        when(staffUsers.findByUsername("test-user"))
                .thenReturn(Optional.of(admin));

        assertDoesNotThrow(() -> access.requireFacilityAccess(999L));
    }

    @Test
    void nonAdminCannotAccessAnotherFacility() {
        StaffUser nurse = staff("nurse", Role.NURSE, department(10L, 1L));
        when(staffUsers.findByUsername("test-user"))
                .thenReturn(Optional.of(nurse));

        assertThrows(
                ResponseStatusException.class,
                () -> access.requireFacilityAccess(2L));
    }

    @Test
    void nonAdminCannotAccessAnotherDepartmentInSameFacility() {
        StaffUser doctor = staff("doctor", Role.DOCTOR, department(10L, 1L));
        when(staffUsers.findByUsername("test-user"))
                .thenReturn(Optional.of(doctor));

        Department otherDepartment = department(11L, 1L);

        assertThrows(
                ResponseStatusException.class,
                () -> access.requireDepartmentAccess(otherDepartment));
    }

    @Test
    void assignedStaffCanAccessOwnDepartment() {
        StaffUser receptionist =
                staff("reception", Role.RECEPTIONIST, department(10L, 1L));
        when(staffUsers.findByUsername("test-user"))
                .thenReturn(Optional.of(receptionist));

        assertDoesNotThrow(
                () -> access.requireDepartmentAccess(department(10L, 1L)));
    }

    @Test
    void receptionistCanAssignPatientToAnotherDepartmentInSameFacility() {
        StaffUser receptionist =
                staff("reception", Role.RECEPTIONIST, department(10L, 1L));
        when(staffUsers.findByUsername("test-user"))
                .thenReturn(Optional.of(receptionist));

        assertDoesNotThrow(
                () -> access.requireDepartmentAssignmentAccess(department(11L, 1L)));
    }

    @Test
    void nurseCanAssignPatientToAnotherDepartmentInSameFacility() {
        StaffUser nurse = staff("nurse", Role.NURSE, department(10L, 1L));
        when(staffUsers.findByUsername("test-user"))
                .thenReturn(Optional.of(nurse));

        assertDoesNotThrow(
                () -> access.requireDepartmentAssignmentAccess(department(11L, 1L)));
    }

    @Test
    void departmentAssignmentCannotCrossFacilityBoundary() {
        StaffUser nurse = staff("nurse", Role.NURSE, department(10L, 1L));
        when(staffUsers.findByUsername("test-user"))
                .thenReturn(Optional.of(nurse));

        assertThrows(
                ResponseStatusException.class,
                () -> access.requireDepartmentAssignmentAccess(department(20L, 2L)));
    }

    @Test
    void doctorCannotAssignPatientsToOtherDepartments() {
        StaffUser doctor = staff("doctor", Role.DOCTOR, department(10L, 1L));
        when(staffUsers.findByUsername("test-user"))
                .thenReturn(Optional.of(doctor));

        assertThrows(
                ResponseStatusException.class,
                () -> access.requireDepartmentAssignmentAccess(department(11L, 1L)));
    }

    @Test
    void inactiveStaffCannotAccessResources() {
        StaffUser nurse = staff("nurse", Role.NURSE, department(10L, 1L));
        nurse.setActive(false);
        when(staffUsers.findByUsername("test-user"))
                .thenReturn(Optional.of(nurse));

        assertThrows(
                ResponseStatusException.class,
                () -> access.requireFacilityAccess(1L));
    }

    private StaffUser staff(String username, Role role, Department department) {
        StaffUser staff = new StaffUser();
        staff.setName(username);
        staff.setUsername(username);
        staff.setPasswordHash("not-used");
        staff.setRole(role);
        staff.setActive(true);
        staff.setDepartment(department);
        return staff;
    }

    private Department department(Long departmentId, Long facilityId) {
        Facility facility = new Facility();
        facility.setId(facilityId);

        Department department = new Department();
        department.setId(departmentId);
        department.setFacility(facility);
        return department;
    }
}
