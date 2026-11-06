package com.MBQ.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.MBQ.dto.DonationDtos.DonationResponse;
import com.MBQ.dto.DonationDtos.RecordDonationRequest;
import com.MBQ.dto.DonationDtos.ScreenDonationRequest;
import com.MBQ.service.DonationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/blood-bank/donations")
@PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF', 'NURSE')")
public class DonationController {

    private final DonationService donationService;

    public DonationController(DonationService donationService) {
        this.donationService = donationService;
    }

    @GetMapping
    public List<DonationResponse> recent() {
        return donationService.recent();
    }

    @GetMapping("/donors/{donorId}")
    public List<DonationResponse> byDonor(@PathVariable Long donorId) {
        return donationService.byDonor(donorId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DonationResponse record(@Valid @RequestBody RecordDonationRequest request) {
        return donationService.record(request);
    }

    /** Screening decides whether the units may enter stock — a lab job. */
    @PatchMapping("/{id}/screen")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public DonationResponse screen(@PathVariable Long id, @Valid @RequestBody ScreenDonationRequest request) {
        return donationService.screen(id, request);
    }
}
