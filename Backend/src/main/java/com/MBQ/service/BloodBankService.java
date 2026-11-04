package com.MBQ.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
import com.MBQ.model.Alert;
import com.MBQ.model.BloodBatchVerificationStatus;
import com.MBQ.model.BloodGroup;
import com.MBQ.model.BloodStock;
import com.MBQ.model.BloodStockBatch;
import com.MBQ.model.BloodStockMovement;
import com.MBQ.model.Donor;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.StaffUser;
import com.MBQ.model.TicketStatus;
import com.MBQ.repository.AlertRepository;
import com.MBQ.repository.BloodStockBatchRepository;
import com.MBQ.repository.BloodStockMovementRepository;
import com.MBQ.repository.BloodStockRepository;
import com.MBQ.repository.DonorRepository;
import com.MBQ.repository.QueueTicketRepository;

/**
 * Read side of the blood bank plus donors and donor alerts (FR5-FR7).
 * Receiving and issuing blood stay in {@link BloodInventoryService}.
 *
 * Non-admin staff only see their own facility; admin sees every facility.
 */
@Service
public class BloodBankService {

    /** Issuable units expiring within this many days are flagged on the screen. */
    static final int EXPIRING_SOON_DAYS = 7;

    static final String PENDING = "PENDING";

    private static final List<TicketStatus> ACTIVE_STATUSES =
            List.of(TicketStatus.WAITING, TicketStatus.IN_PROGRESS);

    private final BloodStockRepository stocks;
    private final BloodStockBatchRepository batches;
    private final BloodStockMovementRepository movements;
    private final DonorRepository donors;
    private final AlertRepository alerts;
    private final QueueTicketRepository tickets;
    private final StaffAccessService staffAccess;

    public BloodBankService(
            BloodStockRepository stocks,
            BloodStockBatchRepository batches,
            BloodStockMovementRepository movements,
            DonorRepository donors,
            AlertRepository alerts,
            QueueTicketRepository tickets,
            StaffAccessService staffAccess) {
        this.stocks = stocks;
        this.batches = batches;
        this.movements = movements;
        this.donors = donors;
        this.alerts = alerts;
        this.tickets = tickets;
        this.staffAccess = staffAccess;
    }

    // ---------- stock (FR5, FR6) ----------

    /** Stock level of every blood group the caller may see, with low-stock flags. */
    @Transactional(readOnly = true)
    public List<StockLevelResponse> listStock() {
        StaffUser staff = staffAccess.currentStaff();
        List<BloodStock> visible = new ArrayList<>(staffAccess.isAdmin(staff)
                ? stocks.findAll()
                : stocks.findByFacilityId(staffAccess.facilityIdOf(staff)));

        visible.sort(Comparator
                .comparing((BloodStock s) -> s.getFacility().getId())
                .thenComparing(s -> s.getBloodGroup().ordinal()));

        return visible.stream().map(this::toStockLevel).toList();
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> listBatches(Long stockId) {
        BloodStock stock = requireStock(stockId);
        LocalDate today = LocalDate.now();
        return batches.findAllForStock(stock.getId()).stream()
                .map(batch -> toBatch(batch, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> listMovements(Long stockId) {
        BloodStock stock = requireStock(stockId);
        return movements.findTop20ByStock_IdOrderByCreatedAtDescIdDesc(stock.getId()).stream()
                .map(this::toMovement)
                .toList();
    }

    @Transactional
    public StockLevelResponse updateThreshold(Long stockId, ThresholdRequest request) {
        BloodStock stock = requireStock(stockId);
        stock.setLowStockThreshold(request.lowStockThreshold());
        return toStockLevel(stocks.save(stock));
    }

    // ---------- donors and alerts (FR7) ----------

    @Transactional(readOnly = true)
    public List<DonorResponse> listDonors(BloodGroup bloodGroup) {
        List<Donor> found = bloodGroup == null
                ? donors.findAllByOrderByNameAsc()
                : donors.findByBloodGroupOrderByNameAsc(bloodGroup);
        return found.stream().map(BloodBankService::toDonor).toList();
    }

    @Transactional
    public DonorResponse registerDonor(DonorRequest request) {
        Donor donor = new Donor();
        donor.setName(request.name().trim());
        donor.setBloodGroup(request.bloodGroup());
        donor.setContact(trimToNull(request.contact()));
        donor.setEligible(request.eligible() == null || request.eligible());
        return toDonor(donors.save(donor));
    }

    /**
     * Alerts every eligible donor of the stock's blood group (simulated: recorded,
     * no real SMS). Donors who already have an unanswered alert for this stock are skipped.
     */
    @Transactional
    public DonorAlertResult notifyDonors(Long stockId) {
        BloodStock stock = requireStock(stockId);

        List<AlertResponse> created = new ArrayList<>();
        int skipped = 0;
        for (Donor donor : donors.findByBloodGroupAndEligibleTrueOrderByNameAsc(stock.getBloodGroup())) {
            if (alerts.existsByStock_IdAndDonor_IdAndResponseStatus(stock.getId(), donor.getId(), PENDING)) {
                skipped++;
                continue;
            }
            Alert alert = new Alert();
            alert.setStock(stock);
            alert.setDonor(donor);
            created.add(toAlert(alerts.save(alert)));
        }

        return new DonorAlertResult(stock.getId(), stock.getBloodGroup(),
                created.size(), skipped, created);
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> listAlerts() {
        StaffUser staff = staffAccess.currentStaff();
        List<Alert> found = staffAccess.isAdmin(staff)
                ? alerts.findTop50ByOrderByDateSentDescIdDesc()
                : alerts.findTop50ByStock_Facility_IdOrderByDateSentDescIdDesc(staffAccess.facilityIdOf(staff));
        return found.stream().map(BloodBankService::toAlert).toList();
    }

    /** Records the donor's answer. Only an unanswered alert can be answered. */
    @Transactional
    public AlertResponse replyToAlert(Long alertId, AlertReplyRequest request) {
        Alert alert = alerts.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        staffAccess.requireFacilityAccess(alert.getStock().getFacility().getId());

        if (!PENDING.equals(alert.getResponseStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This alert has already been answered (" + alert.getResponseStatus() + ")");
        }
        alert.setResponseStatus(request.responseStatus());
        return toAlert(alerts.save(alert));
    }

    // ---------- emergency blood branch ----------

    /** Patients still in the queue whom triage flagged as needing blood. */
    @Transactional(readOnly = true)
    public List<BloodRequestResponse> listBloodRequests() {
        StaffUser staff = staffAccess.currentStaff();
        List<QueueTicket> found = staffAccess.isAdmin(staff)
                ? tickets.findByRequiresBloodTrueAndStatusInOrderByTimeIssuedAsc(ACTIVE_STATUSES)
                : tickets.findByRequiresBloodTrueAndStatusInAndDepartment_Facility_IdOrderByTimeIssuedAsc(
                        ACTIVE_STATUSES, staffAccess.facilityIdOf(staff));

        return found.stream()
                .map(t -> new BloodRequestResponse(
                        t.getId(), t.getTicketNumber(),
                        t.getPatient().getId(), t.getPatient().getName(), t.getPatient().getBloodGroup(),
                        t.getDepartment().getName(), t.getPriorityLevel(), t.getStatus(), t.getTimeIssued()))
                .toList();
    }

    // ---------- helpers ----------

    private BloodStock requireStock(Long stockId) {
        BloodStock stock = stocks.findById(stockId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Blood stock record not found"));
        staffAccess.requireFacilityAccess(stock.getFacility().getId());
        return stock;
    }

    StockLevelResponse toStockLevel(BloodStock stock) {
        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(EXPIRING_SOON_DAYS);
        int issuable = 0;
        int expiringSoon = 0;
        for (BloodStockBatch batch : batches.findAllForStock(stock.getId())) {
            if (isIssuable(batch, today)) {
                issuable += batch.getRemainingQuantity();
                if (!batch.getExpiryDate().isAfter(soon)) {
                    expiringSoon += batch.getRemainingQuantity();
                }
            }
        }
        return new StockLevelResponse(
                stock.getId(),
                stock.getFacility().getId(),
                stock.getBloodGroup(),
                stock.getUnitsAvailable(),
                stock.getLowStockThreshold(),
                stock.getUnitsAvailable() <= stock.getLowStockThreshold(),
                issuable,
                expiringSoon,
                stock.getLastUpdated());
    }

    private static boolean isIssuable(BloodStockBatch batch, LocalDate today) {
        return batch.getVerificationStatus() == BloodBatchVerificationStatus.VERIFIED
                && batch.getExpiryDate() != null
                && !batch.getExpiryDate().isBefore(today)
                && batch.getRemainingQuantity() > 0;
    }

    private static BatchResponse toBatch(BloodStockBatch batch, LocalDate today) {
        boolean expired = batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today);
        boolean expiringSoon = !expired && batch.getExpiryDate() != null
                && !batch.getExpiryDate().isAfter(today.plusDays(EXPIRING_SOON_DAYS));
        return new BatchResponse(
                batch.getId(),
                batch.getBatchReference(),
                batch.getExpiryDate(),
                batch.getOpeningQuantity(),
                batch.getRemainingQuantity(),
                batch.getVerificationStatus().name(),
                batch.getSourceReference(),
                expired,
                expiringSoon);
    }

    private MovementResponse toMovement(BloodStockMovement movement) {
        return new MovementResponse(
                movement.getId(),
                movement.getMovementType().name(),
                movement.getUnitsChanged(),
                movement.getQuantityBefore(),
                movement.getQuantityAfter(),
                movement.getReason(),
                movement.getBatch() == null ? null : movement.getBatch().getBatchReference(),
                movement.getRecordedBy() == null ? null : movement.getRecordedBy().getName(),
                movement.getCreatedAt());
    }

    private static DonorResponse toDonor(Donor donor) {
        return new DonorResponse(donor.getId(), donor.getName(), donor.getBloodGroup(),
                donor.getContact(), donor.isEligible());
    }

    private static AlertResponse toAlert(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getStock().getId(),
                alert.getStock().getBloodGroup(),
                alert.getDonor().getId(),
                alert.getDonor().getName(),
                alert.getDonor().getContact(),
                alert.getDateSent(),
                alert.getResponseStatus());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
