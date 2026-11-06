package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.BloodInventoryDtos.ReceiveBatchRequest;
import com.MBQ.dto.DonationDtos.DonationResponse;
import com.MBQ.dto.DonationDtos.RecordDonationRequest;
import com.MBQ.dto.DonationDtos.ScreenDonationRequest;
import com.MBQ.model.BloodBatchVerificationStatus;
import com.MBQ.model.BloodDonation;
import com.MBQ.model.BloodGroup;
import com.MBQ.model.BloodStock;
import com.MBQ.model.BloodStockBatch;
import com.MBQ.model.DonationStatus;
import com.MBQ.model.Donor;
import com.MBQ.model.Facility;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.BloodDonationRepository;
import com.MBQ.repository.BloodStockBatchRepository;
import com.MBQ.repository.BloodStockRepository;
import com.MBQ.repository.DonorRepository;

/** Donation rules: collection never touches stock; only a passed screening does. */
@ExtendWith(MockitoExtension.class)
class DonationServiceTest {

    @Mock private BloodDonationRepository donations;
    @Mock private DonorRepository donors;
    @Mock private BloodStockRepository stocks;
    @Mock private BloodStockBatchRepository batches;
    @Mock private BloodInventoryService inventory;
    @Mock private StaffAccessService staffAccess;

    private DonationService service;
    private Facility facility;
    private Donor donor;
    private StaffUser labTech;

    @BeforeEach
    void setUp() {
        service = new DonationService(donations, donors, stocks, batches, inventory, staffAccess);
        facility = new Facility();
        facility.setId(1L);
        donor = new Donor();
        donor.setId(2L);
        donor.setName("Bupe Mulenga");
        donor.setBloodGroup(BloodGroup.O_NEG);
        donor.setEligible(true);
        donor.setFacility(facility);
        labTech = new StaffUser();
        labTech.setId(20L);
        labTech.setName("Lab Tech");
    }

    private BloodDonation collected(OffsetDateTime when) {
        BloodDonation donation = new BloodDonation();
        donation.setId(9L);
        donation.setDonor(donor);
        donation.setFacility(facility);
        donation.setBloodGroup(BloodGroup.O_NEG);
        donation.setUnits(1);
        donation.setCollectedAt(when);
        donation.setCollectedBy(labTech);
        donation.setStatus(DonationStatus.COLLECTED);
        return donation;
    }

    @Test
    void record_eligibleDonor_isCollectedWithoutTouchingStock() {
        when(donors.findById(2L)).thenReturn(Optional.of(donor));
        when(donations.findFirstByDonor_IdOrderByCollectedAtDesc(2L)).thenReturn(Optional.empty());
        when(staffAccess.currentStaff()).thenReturn(labTech);
        when(donations.saveAndFlush(any(BloodDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        DonationResponse response = service.record(new RecordDonationRequest(2L, 1));

        assertEquals(DonationStatus.COLLECTED, response.status());
        assertEquals(BloodGroup.O_NEG, response.bloodGroup());
        verify(inventory, never()).receive(any(), anyString());
    }

    @Test
    void record_withinMinimumInterval_isRejectedWithNextEligibleDate() {
        when(donors.findById(2L)).thenReturn(Optional.of(donor));
        when(donations.findFirstByDonor_IdOrderByCollectedAtDesc(2L))
                .thenReturn(Optional.of(collected(OffsetDateTime.now().minusDays(20))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.record(new RecordDonationRequest(2L, 1)));

        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("Next eligible date"));
        verify(donations, never()).saveAndFlush(any());
    }

    @Test
    void record_ineligibleDonor_isRejected() {
        donor.setEligible(false);
        when(donors.findById(2L)).thenReturn(Optional.of(donor));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.record(new RecordDonationRequest(2L, 1)));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void screen_passed_receivesUnitsAsAPendingBatch_withShelfLifeExpiry() {
        OffsetDateTime collectedAt = OffsetDateTime.now().minusDays(1);
        BloodDonation donation = collected(collectedAt);
        BloodStock stock = new BloodStock();
        stock.setId(8L);
        stock.setFacility(facility);
        stock.setBloodGroup(BloodGroup.O_NEG);
        BloodStockBatch batch = new BloodStockBatch();
        batch.setId(55L);
        batch.setBatchReference("DON-9");
        batch.setVerificationStatus(BloodBatchVerificationStatus.PENDING_VERIFICATION);

        when(donations.findByIdForUpdate(9L)).thenReturn(Optional.of(donation));
        when(staffAccess.currentStaff()).thenReturn(labTech);
        when(stocks.findByFacilityIdAndBloodGroup(1L, BloodGroup.O_NEG)).thenReturn(Optional.of(stock));
        when(batches.findByStockIdAndBatchReference(8L, "DON-9")).thenReturn(Optional.of(batch));
        when(donations.saveAndFlush(any(BloodDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        DonationResponse response = service.screen(9L, new ScreenDonationRequest(true, "HIV/HBV/HCV/syphilis negative"));

        ArgumentCaptor<ReceiveBatchRequest> received = ArgumentCaptor.forClass(ReceiveBatchRequest.class);
        verify(inventory).receive(received.capture(), eq("donation-9"));
        assertEquals(8L, received.getValue().stockId());
        assertEquals(1, received.getValue().units());
        assertEquals(collectedAt.toLocalDate().plusDays(DonationService.SHELF_LIFE_DAYS), received.getValue().expiryDate());
        assertEquals(DonationStatus.PASSED, response.status());
        assertEquals(55L, response.batchId());
        assertEquals("PENDING_VERIFICATION", response.batchVerificationStatus());
    }

    @Test
    void screen_failed_neverAddsStock() {
        when(donations.findByIdForUpdate(9L)).thenReturn(Optional.of(collected(OffsetDateTime.now())));
        when(staffAccess.currentStaff()).thenReturn(labTech);
        when(donations.saveAndFlush(any(BloodDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        DonationResponse response = service.screen(9L, new ScreenDonationRequest(false, "Reactive HBsAg"));

        assertEquals(DonationStatus.FAILED, response.status());
        verify(inventory, never()).receive(any(), anyString());
    }

    @Test
    void screen_twice_isRejected() {
        BloodDonation donation = collected(OffsetDateTime.now());
        donation.setStatus(DonationStatus.FAILED);
        when(donations.findByIdForUpdate(9L)).thenReturn(Optional.of(donation));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.screen(9L, new ScreenDonationRequest(true, null)));

        assertEquals(409, error.getStatusCode().value());
        verify(inventory, never()).receive(any(), anyString());
    }

    @Test
    void screen_passedAfterShelfLife_isRejected() {
        when(donations.findByIdForUpdate(9L)).thenReturn(
                Optional.of(collected(OffsetDateTime.now().minusDays(DonationService.SHELF_LIFE_DAYS + 2))));
        when(staffAccess.currentStaff()).thenReturn(labTech);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.screen(9L, new ScreenDonationRequest(true, null)));

        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("expired"));
    }
}
