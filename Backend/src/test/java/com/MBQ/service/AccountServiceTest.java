package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.AuthDtos.ChangePasswordRequest;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.StaffUserRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private StaffAccessService staffAccess;
    @Mock private StaffUserRepository staffUsers;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private AccountService service;
    private StaffUser nurse;

    @BeforeEach
    void setUp() {
        service = new AccountService(staffAccess, staffUsers, encoder);
        nurse = new StaffUser();
        nurse.setId(2L);
        nurse.setUsername("nurse");
        nurse.setPasswordHash(encoder.encode("Password123!"));
        when(staffAccess.currentStaff()).thenReturn(nurse);
    }

    @Test
    void changePassword_withCorrectCurrentPassword_storesANewHash() {
        service.changePassword(new ChangePasswordRequest("Password123!", "NewSecret2026"));

        assertTrue(encoder.matches("NewSecret2026", nurse.getPasswordHash()));
        verify(staffUsers).save(nurse);
    }

    @Test
    void changePassword_wrongCurrentPassword_isRejected() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.changePassword(new ChangePasswordRequest("wrong", "NewSecret2026")));

        assertEquals(400, error.getStatusCode().value());
        verify(staffUsers, never()).save(any());
    }

    @Test
    void changePassword_sameAsCurrent_isRejected() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.changePassword(new ChangePasswordRequest("Password123!", "Password123!")));

        assertEquals(400, error.getStatusCode().value());
        verify(staffUsers, never()).save(any());
    }
}
