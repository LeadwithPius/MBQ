package com.MBQ.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
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
 * Blood demand forecasting. XGBoost is used only after a chronological
 * holdout proves it beats the transparent 28-day moving-average baseline.
 * The baseline remains available whenever the ML service is disabled,
 * unavailable, or does not have enough history.
 */
@Service
public class BloodIntelligenceService {
    private static final int BASELINE_DAYS = 28;
    private static final int MODEL_HISTORY_DAYS = 365;
    private static final int EXPIRY_WARNING_DAYS = 7;

    private final BloodStockRepository stocks;
    private final BloodStockBatchRepository batches;
    private final BloodStockMovementRepository movements;
    private final StaffAccessService access;
    private final BloodIntelligenceMlClient mlClient;

    @Autowired
    public BloodIntelligenceService(BloodStockRepository stocks,
            BloodStockBatchRepository batches, BloodStockMovementRepository movements,
            StaffAccessService access, BloodIntelligenceMlClient mlClient) {
        this.stocks = stocks;
        this.batches = batches;
        this.movements = movements;
        this.access = access;
        this.mlClient = mlClient;
    }

    /** Convenience constructor retained for isolated service unit tests. */
    public BloodIntelligenceService(BloodStockRepository stocks,
            BloodStockBatchRepository batches, BloodStockMovementRepository movements,
            StaffAccessService access) {
        this(stocks, batches, movements, access, null);
    }

    @Transactional(readOnly = true)
    public List<Forecast> forecast() {
        StaffUser staff = access.currentStaff();
        List<BloodStock> visible = access.isAdmin(staff) ? stocks.findAll()
                : stocks.findByFacilityId(access.facilityIdOf(staff));
        LocalDate today = LocalDate.now();
        LocalDateTime historyFrom = today.minusDays(MODEL_HISTORY_DAYS - 1L).atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        LocalDateTime baselineFrom = today.minusDays(BASELINE_DAYS - 1L).atStartOfDay();

        List<ForecastDraft> drafts = new ArrayList<>();
        List<BloodIntelligenceMlClient.SeriesInput> mlSeries = new ArrayList<>();

        for (BloodStock stock : visible) {
            List<BloodStockMovement> ledger = movements
                    .findByStock_IdAndMovementTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                            stock.getId(), BloodStockMovementType.ISSUED, historyFrom, to);

            long baselineIssued = ledger.stream()
                    .filter(m -> !m.getCreatedAt().isBefore(baselineFrom))
                    .mapToLong(BloodStockMovement::getUnitsChanged).sum();
            int daysObserved = (int) ledger.stream()
                    .filter(m -> !m.getCreatedAt().isBefore(baselineFrom))
                    .map(m -> m.getCreatedAt().toLocalDate()).distinct().count();
            double baselineDailyDemand = baselineIssued / (double) BASELINE_DAYS;

            TreeMap<LocalDate, Integer> daily = new TreeMap<>();
            for (int day = MODEL_HISTORY_DAYS - 1; day >= 0; day--) {
                daily.put(today.minusDays(day), 0);
            }
            for (BloodStockMovement movement : ledger) {
                LocalDate movementDate = movement.getCreatedAt().toLocalDate();
                if (daily.containsKey(movementDate)) {
                    daily.merge(movementDate, movement.getUnitsChanged(), Integer::sum);
                }
            }

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

            boolean baselineAvailable = baselineIssued > 0 && daysObserved >= 3;
            drafts.add(new ForecastDraft(stock, usable, expiringSoon, baselineIssued,
                    daysObserved, baselineDailyDemand, baselineAvailable));

            List<BloodIntelligenceMlClient.DailyDemand> dailyDemand = daily.entrySet().stream()
                    .map(entry -> new BloodIntelligenceMlClient.DailyDemand(
                            entry.getKey(), entry.getValue()))
                    .toList();
            mlSeries.add(new BloodIntelligenceMlClient.SeriesInput(stock.getId(),
                    stock.getFacility().getId(), stock.getBloodGroup().name(), dailyDemand));
        }

        BloodIntelligenceMlClient.MlResponse mlResponse = mlClient == null
                ? null : mlClient.forecast(mlSeries);
        Map<Long, BloodIntelligenceMlClient.StockForecast> mlForecasts = new HashMap<>();
        if (mlResponse != null && "TRAINED".equalsIgnoreCase(mlResponse.status())
                && mlResponse.forecasts() != null) {
            for (BloodIntelligenceMlClient.StockForecast prediction : mlResponse.forecasts()) {
                mlForecasts.put(prediction.stockId(), prediction);
            }
        }

        List<Forecast> result = new ArrayList<>();
        for (ForecastDraft draft : drafts) {
            BloodStock stock = draft.stock();
            BloodIntelligenceMlClient.StockForecast ml = mlForecasts.get(stock.getId());
            boolean xgboostAvailable = ml != null;
            boolean forecastAvailable = draft.baselineAvailable() || xgboostAvailable;

            int forecast7 = xgboostAvailable ? ml.predictedDemand7Days()
                    : draft.baselineAvailable() ? (int) Math.ceil(draft.baselineDailyDemand() * 7) : 0;
            int forecast14 = xgboostAvailable ? ml.predictedDemand14Days()
                    : draft.baselineAvailable() ? (int) Math.ceil(draft.baselineDailyDemand() * 14) : 0;
            double dailyDemand = xgboostAvailable ? forecast14 / 14.0 : draft.baselineDailyDemand();
            Double daysUntilShortage = forecastAvailable && dailyDemand > 0
                    ? draft.usable() / dailyDemand : null;
            String risk = !forecastAvailable ? "INSUFFICIENT_DATA"
                    : daysUntilShortage == null ? "LOW"
                    : daysUntilShortage <= 3 ? "CRITICAL"
                    : daysUntilShortage <= 7 ? "HIGH"
                    : daysUntilShortage <= 14 ? "MODERATE" : "LOW";
            int recommended = forecastAvailable ? Math.max(0, forecast14 - draft.usable()) : 0;

            result.add(new Forecast(stock.getId(), stock.getFacility().getId(),
                    stock.getBloodGroup().name(), stock.getUnitsAvailable(), draft.usable(),
                    stock.getLowStockThreshold(), draft.expiringSoon(), draft.issued(),
                    draft.daysObserved(), round(dailyDemand, 2), forecastAvailable,
                    forecast7, forecast14, daysUntilShortage == null ? null
                    : round(daysUntilShortage, 1), risk, recommended, BASELINE_DAYS,
                    xgboostAvailable ? "XGBOOST" : "MOVING_AVERAGE",
                    xgboostAvailable ? mlResponse.modelVersion() : null,
                    xgboostAvailable ? mlResponse.validationMae() : null));
        }
        return result;
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    private record ForecastDraft(BloodStock stock, int usable, int expiringSoon,
            long issued, int daysObserved, double baselineDailyDemand,
            boolean baselineAvailable) {}

    public record Forecast(Long stockId, Long facilityId, String bloodGroup,
            int physicalUnits, int usableUnits, int lowStockThreshold,
            int expiringWithin7Days, long unitsIssuedInHistory, int daysWithIssues,
            double averageDailyDemand, boolean forecastAvailable,
            int predictedDemand7Days, int predictedDemand14Days,
            Double estimatedDaysUntilStockout, String riskLevel,
            int suggestedReplenishmentUnits, int historyWindowDays,
            String forecastMethod, String modelVersion, Double validationMae) {}
}
