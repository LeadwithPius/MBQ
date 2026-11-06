package com.MBQ.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MBQ.model.BloodStock;
import com.MBQ.model.BloodStockBatch;
import com.MBQ.model.BloodBatchVerificationStatus;
import com.MBQ.model.BloodStockMovement;
import com.MBQ.model.BloodStockMovementType;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.BloodStockBatchRepository;
import com.MBQ.repository.BloodStockMovementRepository;
import com.MBQ.repository.BloodStockRepository;

/**
 * Explainable baseline forecast. It uses completed ISSUE ledger movements over
 * the preceding 28 calendar days; it does not invent demand when history is absent.
 */
@Service
public class BloodIntelligenceService {
    private static final int HISTORY_DAYS = 28;
    private static final int EXPIRY_WARNING_DAYS = 7;

    private final BloodStockRepository stocks;
    private final BloodStockBatchRepository batches;
    private final BloodStockMovementRepository movements;
    private final StaffAccessService access;

    public BloodIntelligenceService(BloodStockRepository stocks,
            BloodStockBatchRepository batches, BloodStockMovementRepository movements,
            StaffAccessService access) {
        this.stocks = stocks;
        this.batches = batches;
        this.movements = movements;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<Forecast> forecast() {
        StaffUser staff = access.currentStaff();
        List<BloodStock> visible = access.isAdmin(staff) ? stocks.findAll()
                : stocks.findByFacilityId(access.facilityIdOf(staff));
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(HISTORY_DAYS).atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        List<Forecast> result = new ArrayList<>();
        for (BloodStock stock : visible) {
            List<BloodStockMovement> ledger = movements
                    .findByStock_IdAndMovementTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                            stock.getId(), BloodStockMovementType.ISSUED, from, to);
            long issued = ledger.stream().mapToLong(BloodStockMovement::getUnitsChanged).sum();
            int daysObserved = (int) ledger.stream().map(m -> m.getCreatedAt().toLocalDate()).distinct().count();
            double dailyDemand = issued / (double) HISTORY_DAYS;
            int usable = 0;
            int expiringSoon = 0;
            for (BloodStockBatch batch : batches.findAllForStock(stock.getId())) {
                if (batch.getVerificationStatus() == BloodBatchVerificationStatus.VERIFIED
                        && batch.getExpiryDate() != null && !batch.getExpiryDate().isBefore(today)
                        && batch.getRemainingQuantity() > 0) {
                    usable += batch.getRemainingQuantity();
                    if (!batch.getExpiryDate().isAfter(today.plusDays(EXPIRY_WARNING_DAYS))) {
                        expiringSoon += batch.getRemainingQuantity();
                    }
                }
            }
            boolean enoughHistory = issued > 0 && daysObserved >= 3;
            Double daysUntilShortage = enoughHistory && dailyDemand > 0
                    ? usable / dailyDemand : null;
            String risk = !enoughHistory ? "INSUFFICIENT_DATA"
                    : daysUntilShortage <= 3 ? "CRITICAL"
                    : daysUntilShortage <= 7 ? "HIGH"
                    : daysUntilShortage <= 14 ? "MODERATE" : "LOW";
            int forecast7 = enoughHistory ? (int) Math.ceil(dailyDemand * 7) : 0;
            int forecast14 = enoughHistory ? (int) Math.ceil(dailyDemand * 14) : 0;
            int recommended = enoughHistory ? Math.max(0, forecast14 - usable) : 0;
            result.add(new Forecast(stock.getId(), stock.getFacility().getId(),
                    stock.getBloodGroup().name(), stock.getUnitsAvailable(), usable,
                    stock.getLowStockThreshold(), expiringSoon, issued, daysObserved,
                    Math.round(dailyDemand * 100.0) / 100.0, enoughHistory,
                    forecast7, forecast14, daysUntilShortage == null ? null
                    : Math.round(daysUntilShortage * 10.0) / 10.0,
                    risk, recommended, HISTORY_DAYS));
        }
        return result;
    }

    public record Forecast(Long stockId, Long facilityId, String bloodGroup,
            int physicalUnits, int usableUnits, int lowStockThreshold,
            int expiringWithin7Days, long unitsIssuedInHistory, int daysWithIssues,
            double averageDailyDemand, boolean forecastAvailable,
            int predictedDemand7Days, int predictedDemand14Days,
            Double estimatedDaysUntilStockout, String riskLevel,
            int suggestedReplenishmentUnits, int historyWindowDays) {}
}
