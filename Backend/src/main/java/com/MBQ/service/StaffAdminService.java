package com.MBQ.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.StaffDtos.CreateStaffRequest;
import com.MBQ.dto.StaffDtos.StaffResponse;
import com.MBQ.model.Department;
import com.MBQ.model.Role;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.DepartmentRepository;
import com.MBQ.repository.StaffUserRepository;

/**
 * Admin-only staff account management: create accounts, deactivate/reactivate them and
 * reset passwords. Accounts are never deleted, so audit trails (who recorded what) stay intact.
 */
@Service
public class StaffAdminService {

    private final StaffUserRepository staffUsers;
    private final DepartmentRepository departments;
    private final StaffAccessService staffAccess;
    private final PasswordEncoder passwordEncoder;

    public StaffAdminService(StaffUserRepository staffUsers, DepartmentRepository departments,
                             StaffAccessService staffAccess, PasswordEncoder passwordEncoder) {
        this.staffUsers = staffUsers;
        this.departments = departments;
        this.staffAccess = staffAccess;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> list() {
        requireAdmin();
        return staffUsers.findAllByOrderByNameAsc().stream().map(StaffAdminService::toResponse).toList();
    }

    @Transactional
    public StaffResponse create(CreateStaffRequest request) {
        requireAdmin();
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (staffUsers.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The username \"" + username + "\" is already taken");
        }
        if (request.temporaryPassword() == null || request.temporaryPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The password must be at least 8 characters");
        }

        Department department = null;
        if (request.role() != Role.ADMIN) {
            if (request.departmentId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Every non-admin account must be assigned to a department");
            }
            department = departments.findById(request.departmentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        }

        StaffUser staff = new StaffUser();
        staff.setName(request.name().trim());
        staff.setUsername(username);
        staff.setRole(request.role());
        staff.setDepartment(department);
        staff.setActive(true);
        staff.setPasswordHash(passwordEncoder.encode(request.temporaryPassword()));
        return toResponse(staffUsers.save(staff));
    }

    @Transactional
    public StaffResponse setActive(Long id, boolean active) {
        StaffUser admin = requireAdmin();
        StaffUser staff = find(id);
        if (!active && staff.getId().equals(admin.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot deactivate your own account");
        }
        staff.setActive(active);
        return toResponse(staffUsers.save(staff));
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        requireAdmin();
        if (newPassword == null || newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The password must be at least 8 characters");
        }
        StaffUser staff = find(id);
        staff.setPasswordHash(passwordEncoder.encode(newPassword));
        staffUsers.save(staff);
    }

    private StaffUser find(Long id) {
        return staffUsers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff account not found"));
    }

    /** Defence in depth: the controller already restricts to ADMIN. */
    private StaffUser requireAdmin() {
        StaffUser current = staffAccess.currentStaff();
        if (!staffAccess.isAdmin(current)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an administrator can manage staff accounts");
        }
        return current;
    }

    static StaffResponse toResponse(StaffUser s) {
        Department d = s.getDepartment();
        return new StaffResponse(s.getId(), s.getName(), s.getUsername(), s.getRole(),
                d == null ? null : d.getId(), d == null ? null : d.getName(), s.isActive());
    }
}
