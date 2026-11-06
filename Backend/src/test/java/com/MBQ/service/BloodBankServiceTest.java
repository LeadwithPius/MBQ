package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.BloodBankDtos.AlertReplyRequest;
import com.MBQ.dto.BloodBankDtos.DonorAlertResult;
import com.MBQ.dto.BloodBankDtos.StockLevelResponse;
import com.MBQ.model.Alert;
import com.MBQ.model.BloodBatchVerificationStatus;
import com.MBQ.model.BloodGroup;
import com.MBQ.model.BloodStock;
import com.MBQ.model.BloodStockBatch;
import com.MBQ.model.Donor;
import com.MBQ.model.Facility;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.AlertRepository;
import com.MBQ.repository.BloodStockBatchRepository;
import com.MBQ.repository.BloodStockMovementRepository;
import com.MBQ.repository.BloodStockRepository;
import com.MBQ.repository.DonorRepository;
import com.MBQ.repository.QueueTicketRepository;

/** Unit tests for the blood bank rules. No database: repositories are Mockito mocks. */
@ExtendWith(MockitoExtension.class)
class BloodBankServiceTest {

    @Mock private BloodStockRepository stocks;
    @Mock private BloodStockBatchRepository batches;
    @Mock private BloodStockMovementRepository movements;
    @Mock private DonorRepository donors;
    @Mock private AlertRepository alerts;
    @Mock private QueueTicketRepository tickets;
    @Mock private StaffAccessService staffAccess;

    private BloodBankService service;
    private BloodStock oNeg;

    @BeforeEach
    void setUp() {
        service = new BloodBankService(stocks, batches, movements, donors, alerts, tickets, staffAccess);

        Facility facility = new Facility();
        facility.setId(1L);

        oNeg = new BloodStock();
        oNeg.setId(8L);
        oNeg.setFacility(facility);
        oNeg.setBloodGroup(BloodGroup.O_NEG);
        oNeg.setUnitsAvailable(2);
        oNeg.setLowStockThreshold(5);
    }

    private static BloodStockBatch batch(BloodBatchVerificationStatus status, LocalDate expiry, int remaining) {
        BloodStockBatch batch = new BloodStockBatch();
        batch.setVerificationStatus(status);
        batch.setExpiryDate(expiry);
        batch.setOpeningQuantity(remaining);
        batch.setRemainingQuantity(remaining);
        return batch;
    }

    private static Donor donor(long id, String name) {
        Donor donor = new Donor();
        donor.setId(id);
        donor.setName(name);
        donor.setBloodGroup(BloodGroup.O_NEG);
        donor.setEligible(true);
        return donor;
    }

    @Test
    void listStock_flagsLowStock_andCountsOnlyVerifiedUnexpiredUnitsAsIssuable() {
        StaffUser admin = new StaffUser();
        when(staffAccess.currentStaff()).thenReturn(admin);
        when(staffAccess.isAdmin(admin)).thenReturn(true);
        when(stocks.findAll()).thenReturn(List.of(oNeg));
        LocalDate today = LocalDate.now();
        when(batches.findAllForStock(8L)).thenReturn(List.of(
                batch(BloodBatchVerificationStatus.VERIFIED, today.plusDays(3), 2),   // issuable, expiring soon
                batch(BloodBatchVerificationStatus.VERIFIED, today.minusDays(1), 4),  // expired: not issuable
                batch(BloodBatchVerificationStatus.UNVERIFIED_LEGACY, null, 6)));     // legacy: not issuable

        StockLevelResponse level = service.listStock().get(0);

        assertTrue(level.low());
        assertEquals(2, level.issuableUnits());
        assertEquals(2, level.expiringSoonUnits());
    }

    @Test
    void notifyDonors_alertsEligibleDonors_andSkipsThoseAlreadyWaitingForAnAnswer() {
        Donor alreadyAlerted = donor(1L, "Mwila Chanda");
        Donor fresh = donor(2L, "Bupe Mulenga");
        when(stocks.findById(8L)).thenReturn(Optional.of(oNeg));
        when(donors.findByFacilityIdAndBloodGroupAndEligibleTrueOrderByNameAsc(1L, BloodGroup.O_NEG))
                .thenReturn(List.of(alreadyAlerted, fresh));
        when(alerts.existsByStock_IdAndDonor_IdAndResponseStatus(8L, 1L, "PENDING")).thenReturn(true);
        when(alerts.existsByStock_IdAndDonor_IdAndResponseStatus(8L, 2L, "PENDING")).thenReturn(false);
        when(alerts.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DonorAlertResult result = service.notifyDonors(8L);

        assertEquals(1, result.donorsNotified());
        assertEquals(1, result.donorsSkipped());
        assertEquals("Bupe Mulenga", result.alerts().get(0).donorName());
        assertEquals("PENDING", result.alerts().get(0).responseStatus());
    }

    @Test
    void replyToAlert_alreadyAnswered_isRejectedWith409() {
        Alert answered = new Alert();
        answered.setId(5L);
        answered.setStock(oNeg);
        answered.setDonor(donor(1L, "Mwila Chanda"));
        answered.setResponseStatus("ACCEPTED");
        when(alerts.findById(5L)).thenReturn(Optional.of(answered));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.replyToAlert(5L, new AlertReplyRequest("DECLINED")));

        assertEquals(409, error.getStatusCode().value());
        verify(alerts, never()).save(any());
    }
}
