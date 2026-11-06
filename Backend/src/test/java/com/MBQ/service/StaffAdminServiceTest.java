package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.StaffDtos.CreateStaffRequest;
import com.MBQ.dto.StaffDtos.StaffResponse;
import com.MBQ.model.Department;
import com.MBQ.model.Role;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.DepartmentRepository;
import com.MBQ.repository.StaffUserRepository;

@ExtendWith(MockitoExtension.class)
class StaffAdminServiceTest {

    @Mock private StaffUserRepository staffUsers;
    @Mock private DepartmentRepository departments;
    @Mock private StaffAccessService staffAccess;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private StaffAdminService service;
    private StaffUser admin;

    @BeforeEach
    void setUp() {
        service = new StaffAdminService(staffUsers, departments, staffAccess, encoder);
        admin = new StaffUser();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        when(staffAccess.currentStaff()).thenReturn(admin);
        when(staffAccess.isAdmin(admin)).thenReturn(true);
    }

    @Test
    void create_nurseInDepartment_storesHashedPasswordAndLowercaseUsername() {
        Department opd = new Department();
        opd.setId(1L);
        opd.setName("OPD");
        when(staffUsers.existsByUsernameIgnoreCase("grace.nurse")).thenReturn(false);
        when(departments.findById(1L)).thenReturn(Optional.of(opd));
        when(staffUsers.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

        StaffResponse created = service.create(
                new CreateStaffRequest("Grace Mwale", "Grace.Nurse", Role.NURSE, 1L, "TempPass2026"));

        assertEquals("grace.nurse", created.username());
        assertEquals("OPD", created.departmentName());
        assertTrue(created.active());
    }

    @Test
    void create_duplicateUsername_isRejected() {
        when(staffUsers.existsByUsernameIgnoreCase("doctor")).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(new CreateStaffRequest("Dr X", "doctor", Role.DOCTOR, 1L, "TempPass2026")));

        assertEquals(409, error.getStatusCode().value());
        verify(staffUsers, never()).save(any());
    }

    @Test
    void create_nonAdminWithoutDepartment_isRejected() {
        when(staffUsers.existsByUsernameIgnoreCase("new.doc")).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(new CreateStaffRequest("Dr Y", "new.doc", Role.DOCTOR, null, "TempPass2026")));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void setActive_adminCannotDeactivateThemselves() {
        when(staffUsers.findById(1L)).thenReturn(Optional.of(admin));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.setActive(1L, false));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void resetPassword_replacesTheHash() {
        StaffUser nurse = new StaffUser();
        nurse.setId(2L);
        nurse.setPasswordHash(encoder.encode("Password123!"));
        when(staffUsers.findById(2L)).thenReturn(Optional.of(nurse));

        service.resetPassword(2L, "Reset2026!!");

        assertTrue(encoder.matches("Reset2026!!", nurse.getPasswordHash()));
        verify(staffUsers).save(nurse);
    }
}
