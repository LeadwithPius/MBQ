package com.MBQ.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.BloodInventoryDtos.ReceiveBatchRequest;
import com.MBQ.dto.DonationDtos.DonationResponse;
import com.MBQ.dto.DonationDtos.RecordDonationRequest;
import com.MBQ.dto.DonationDtos.ScreenDonationRequest;
import com.MBQ.model.BloodDonation;
import com.MBQ.model.BloodStock;
import com.MBQ.model.BloodStockBatch;
import com.MBQ.model.DonationStatus;
import com.MBQ.model.Donor;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.BloodDonationRepository;
import com.MBQ.repository.BloodStockBatchRepository;
import com.MBQ.repository.BloodStockRepository;
import com.MBQ.repository.DonorRepository;

/**
 * Donations (brief Workflow C). Collecting blood does NOT change stock. Only a
 * donation that passes screening enters inventory, through the normal receive
 * path, as a batch awaiting verification — so it is never issuable unchecked.
 */
@Service
public class DonationService {

    /** Minimum gap between whole-blood donations by the same donor. */
    static final int MIN_DAYS_BETWEEN_DONATIONS = 56;
    /** Shelf life of whole blood in CPDA-1 anticoagulant. */
    static final int SHELF_LIFE_DAYS = 35;

    private final BloodDonationRepository donations;
    private final DonorRepository donors;
    private final BloodStockRepository stocks;
    private final BloodStockBatchRepository batches;
    private final BloodInventoryService inventory;
    private final StaffAccessService staffAccess;

    public DonationService(
            BloodDonationRepository donations,
            DonorRepository donors,
            BloodStockRepository stocks,
            BloodStockBatchRepository batches,
            BloodInventoryService inventory,
            StaffAccessService staffAccess) {
        this.donations = donations;
        this.donors = donors;
        this.stocks = stocks;
        this.batches = batches;
        this.inventory = inventory;
        this.staffAccess = staffAccess;
    }

    @Transactional
    public DonationResponse record(RecordDonationRequest request) {
        Donor donor = donors.findById(request.donorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donor not found"));
        if (donor.getFacility() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This donor is not linked to a facility. Re-register the donor from a facility account.");
        }
        staffAccess.requireFacilityAccess(donor.getFacility().getId());
        if (!donor.isEligible()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    donor.getName() + " is marked as not eligible to donate");
        }
        if (request.units() < 1 || request.units() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A donation is 1 or 2 units");
        }

        OffsetDateTime now = OffsetDateTime.now();
        donations.findFirstByDonor_IdOrderByCollectedAtDesc(donor.getId()).ifPresent(last -> {
            long days = Duration.between(last.getCollectedAt(), now).toDays();
            if (days < MIN_DAYS_BETWEEN_DONATIONS) {
                LocalDate nextDate = last.getCollectedAt().toLocalDate().plusDays(MIN_DAYS_BETWEEN_DONATIONS);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        donor.getName() + " last donated " + days + " day(s) ago. Next eligible date: " + nextDate);
            }
        });

        BloodDonation donation = new BloodDonation();
        donation.setDonor(donor);
        donation.setFacility(donor.getFacility());
        donation.setBloodGroup(donor.getBloodGroup());
        donation.setUnits(request.units());
        donation.setCollectedAt(now);
        donation.setCollectedBy(staffAccess.currentStaff());
        donation.setStatus(DonationStatus.COLLECTED);
        return toResponse(donations.saveAndFlush(donation));
    }

    @Transactional
    public DonationResponse screen(Long donationId, ScreenDonationRequest request) {
        BloodDonation donation = donations.findByIdForUpdate(donationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donation not found"));
        staffAccess.requireFacilityAccess(donation.getFacility().getId());
        if (donation.getStatus() != DonationStatus.COLLECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This donation has already been screened (" + donation.getStatus() + ")");
        }

        StaffUser actor = staffAccess.currentStaff();
        String notes = request.notes() == null || request.notes().isBlank() ? null : request.notes().trim();

        if (Boolean.TRUE.equals(request.passed())) {
            LocalDate expiry = donation.getCollectedAt().toLocalDate().plusDays(SHELF_LIFE_DAYS);
            if (expiry.isBefore(LocalDate.now())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "These units expired on " + expiry + ". Record the screening as failed instead.");
            }
            BloodStock stock = stocks.findByFacilityIdAndBloodGroup(
                            donation.getFacility().getId(), donation.getBloodGroup())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "No " + donation.getBloodGroup() + " stock record exists for this facility"));
            String batchReference = "DON-" + donation.getId();
            inventory.receive(new ReceiveBatchRequest(
                    stock.getId(), batchReference, expiry, donation.getUnits(),
                    "Donation #" + donation.getId() + " (" + donation.getDonor().getName() + ")",
                    "Donation passed screening"), "donation-" + donation.getId());
            BloodStockBatch batch = batches.findByStockIdAndBatchReference(stock.getId(), batchReference)
                    .orElseThrow(() -> new IllegalStateException("Donation batch was not created"));
            donation.setBatch(batch);
            donation.setStatus(DonationStatus.PASSED);
        } else {
            donation.setStatus(DonationStatus.FAILED);
        }
        donation.setScreenedBy(actor);
        donation.setScreenedAt(OffsetDateTime.now());
        donation.setScreeningNotes(notes);
        return toResponse(donations.saveAndFlush(donation));
    }

    @Transactional(readOnly = true)
    public List<DonationResponse> recent() {
        Long facilityId = staffAccess.facilityIdOf(staffAccess.currentStaff());
        List<BloodDonation> rows = facilityId == null
                ? donations.findTop100ByOrderByCollectedAtDescIdDesc()
                : donations.findTop100ByFacility_IdOrderByCollectedAtDescIdDesc(facilityId);
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DonationResponse> byDonor(Long donorId) {
        Donor donor = donors.findById(donorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donor not found"));
        staffAccess.requireFacilityAccess(donor.getFacility() == null ? null : donor.getFacility().getId());
        return donations.findByDonor_IdOrderByCollectedAtDesc(donorId).stream().map(this::toResponse).toList();
    }

    DonationResponse toResponse(BloodDonation d) {
        BloodStockBatch batch = d.getBatch();
        return new DonationResponse(
                d.getId(),
                d.getDonor().getId(),
                d.getDonor().getName(),
                d.getBloodGroup(),
                d.getUnits(),
                d.getCollectedAt(),
                d.getCollectedBy() == null ? null : d.getCollectedBy().getName(),
                d.getStatus(),
                d.getScreenedBy() == null ? null : d.getScreenedBy().getName(),
                d.getScreenedAt(),
                d.getScreeningNotes(),
                batch == null ? null : batch.getId(),
                batch == null ? null : batch.getBatchReference(),
                batch == null ? null : batch.getExpiryDate(),
                batch == null || batch.getVerificationStatus() == null ? null : batch.getVerificationStatus().name());
    }
}
