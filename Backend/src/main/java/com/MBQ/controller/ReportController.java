package com.MBQ.controller;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.MBQ.dto.ReportDtos.DailyReportResponse;
import com.MBQ.dto.ReportDtos.DepartmentResponse;
import com.MBQ.model.Department;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.DepartmentRepository;
import com.MBQ.service.ReportService;
import com.MBQ.service.StaffAccessService;

/**
 * GET /api/reports/daily  (FR8, admin only)
 * GET /api/departments    (department list; admin uses it to pick which queue to run)
 */
@RestController
@RequestMapping("/api")
public class ReportController {

    private final ReportService reports;
    private final DepartmentRepository departments;
    private final StaffAccessService staffAccess;

    public ReportController(ReportService reports, DepartmentRepository departments,
                            StaffAccessService staffAccess) {
        this.reports = reports;
        this.departments = departments;
        this.staffAccess = staffAccess;
    }

    /** ?date=2026-11-11 (optional, defaults to today). */
    @GetMapping("/reports/daily")
    @PreAuthorize("hasRole('ADMIN')")
    public DailyReportResponse daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reports.daily(date);
    }

    /** Admin: every department. Other staff: departments of their own facility. */
    @GetMapping("/departments")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<DepartmentResponse> listDepartments() {
        StaffUser staff = staffAccess.currentStaff();
        Long facilityId = staffAccess.isAdmin(staff) ? null : staffAccess.facilityIdOf(staff);

        return departments.findAll().stream()
                .filter(d -> facilityId == null || facilityId.equals(d.getFacility().getId()))
                .sorted(Comparator
                        .comparing((Department d) -> d.getFacility().getId())
                        .thenComparing(Department::getName))
                .map(d -> new DepartmentResponse(d.getId(), d.getName(),
                        d.getFacility().getId(), d.getFacility().getName()))
                .toList();
    }
}
