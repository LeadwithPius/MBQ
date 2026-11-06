package com.MBQ.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.MBQ.dto.StaffDtos.CreateStaffRequest;
import com.MBQ.dto.StaffDtos.ResetPasswordRequest;
import com.MBQ.dto.StaffDtos.SetActiveRequest;
import com.MBQ.dto.StaffDtos.StaffResponse;
import com.MBQ.service.StaffAdminService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
public class StaffAdminController {

    private final StaffAdminService staffAdminService;

    public StaffAdminController(StaffAdminService staffAdminService) {
        this.staffAdminService = staffAdminService;
    }

    @GetMapping
    public List<StaffResponse> list() {
        return staffAdminService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse create(@Valid @RequestBody CreateStaffRequest request) {
        return staffAdminService.create(request);
    }

    @PatchMapping("/{id}/active")
    public StaffResponse setActive(@PathVariable Long id, @Valid @RequestBody SetActiveRequest request) {
        return staffAdminService.setActive(id, request.active());
    }

    @PatchMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        staffAdminService.resetPassword(id, request.newPassword());
    }
}
