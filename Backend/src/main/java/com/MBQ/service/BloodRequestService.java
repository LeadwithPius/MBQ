package com.MBQ.service;

import java.time.OffsetDateTime;
import java.util.List;

import com.MBQ.dto.BloodInventoryDtos.IssueRequest;
import com.MBQ.dto.BloodInventoryDtos.InventoryOperationResponse;
import com.MBQ.dto.BloodRequestDtos.*;
import com.MBQ.model.*;
import com.MBQ.repository.BloodRequestRepository;
import com.MBQ.repository.BloodStockRepository;
import com.MBQ.repository.PatientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BloodRequestService {
    private final BloodRequestRepository requests;
    private final PatientRepository patients;
    private final BloodStockRepository stocks;
    private final StaffAccessService staffAccess;
    private final BloodInventoryService inventory;

    public BloodRequestService(BloodRequestRepository requests, PatientRepository patients,
            BloodStockRepository stocks, StaffAccessService staffAccess, BloodInventoryService inventory) {
        this.requests = requests;
        this.patients = patients;
        this.stocks = stocks;
        this.staffAccess = staffAccess;
        this.inventory = inventory;
    }

    @Transactional
    public BloodRequestResponse create(CreateBloodRequest request) {
        StaffUser actor = staffAccess.currentStaff();
        if (actor.getRole() != Role.DOCTOR && actor.getRole() != Role.NURSE) {
            throw forbidden("Only doctors and nurses can request blood for a patient.");
        }
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found."));
        staffAccess.requirePatientFacilityAccess(patient);
        if (!patient.getFacility().getId().equals(staffAccess.facilityIdOf(actor))) {
            throw forbidden("The patient must belong to your facility.");
        }

        BloodRequest bloodRequest = new BloodRequest();
        bloodRequest.setFacility(patient.getFacility());
        bloodRequest.setPatient(patient);
        bloodRequest.setBloodGroup(patient.getBloodGroup());
        bloodRequest.setUnits(request.units());
        bloodRequest.setPriorityLevel(request.priorityLevel() == null ? PriorityLevel.NORMAL : request.priorityLevel());
        bloodRequest.setReason(request.reason().trim());
        bloodRequest.setRequestedBy(actor);
        bloodRequest.setStatus(BloodRequestStatus.PENDING);
        return toResponse(requests.save(bloodRequest));
    }

    @Transactional(readOnly = true)
    public List<BloodRequestResponse> list() {
        StaffUser actor = staffAccess.currentStaff();
        List<BloodRequest> found = actor.getRole() == Role.ADMIN
                ? requests.findTop100ByOrderByRequestedAtDesc()
                : requests.findAllByFacility_IdOrderByRequestedAtDesc(staffAccess.facilityIdOf(actor));
        return found.stream().map(BloodRequestService::toResponse).toList();
    }

    @Transactional
    public BloodRequestResponse review(Long id, ReviewBloodRequest request) {
        StaffUser actor = staffAccess.currentStaff();
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.LAB_STAFF) {
            throw forbidden("Only blood-bank staff can approve or reject a blood request.");
        }
        BloodRequest bloodRequest = lockRequest(id);
        staffAccess.requireFacilityAccess(bloodRequest.getFacility().getId());
        if (bloodRequest.getStatus() != BloodRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending requests can be reviewed.");
        }
        if (request.status() != BloodRequestStatus.APPROVED && request.status() != BloodRequestStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A request can only be approved or rejected during review.");
        }
        bloodRequest.setStatus(request.status());
        bloodRequest.setReviewedBy(actor);
        bloodRequest.setReviewedAt(OffsetDateTime.now());
        bloodRequest.setReviewNotes(request.notes() == null ? null : request.notes().trim());
        return toResponse(requests.save(bloodRequest));
    }

    @Transactional
    public BloodRequestResponse fulfil(Long id, FulfilBloodRequest request, String idempotencyKey) {
        StaffUser actor = staffAccess.currentStaff();
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.LAB_STAFF) {
            throw forbidden("Only blood-bank staff can fulfil a blood request.");
        }
        BloodRequest bloodRequest = lockRequest(id);
        staffAccess.requireFacilityAccess(bloodRequest.getFacility().getId());
        if (bloodRequest.getStatus() != BloodRequestStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approve the request before fulfilling it.");
        }
        BloodStock stock = stocks.findById(request.stockId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blood stock record not found."));
        staffAccess.requireFacilityAccess(stock.getFacility().getId());
        if (!stock.getFacility().getId().equals(bloodRequest.getFacility().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock and patient must belong to the same facility.");
        }
        InventoryOperationResponse issued = inventory.issue(
                new IssueRequest(stock.getId(), bloodRequest.getUnits(),
                        "Blood request #" + bloodRequest.getId() + ": " + bloodRequest.getReason(),
                        bloodRequest.getPatient().getId()),
                idempotencyKey);
        bloodRequest.setStatus(BloodRequestStatus.FULFILLED);
        bloodRequest.setStock(stock);
        bloodRequest.setInventoryOperationId(issued.operationId());
        bloodRequest.setFulfilledAt(OffsetDateTime.now());
        return toResponse(requests.save(bloodRequest));
    }

    private BloodRequest lockRequest(Long id) {
        return requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blood request not found."));
    }

    private static BloodRequestResponse toResponse(BloodRequest r) {
        return new BloodRequestResponse(r.getId(), r.getPatient().getId(), r.getPatient().getName(),
                r.getBloodGroup(), r.getUnits(), r.getPriorityLevel(), r.getReason(), r.getStatus(),
                r.getRequestedBy().getName(), r.getRequestedAt(), r.getReviewedBy() == null ? null : r.getReviewedBy().getName(),
                r.getReviewedAt(), r.getReviewNotes(), r.getStock() == null ? null : r.getStock().getId(),
                r.getInventoryOperationId(), r.getFulfilledAt());
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
