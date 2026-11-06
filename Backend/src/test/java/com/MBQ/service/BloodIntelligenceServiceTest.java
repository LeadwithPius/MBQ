package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.MBQ.model.BloodBatchVerificationStatus;
import com.MBQ.model.BloodGroup;
import com.MBQ.model.BloodStock;
import com.MBQ.model.BloodStockBatch;
import com.MBQ.model.BloodStockMovement;
import com.MBQ.model.BloodStockMovementType;
import com.MBQ.model.Facility;
import com.MBQ.model.Role;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.BloodStockBatchRepository;
import com.MBQ.repository.BloodStockMovementRepository;
import com.MBQ.repository.BloodStockRepository;

@ExtendWith(MockitoExtension.class)
class BloodIntelligenceServiceTest {
    @Mock private BloodStockRepository stocks;
    @Mock private BloodStockBatchRepository batches;
    @Mock private BloodStockMovementRepository movements;
    @Mock private StaffAccessService access;

    @Test
    void forecastUsesOnlyIssueHistoryAndVerifiedUnexpiredStock() {
        StaffUser admin = new StaffUser();
        admin.setRole(Role.ADMIN);
        Facility facility = new Facility();
        facility.setId(9L);
        BloodStock stock = new BloodStock();
        stock.setId(4L);
        stock.setFacility(facility);
        stock.setBloodGroup(BloodGroup.O_NEG);
        stock.setUnitsAvailable(30);
        stock.setLowStockThreshold(5);
        when(access.currentStaff()).thenReturn(admin);
        when(access.isAdmin(admin)).thenReturn(true);
        when(stocks.findAll()).thenReturn(List.of(stock));

        BloodStockBatch usable = new BloodStockBatch();
        usable.setVerificationStatus(BloodBatchVerificationStatus.VERIFIED);
        usable.setExpiryDate(LocalDate.now().plusDays(20));
        usable.setRemainingQuantity(10);
        BloodStockBatch expired = new BloodStockBatch();
        expired.setVerificationStatus(BloodBatchVerificationStatus.VERIFIED);
        expired.setExpiryDate(LocalDate.now().minusDays(1));
        expired.setRemainingQuantity(20);
        when(batches.findAllForStock(4L)).thenReturn(List.of(usable, expired));

        List<BloodStockMovement> issueRows = List.of(
                movement(LocalDate.now().minusDays(1), 10),
                movement(LocalDate.now().minusDays(8), 9),
                movement(LocalDate.now().minusDays(15), 9));
        when(movements.findByStock_IdAndMovementTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                org.mockito.ArgumentMatchers.eq(4L),
                org.mockito.ArgumentMatchers.eq(BloodStockMovementType.ISSUED),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(issueRows);

        var result = new BloodIntelligenceService(stocks, batches, movements, access).forecast().get(0);
        assertTrue(result.forecastAvailable());
        assertEquals(10, result.usableUnits());
        assertEquals(1.0, result.averageDailyDemand());
        assertEquals(7, result.predictedDemand7Days());
        assertEquals(14, result.predictedDemand14Days());
        assertEquals("MODERATE", result.riskLevel());
        assertEquals(4, result.suggestedReplenishmentUnits());
    }

    @Test
    void forecastDoesNotInventDemandWhenThereIsNoIssueHistory() {
        StaffUser admin = new StaffUser();
        admin.setRole(Role.ADMIN);
        when(access.currentStaff()).thenReturn(admin);
        when(access.isAdmin(admin)).thenReturn(true);
        BloodStock stock = new BloodStock();
        stock.setId(4L);
        Facility facility = new Facility();
        facility.setId(9L);
        stock.setFacility(facility);
        stock.setBloodGroup(BloodGroup.O_NEG);
        when(stocks.findAll()).thenReturn(List.of(stock));
        when(batches.findAllForStock(4L)).thenReturn(List.of());
        when(movements.findByStock_IdAndMovementTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                org.mockito.ArgumentMatchers.eq(4L),
                org.mockito.ArgumentMatchers.eq(BloodStockMovementType.ISSUED),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        var result = new BloodIntelligenceService(stocks, batches, movements, access).forecast().get(0);
        assertFalse(result.forecastAvailable());
        assertEquals("INSUFFICIENT_DATA", result.riskLevel());
        assertEquals(0, result.predictedDemand14Days());
    }

    private static BloodStockMovement movement(LocalDate date, int units) {
        BloodStockMovement movement = new BloodStockMovement();
        movement.setMovementType(BloodStockMovementType.ISSUED);
        movement.setUnitsChanged(units);
        movement.setCreatedAt(date.atTime(10, 0));
        return movement;
    }
}
