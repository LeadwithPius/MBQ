package com.MBQ.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.MBQ.dto.BloodBankDtos.AlertReplyRequest;
import com.MBQ.dto.BloodBankDtos.AlertResponse;
import com.MBQ.dto.BloodBankDtos.BatchResponse;
import com.MBQ.dto.BloodBankDtos.BloodRequestResponse;
import com.MBQ.dto.BloodBankDtos.DonorAlertResult;
import com.MBQ.dto.BloodBankDtos.DonorRequest;
import com.MBQ.dto.BloodBankDtos.DonorResponse;
import com.MBQ.dto.BloodBankDtos.MovementResponse;
import com.MBQ.dto.BloodBankDtos.StockLevelResponse;
import com.MBQ.dto.BloodBankDtos.ThresholdRequest;
import com.MBQ.model.BloodGroup;
import com.MBQ.service.BloodBankService;

import jakarta.validation.Valid;

/**
 * Blood bank screen: stock levels and low-stock alerts (FR5, FR6),
 * donors and donor alerts (FR7), and patients flagged as needing blood.
 * Receiving and issuing units: {@link BloodInventoryController}.
 */
@RestController
@RequestMapping("/api/blood-bank")
public class BloodBankController {

    private final BloodBankService bloodBank;

    public BloodBankController(BloodBankService bloodBank) {
        this.bloodBank = bloodBank;
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF', 'DOCTOR', 'NURSE')")
    public List<StockLevelResponse> listStock() {
        return bloodBank.listStock();
    }

    @GetMapping("/stock/{stockId}/batches")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF', 'DOCTOR', 'NURSE')")
    public List<BatchResponse> listBatches(@PathVariable Long stockId) {
        return bloodBank.listBatches(stockId);
    }

    @GetMapping("/stock/{stockId}/movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF', 'DOCTOR')")
    public List<MovementResponse> listMovements(@PathVariable Long stockId) {
        return bloodBank.listMovements(stockId);
    }

    @PatchMapping("/stock/{stockId}/threshold")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public StockLevelResponse updateThreshold(
            @PathVariable Long stockId,
            @Valid @RequestBody ThresholdRequest request) {
        return bloodBank.updateThreshold(stockId, request);
    }

    @GetMapping("/donors")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public List<DonorResponse> listDonors(@RequestParam(required = false) BloodGroup bloodGroup) {
        return bloodBank.listDonors(bloodGroup);
    }

    @PostMapping("/donors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public DonorResponse registerDonor(@Valid @RequestBody DonorRequest request) {
        return bloodBank.registerDonor(request);
    }

    @PostMapping("/stock/{stockId}/donor-alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public DonorAlertResult notifyDonors(@PathVariable Long stockId) {
        return bloodBank.notifyDonors(stockId);
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public List<AlertResponse> listAlerts() {
        return bloodBank.listAlerts();
    }

    @PatchMapping("/alerts/{alertId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF')")
    public AlertResponse replyToAlert(
            @PathVariable Long alertId,
            @Valid @RequestBody AlertReplyRequest request) {
        return bloodBank.replyToAlert(alertId, request);
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_STAFF', 'DOCTOR', 'NURSE')")
    public List<BloodRequestResponse> listBloodRequests() {
        return bloodBank.listBloodRequests();
    }
}
