package com.MBQ.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.AuthDtos.ChangePasswordRequest;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.StaffUserRepository;

/** Self-service account actions for the signed-in staff member. */
@Service
public class AccountService {

    private final StaffAccessService staffAccess;
    private final StaffUserRepository staffUsers;
    private final PasswordEncoder passwordEncoder;

    public AccountService(StaffAccessService staffAccess, StaffUserRepository staffUsers,
                          PasswordEncoder passwordEncoder) {
        this.staffAccess = staffAccess;
        this.staffUsers = staffUsers;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        StaffUser staff = staffAccess.currentStaff();
        if (!passwordEncoder.matches(request.currentPassword(), staff.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        String next = request.newPassword();
        if (next == null || next.length() < 8 || next.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The new password must be at least 8 characters");
        }
        if (next.equals(request.currentPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The new password must be different from the current one");
        }
        staff.setPasswordHash(passwordEncoder.encode(next));
        staffUsers.save(staff);
    }
}
