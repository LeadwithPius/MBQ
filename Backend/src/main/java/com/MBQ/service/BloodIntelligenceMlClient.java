package com.MBQ.service;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Optional bridge to the Python/XGBoost forecasting service.
 * A missing or unavailable ML service never prevents the blood-bank API from working.
 */
@Component
public class BloodIntelligenceMlClient {
    private static final Logger log = LoggerFactory.getLogger(BloodIntelligenceMlClient.class);

    private final RestClient client;
    private final String apiKey;

    public BloodIntelligenceMlClient(RestClient.Builder builder,
            @Value("${app.blood-intelligence.ml-url:}") String baseUrl,
            @Value("${app.blood-intelligence.ml-api-key:}") String apiKey) {
        this.client = baseUrl == null || baseUrl.isBlank() ? null
                : builder.baseUrl(baseUrl.replaceAll("/+$", "")).build();
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public MlResponse forecast(List<SeriesInput> series) {
        if (client == null || series == null || series.isEmpty()) {
            return null;
        }
        try {
            RestClient.RequestBodySpec request = client.post().uri("/forecast")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
            if (!apiKey.isBlank()) {
                request.header("X-API-Key", apiKey);
            }
            return request.body(new ForecastRequest(series)).retrieve().body(MlResponse.class);
        } catch (RestClientException ex) {
            log.warn("XGBoost blood forecast unavailable; using the Java baseline: {}", ex.getMessage());
            return null;
        }
    }

    public record ForecastRequest(List<SeriesInput> series) {}
    public record SeriesInput(Long stockId, Long facilityId, String bloodGroup,
            List<DailyDemand> dailyDemand) {}
    public record DailyDemand(LocalDate date, int unitsIssued) {}
    public record MlResponse(String status, String modelVersion, Double validationMae,
            Double baselineMae, String message, List<StockForecast> forecasts) {}
    public record StockForecast(Long stockId, int predictedDemand7Days,
            int predictedDemand14Days) {}
}
